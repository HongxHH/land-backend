package com.gov.landcheck.file.task.processor.receiver;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.gov.landcheck.file.config.FileProcessingConfig;
import com.gov.landcheck.file.processing.ProcessingConcurrencyGate;
import com.gov.landcheck.file.task.base.TaskData;
import com.gov.landcheck.file.task.base.TaskException;
import com.gov.landcheck.file.utils.GridFSUtils;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * PDF预处理器实现
 * 功能：基于OpenCV实现红色印章去除和文本增强
 */
@Slf4j
@Service
public class PdfPreReceiver {

    @Resource
    private GridFsTemplate gridFsTemplate;

    @Resource
    private GridFSUtils gridFSUtils;

    // Python脚本相关配置
    private static final String PYTHON_SCRIPT_NAME = "pdf_preprocessor.py";
    private static final int PYTHON_TIMEOUT_SECONDS = 300; // 5分钟超时
    private static final int WAIT_MAX_TIMEOUT = 5; // 最大等待时间5秒
    private static final int DESTROY_WAIT_SECONDS = 5;

    @Value("${landcheck.file.preprocess.conda-env:SR}")
    private String preprocessCondaEnv;

    private final ProcessingConcurrencyGate pdfPreprocessGate;

    public PdfPreReceiver(
            @Qualifier(FileProcessingConfig.PDF_PREPROCESS_GATE) ProcessingConcurrencyGate pdfPreprocessGate) {
        this.pdfPreprocessGate = pdfPreprocessGate;
    }

    public String preprocess(String gridfsId, String fileName, TaskData taskData) throws Exception {
        log.debug("开始PDF预处理: gridfsId={}, fileName={}", gridfsId, fileName);

        Path tempDir = null;

        try {
            // 从GridFS下载原始PDF
            tempDir = Files.createTempDirectory("pdf_preprocess_");
            Path inputPdfPath = tempDir.resolve("original.pdf");
            gridFSUtils.downloadToPath(gridfsId, inputPdfPath);

            Path outputPdfPath = tempDir.resolve("preprocessed_" + fileName);

            // 调用Python脚本进行预处理
            callPythonPreprocessor(inputPdfPath, outputPdfPath, taskData);

            // 上传预处理后的PDF到GridFS
            String preprocessFileName = "preprocessed_" + fileName;
            Document metadata = new Document();
            if (gridfsId != null) {
                metadata.append("originalFileId", gridfsId);
                metadata.append("preprocessTime", System.currentTimeMillis());
            }

            try (FileInputStream inputStream = new FileInputStream(outputPdfPath.toFile())) {
                ObjectId objectId = gridFsTemplate.store(inputStream, preprocessFileName, "application/pdf", metadata);
                return objectId.toString();
            }

        } catch (Exception e) {
            boolean cancelled = (e.getMessage() != null && e.getMessage().contains("任务已取消"))
                    || (e.getCause() instanceof InterruptedException);
            if (cancelled) {
                log.info("任务已取消，预处理已终止: gridfsId={}", gridfsId);
                throw e instanceof TaskException ? (TaskException) e
                        : new TaskException(TaskException.ErrorCode.PREPROCESS_FAILED,
                                "PREPROCESS", null, null, "任务已取消，已终止Python预处理进程", e);
            }
            log.error("PDF预处理失败: gridfsId={}, error={}", gridfsId, e.getMessage(), e);
            throw new TaskException(TaskException.ErrorCode.PREPROCESS_FAILED,
                    "PREPROCESS", null, null, "PDF预处理失败: " + e.getMessage(), e);
        } finally {
            if (tempDir != null && Files.exists(tempDir)) {
                try {
                    List<Path> toDelete = Files.walk(tempDir)
                            .sorted(Comparator.comparing(Path::toString,
                                    Comparator.comparingInt(String::length).reversed()))
                            .toList();
                    for (Path p : toDelete) {
                        Files.deleteIfExists(p);
                    }
                    log.debug("临时文件清理完成: {}", tempDir);
                } catch (IOException e) {
                    log.warn("清理临时文件失败: {}, error={}", tempDir, e.getMessage());
                }

            }
        }
    }

    /**
     * 调用Python脚本进行PDF预处理。
     */
    private void callPythonPreprocessor(Path inputPdfPath, Path outputPdfPath, TaskData taskData) throws Exception {
        if (!pdfPreprocessGate.tryAcquire()) {
            throw new TaskException(TaskException.ErrorCode.PREPROCESS_RESOURCE_ERROR,
                    "PREPROCESS", null, null, "PDF预处理并发已满，系统内存资源不足，稍后重试");
        }
        try {
            callPythonPreprocessorUnderGate(inputPdfPath, outputPdfPath, taskData);
        } finally {
            pdfPreprocessGate.release();
        }
    }

