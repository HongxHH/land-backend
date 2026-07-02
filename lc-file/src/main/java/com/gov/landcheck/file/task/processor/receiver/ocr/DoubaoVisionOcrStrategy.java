package com.gov.landcheck.file.task.processor.receiver.ocr;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.service.LLMService;
import com.gov.landcheck.core.service.impl.VolcanoLLMServiceImpl;
import com.gov.landcheck.file.dto.OCRPageResult;
import com.gov.landcheck.file.dto.OCRProcessResult;
import com.gov.landcheck.file.task.base.TaskData;
import com.gov.landcheck.file.utils.GridFSUtils;
import com.gov.landcheck.file.utils.PdfProcessor;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 豆包多模态逐页 OCR：PDF 按页渲染为图后逐页调用视觉模型，适用于规划复核表、容量指标核查表等；
 */
@Slf4j
@Service
public class DoubaoVisionOcrStrategy implements OcrProcessingStrategy {

    private static final int MAX_PAGE_LLM_ATTEMPTS = 3;

    private static final String PLANNING_REVIEW_JSON_REPAIR_SUFFIX = """

            【输出修正】上一输出无法解析为顶层 JSON 对象。请严格只输出一个 JSON 对象（不要 markdown 代码块、不要任何说明文字），顶层含 header 与 rows，键名仍为英文 snake_case。""";

    private static final String CAPACITY_INDICATOR_JSON_REPAIR_SUFFIX = """

            【输出修正】上一输出无法解析为顶层 JSON 对象。请严格只输出一个 JSON 对象（不要 markdown 代码块、不要任何说明文字），顶层仅含 total_area、commercial_area、residential_area 三个键（英文 snake_case），值为数字或 null。""";

    @Resource
    private GridFSUtils gridFSUtils;
    @Resource
    private PdfProcessor pdfProcessor;
    @Resource
    private LLMService llmService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private int renderDpi = 168;
    private double temperature = 0.1;
    private int maxTokens = 8192;

    private int maxConcurrentVisionRequests = 10;

    @Override
    public boolean supports(FileContextType fileContextType) {
        if (fileContextType == null) {
            return false;
        }
        return switch (fileContextType) {
            case PLANNING_REVIEW, CAPACITY_INDICATOR -> true;
            default -> false;
        };
    }

    private static String resolveVisionInstruction(FileContextType fileContextType) {
        if (fileContextType == null) {
            return VolcanoLLMServiceImpl.DOCUMENT_VISION_SINGLE_PAGE_DEFAULT_INSTRUCTION;
        }
        return switch (fileContextType) {
            case PLANNING_REVIEW -> VolcanoLLMServiceImpl.PLANNING_REVIEW_VISION_SINGLE_PAGE_INSTRUCTION;
            case CAPACITY_INDICATOR -> VolcanoLLMServiceImpl.CAPACITY_INDICATOR_VISION_SINGLE_PAGE_INSTRUCTION;
            default -> VolcanoLLMServiceImpl.DOCUMENT_VISION_SINGLE_PAGE_DEFAULT_INSTRUCTION;
        };
    }

    @Override
    public OCRProcessResult process(TaskData taskData) throws Exception {
        long start = System.currentTimeMillis();
        var fileRecord = taskData.getFileRecord();
        String targetGridfsId = StringUtils.hasText(taskData.getPreprocessGridfsId())
                ? taskData.getPreprocessGridfsId()
                : fileRecord.getGridfsId();

        FileContextType contextType = fileRecord.getFileContextType();
        String instruction = resolveVisionInstruction(contextType);

        byte[] pdfBytes = gridFSUtils.getFileBytes(targetGridfsId);
        List<byte[]> pngPages = pdfProcessor.renderPdfToPngBytesPerPage(pdfBytes, renderDpi);

        List<OCRPageResult> pageResults = new ArrayList<>();

        if (maxConcurrentVisionRequests > 1 && pngPages.size() > 1) {
            runSinglePageVisionParallel(taskData, pngPages, pageResults, instruction, contextType);
        } else {
            int n = pngPages.size();
            for (int i = 0; i < n; i++) {
                if (taskData.getTask() != null) {
                    taskData.getTask().checkCancellation();
                }
                pageResults.add(recognizePage(i + 1, pngPages.get(i), contextType, instruction, taskData));
            }
        }

        long okPages = pageResults.stream()
                .filter(p -> Boolean.TRUE.equals(p.getSuccess()) && StringUtils.hasText(p.getMarkdownText()))
                .count();
        if (!pngPages.isEmpty() && okPages == 0) {
            throw new IllegalStateException("多模态视觉OCR全部页失败");
        }

        OCRProcessResult result = new OCRProcessResult(pageResults);
        result.setProcessingTimeMs(System.currentTimeMillis() - start);
        return result;
    }

    private void runSinglePageVisionParallel(TaskData taskData, List<byte[]> pngPages, List<OCRPageResult> pageResults,
            String instruction, FileContextType contextType) {
        int n = pngPages.size();
        int permits = Math.min(Math.max(1, maxConcurrentVisionRequests), n);
        Semaphore gate = new Semaphore(permits);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<OCRPageResult>> futures = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                final int pageIndex = i;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        if (taskData.getTask() != null) {
                            taskData.getTask().checkCancellation();
                        }
                        gate.acquireUninterruptibly();
                        try {
                            return recognizePage(pageIndex + 1, pngPages.get(pageIndex), contextType, instruction,
                                    taskData);
                        } finally {
                            gate.release();
                        }
                    } catch (Throwable t) {
                        OCRPageResult failed = new OCRPageResult(pageIndex + 1);
                        failed.markFailed(
                                shorten(t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
                        return failed;
                    }
                }, executor));
            }

            for (CompletableFuture<OCRPageResult> future : futures) {
                pageResults.add(future.join());
            }
        }
    }

    private OCRPageResult recognizePage(int pageNumber, byte[] png, FileContextType contextType, String baseInstruction,
            TaskData taskData) {
        String repairSuffix = resolveJsonRepairSuffix(contextType);
        for (int call = 1; call <= MAX_PAGE_LLM_ATTEMPTS; call++) {
            try {
                if (taskData.getTask() != null) {
                    taskData.getTask().checkCancellation();
                }
                String text = runVisionForSinglePage(png, baseInstruction);
                if (isValidVisionJson(contextType, text)) {
                    OCRPageResult ok = new OCRPageResult(pageNumber);
                    ok.setMarkdownText(text);
                    ok.setSuccess(true);
                    return ok;
                }
                String repaired = runVisionForSinglePage(png, baseInstruction + repairSuffix);
                if (isValidVisionJson(contextType, repaired)) {
                    OCRPageResult ok = new OCRPageResult(pageNumber);
                    ok.setMarkdownText(repaired);
                    ok.setSuccess(true);
                    return ok;
                }
                OCRPageResult bad = new OCRPageResult(pageNumber);
                bad.markFailed("视觉单页返回非合法JSON");
                return bad;
            } catch (Exception ex) {
                if (call < MAX_PAGE_LLM_ATTEMPTS) {
                    log.warn("多模态视觉页调用失败，将重试 page={} attempt={}/{}: {}",
                            pageNumber, call, MAX_PAGE_LLM_ATTEMPTS, ex.getMessage());
                    sleepQuiet(200L * call);
                    continue;
                }
                OCRPageResult failed = new OCRPageResult(pageNumber);
                failed.markFailed(shorten(ex.getMessage()));
                return failed;
            }
        }
        OCRPageResult failed = new OCRPageResult(pageNumber);
        failed.markFailed("多模态视觉页超过最大重试次数");
        return failed;
    }

    private static String resolveJsonRepairSuffix(FileContextType contextType) {
        if (contextType == FileContextType.CAPACITY_INDICATOR) {
            return CAPACITY_INDICATOR_JSON_REPAIR_SUFFIX;
        }
        return PLANNING_REVIEW_JSON_REPAIR_SUFFIX;
    }

    private boolean isValidVisionJson(FileContextType contextType, String raw) {
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        try {
            var node = objectMapper.readTree(stripJsonFence(raw.trim()));
            if (!node.isObject()) {
                return false;
            }
            return switch (contextType) {
                case CAPACITY_INDICATOR -> node.has("total_area") || node.has("commercial_area")
                        || node.has("residential_area");
                case PLANNING_REVIEW -> true;
                default -> true;
            };
        } catch (Exception e) {
            return false;
        }
    }

    private static void sleepQuiet(long millis) {
        try {
            Thread.sleep(Math.min(Math.max(0L, millis), 60_000L));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static String shorten(String message) {
        if (message == null) {
            return "unknown";
        }
        String t = message.trim();
        return t.length() > 500 ? t.substring(0, 500) + "…" : t;
    }

    private static String stripJsonFence(String s) {
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl > 0) {
                s = s.substring(firstNl + 1);
            }
            int end = s.lastIndexOf("```");
            if (end > 0) {
                s = s.substring(0, end);
            }
        }
        return s.trim();
    }

    private String runVisionForSinglePage(byte[] png, String instruction) throws Exception {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", instruction));

        String b64 = Base64.getEncoder().encodeToString(png);
        String dataUrl = "data:image/png;base64," + b64;
        Map<String, Object> imageUrl = new HashMap<>();
        imageUrl.put("url", dataUrl);
        Map<String, Object> imagePart = new HashMap<>();
        imagePart.put("type", "image_url");
        imagePart.put("image_url", imageUrl);
        content.add(imagePart);

        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", content);

        List<Map<String, Object>> messages = List.of(userMessage);
        return llmService.chatCompletionMultimodal(messages, temperature, maxTokens, null);
    }
}