    private void callPythonPreprocessorUnderGate(Path inputPdfPath, Path outputPdfPath, TaskData taskData)
            throws Exception {
        Path scriptPath = findPythonScript();

        StringBuilder args = new StringBuilder();
        String pythonCommand = String.format(
                "conda activate %s && python \"%s\" --input \"%s\" --output \"%s\" --dpi 300"
                        + " --red-diff-thresh 30 --red-min-r 80 --protect-gray-thresh 80 --morph-ksize 3",
                preprocessCondaEnv, scriptPath, inputPdfPath, outputPdfPath);

        ProcessBuilder pb = new ProcessBuilder("cmd", "/c", pythonCommand);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        ExecutorService outputReader = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "PdfPreprocess-OutputReader");
            t.setDaemon(true);
            return t;
        });
        outputReader.submit(() -> {
            try (InputStream in = process.getInputStream()) {
                in.transferTo(OutputStream.nullOutputStream());
            } catch (IOException e) {
                log.debug("读取 Python 合并输出流结束: {}", e.getMessage());
            }
        });

        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(PYTHON_TIMEOUT_SECONDS);
        boolean cancelledByTask = false;

        try {
            while (!process.waitFor(WAIT_MAX_TIMEOUT, TimeUnit.SECONDS)) {
                if (System.nanoTime() >= deadlineNanos) {
                    process.destroyForcibly();
                    process.waitFor(DESTROY_WAIT_SECONDS, TimeUnit.SECONDS);
                    throw new TaskException(TaskException.ErrorCode.PREPROCESS_TIMEOUT,
                            "PREPROCESS", null, null, "Python脚本执行超时");
                }
                if (taskData != null && taskData.getTask() != null && taskData.getTask().shouldStop()) {
                    cancelledByTask = true;
                }
            }
        } catch (InterruptedException e) {
            cancelledByTask = true;
            Thread.currentThread().interrupt();
            // 不强杀子进程：等待进程结束后再由 finally 清理临时文件
            while (true) {
                try {
                    if (process.waitFor(WAIT_MAX_TIMEOUT, TimeUnit.SECONDS)) {
                        break;
                    }
                    if (System.nanoTime() >= deadlineNanos) {
                        process.destroyForcibly();
                        process.waitFor(DESTROY_WAIT_SECONDS, TimeUnit.SECONDS);
                        break;
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        outputReader.shutdown();
        try {
            if (!outputReader.awaitTermination(DESTROY_WAIT_SECONDS, TimeUnit.SECONDS)) {
                outputReader.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            outputReader.shutdownNow();
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new TaskException(TaskException.ErrorCode.PREPROCESS_FAILED,
                    "PREPROCESS", null, null,
                    "Python脚本执行失败，退出码: " + exitCode);
        }

        if (!Files.exists(outputPdfPath)) {
            throw new TaskException(TaskException.ErrorCode.PREPROCESS_FAILED,
                    "PREPROCESS", null, null,
                    "Python脚本未生成输出文件: " + outputPdfPath);
        }

        // 曾检测到取消请求，但子进程已正常结束且产物存在：按成功处理，避免误抛“任务已取消”
        if (cancelledByTask) {
            log.info("预处理过程中曾出现取消/中断信号，但子进程已正常完成且输出文件存在，按成功路径继续: output={}",
                    outputPdfPath);
        }
    }

    /**
     * 查找Python脚本文件
     */
    private Path findPythonScript() throws IOException {
        // 优先从 classpath 读取（支持从 resources 和打包后的 jar 运行）
        try (InputStream scriptStream = getClass().getClassLoader().getResourceAsStream(PYTHON_SCRIPT_NAME)) {
            if (scriptStream != null) {
                Path tempScriptPath = Files.createTempFile("pdf_preprocessor_", ".py");
                Files.copy(scriptStream, tempScriptPath, StandardCopyOption.REPLACE_EXISTING);
                tempScriptPath.toFile().deleteOnExit();
                return tempScriptPath;
            }
        }

        // 兼容开发态：回退到 resources 目录（工作目录为 backend 根目录时）
        Path currentDir = Path.of(System.getProperty("user.dir"));
        for (String module : new String[] { "lc-start", "lc-file" }) {
            Path resourcesPath = currentDir.resolve(module)
                    .resolve("src")
                    .resolve("main")
                    .resolve("resources")
                    .resolve(PYTHON_SCRIPT_NAME);
            if (Files.exists(resourcesPath)) {
                return resourcesPath.toAbsolutePath();
            }
        }

        throw new TaskException(TaskException.ErrorCode.PREPROCESS_FAILED,
                "PREPROCESS", null, null,
                "找不到Python脚本文件: " + PYTHON_SCRIPT_NAME
                        + "。请确认该脚本已放入 lc-start/src/main/resources");
    }

    public void deleteExistingPreprocessResult(String originalGridfsId) {

        gridFSUtils.deleteById(originalGridfsId);

    }
}