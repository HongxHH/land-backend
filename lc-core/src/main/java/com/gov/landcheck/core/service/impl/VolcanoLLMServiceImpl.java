package com.gov.landcheck.core.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

import com.gov.landcheck.core.service.LLMService;

import lombok.extern.slf4j.Slf4j;

/**
 * 火山引擎LLM服务实现
 *
 * @author system
 * @date 2025/01/20
 */
@Slf4j
@Service
public class VolcanoLLMServiceImpl implements LLMService {

    @Value("${landcheck.llm.api.url}")
    private String apiUrl;

    @Value("${landcheck.llm.api.key}")
    private String apiKey;

    @Value("${landcheck.llm.api.model}")
    private String model;

    @Value("${landcheck.llm.api.vision-model:${landcheck.llm.api.model}}")
    private String visionModel;

    @Value("${landcheck.llm.api.timeout-seconds:240}")
    private int apiTimeoutSeconds;

    @Value("${landcheck.llm.http.max-attempts:3}")
    private int maxHttpAttempts;

    private RestTemplate restTemplate;

    // 默认参数
    private static final Double DEFAULT_TEMPERATURE = 0.1;
    private static final Integer DEFAULT_MAX_TOKENS = 2000;
    /** 项目方汇总 JSON 抽取输出上限（硬编码，不暴露为配置项） */
    private static final Integer PROJECT_PARTY_SUMMARY_MAX_TOKENS = 4096;
    private static final String ROOM_USAGE_INITIAL_PROMPT_TEMPLATE = """
            你是一个专业的房地产勘测数据分析助手。请仔细分析下面的勘测成果表内容，提取房屋设计用途的规则。

            分析任务：
            请从勘测成果表中找出"房屋设计用途"的相关表述，理解用途分配规则。特别注意按楼层分配的规则，如"4-32层为公寓式办公"等。

            输入数据：
            - 涉及的房间号(这里我仅展示了前200个，实际的房间号可能更多)：%s
            - 勘测成果表内容见下文

            分析要求：
            1. 提取"房屋设计用途"的完整表述
            2. 理解用途分配规则，包括：
            - 默认用途（所有房间的通用用途，适用于大部分未明确指定用途的房间）
            - 特殊房间的例外用途
            - 范围描述（包括楼层范围，如"4-32层"、房间范围如"101-105"等）
            - 特别关注按楼层分配的规则

            输出格式：
            请以JSON格式返回结果，包含以下字段：
            {
                "默认用途": "公寓式办公",
                "特殊用途": {
                    "112": "公卫"
                },
                "范围用途": {
                    "101-111": "商业",
                    "201-208": "商业",
                    "301-320": "物管用房"
                }
            }

            或者对于简单情况：
            {
                "默认用途": "住宅"
            }
            """;
    private static final String ROOM_USAGE_INITIAL_PROMPT_NOTES = """
            - "默认用途"：适用于所有未明确指定用途的房间，支持以下用途：住宅、商业、人防车库/车库、医疗卫生、办公、公寓式办公、写字楼、商住楼、综合楼、酒店式公寓、酒店等
            - "特殊用途"：特定房间号的特殊用途
            - "范围用途"：房间号范围或楼层范围的用途，支持"101-105"、"4-32层"等格式
            - 只返回JSON格式的结果，不要添加其他说明
            - 用途名称要简洁准确
            - 如果只有一种用途，返回简单的默认用途格式
            """;
    private static final String ROOM_USAGE_SUPPLEMENT_PROMPT_TEMPLATE = """
            你是一个专业的房地产勘测数据分析助手。现在需要补充分析尚未确定用途的房间。

            背景信息：
            - 之前的分析结果：%s
            - 已确定用途的房间示例：%s

            补充分析任务：
            请针对以下未确定用途的房间，从勘测成果表中找出对应的用途规则。

            待分析的房间号：%s

            分析要求：
            1. 基于之前的分析结果和勘测表内容
            2. 为未确定用途的房间找出合适的用途
            3. 特别注意楼层规则和范围规则

            输出格式：
            请以JSON格式返回结果：
            {
                "补充用途": {
                    "401": "公寓式办公",
                    "402": "公寓式办公"
                }
            }
            """;
    private static final String ROOM_USAGE_SUPPLEMENT_PROMPT_NOTES = """
            - 只返回JSON格式的结果
            - 重点关注楼层范围规则（如"4-32层为公寓式办公"）
            - 用途名称要与之前保持一致
            """;
    private static final String ROOM_USAGE_PROMPT_SUFFIX = "\n\n勘测成果表内容：\n";
    /** 合同解析：单次调用，要求模型只返回 JSON */
    private static final String CONTRACT_PARSE_INSTRUCTION = "你是一个合同信息抽取助手。请从以下合同OCR识别文本中，精确提取以下三个字段，并仅输出一个JSON对象，不要输出任何其他说明、markdown代码块或换行。\n\n"
            + "字段说明：\n"
            + "- 合同编号：合同正文中的合同编号，通常包含“合同编号”或“编号”等字样，完整抄录。\n"
            + "- 出让方：土地出让合同中的出让人/出让方单位名称，完整抄录。\n"
            + "- 受让方：土地出让合同中的受让人/受让方单位名称，完整抄录。\n\n"
            + "输出格式（严格只输出此JSON，不要用```包裹）：\n"
            + "{\"合同编号\":\"\",\"出让方\":\"\",\"受让方\":\"\"}\n\n"
            + "若某字段在文本中未找到，该字段值为空字符串\"\"。\n\n"
            + "以下为合同OCR文本：\n\n";

    /** 项目方汇总 Excel 抽取：输入为预截取的汇总区块（declared totals）。 */
    private static final String PROJECT_PARTY_SUMMARY_PARSE_INSTRUCTION = "你是房地产项目数据抽取助手。下面提供的是Excel汇总区块片段（已按行列展开，R/C为原表行号列号）。\n"
            + "【最高优先级】只依据片段中可见的单元格内容抽取，严禁编造、推测、补全、推算任何数值。\n"
            + "若片段中不存在「项目方声明汇总」三行（合同约定/计容/差值 × 建筑/商业/住宅），则 totals 全部留空。\n"
            + "不得从明细行、小计行、合计行、表头或其他区域推断汇总声明数值。\n"
            + "标签可能被人工改写（如「合同建筑面积」≈「合同约定建筑面积」），请据语义识别，但数值必须来自对应单元格。\n"
            + "输出要求（必须同时满足）：\n"
            + "- 严格只输出一个JSON对象；\n"
            + "- 禁止输出任何解释、分析、提示、前后缀文本；\n"
            + "- 禁止输出markdown代码块标记（```）；\n"
            + "- 输出为紧凑JSON（不要美化缩进/不要多余空白）；\n"
            + "- 顶层只含 totals 一个键。\n\n"
            + "输出结构必须严格为：\n"
            + "{\n"
            + "  \"totals\": {\n"
            + "    \"contract_agreed_total_building_area\": number|null,\n"
            + "    \"buildable_total_building_area\": number|null,\n"
            + "    \"difference_total_building_area\": number|null,\n"
            + "    \"contract_agreed_commercial_area\": number|null,\n"
            + "    \"buildable_commercial_area\": number|null,\n"
            + "    \"difference_commercial_area\": number|null,\n"
            + "    \"contract_agreed_residential_area\": number|null,\n"
            + "    \"buildable_residential_area\": number|null,\n"
            + "    \"difference_residential_area\": number|null\n"
            + "  }\n"
            + "}\n\n"
            + "规则：\n"
            + "1) 仅在片段中明确存在三行汇总声明时填写对应数值；无三行则输出 {\"totals\":{}}；\n"
            + "2) 某格为空、看不清、无法对应时填 null，禁止用其他格或公式结果替代；\n"
            + "3) 数值只输出数字或 null；去掉单位、空格、千分位；括号负数如(85.56)输出为 -85.56；\n"
            + "4) 禁止用「合同减计容」推算差值，差值必须来自文件中差值行的单元格；\n"
            + "5) 值为 null 的键可省略。\n\n"
            + "以下为Excel文本：\n\n";

    /**
     * 通用扫描件/证照/工程图多模态单页识别：无固定 JSON 模式时由 lc-file 侧豆包多模态 OCR 策略对非专项类型回退到此提示词。
     */
    public static final String DOCUMENT_VISION_SINGLE_PAGE_DEFAULT_INSTRUCTION = """
            你是文档识别助手。当前图片为 PDF 的其中一页扫描或截图。
            请只依据图中可见内容输出，不要编造。优先使用 Markdown：保留标题、段落、列表；表格请用 Markdown 表格或清晰分行表示。
            不要输出思考过程。若整页几乎空白，只输出一行：空白页。
            """;

    /**
     * 规划复核表多模态识别（单页）：固定 header/rows JSON 结构，供解析器消费；在
     * {@link com.gov.landcheck.core.enums.FileContextType#PLANNING_REVIEW} 时使用。
     */
    public static final String PLANNING_REVIEW_VISION_SINGLE_PAGE_INSTRUCTION = """
            你是建筑工程文档识别助手。当前图片是「规划复核表」PDF 中的一页（可能含表头或表格续页）。
            请只根据图中可见内容抽取结构化数据，不要猜测；看不清或没有的字段用 null；数值只输出数字（不要单位）。

            严格只输出一个 JSON 对象（不要 markdown 代码块、不要前后说明），顶层结构为：
            {
              "header": {
                "project_name": string|null,
                "construction_unit": string|null,
                "design_unit": string|null,
                "construction_location": string|null,
                "land_use_nature": string|null,
                "contact_person": string|null,
                "contact_phone": string|null,
                "remarks": string|null
              },
              "rows": [
                {
                  "row_index": number|null,
                  "engineering_project": string|null,
                  "building_nature": string|null,
                  "construction_nature": string|null,
                  "building_count": number|null,
                  "above_ground_floors": number|null,
                  "below_ground_floors": number|null,
                  "height_m": number|null,
                  "base_area_m2": number|null,
                  "residential_residential_area": number|null,
                  "residential_hotel_apartment_area": number|null,
                  "residential_other_area": number|null,
                  "nr_above_commercial": number|null,
                  "nr_above_garage": number|null,
                  "nr_above_other": number|null,
                  "nr_below_commercial": number|null,
                  "nr_below_supporting": number|null,
                  "nr_below_other": number|null,
                  "above_ground_area": number|null,
                  "below_ground_area": number|null,
                  "total_area": number|null,
                  "far_above_ground": number|null,
                  "far_below_ground": number|null,
                  "is_summary_row": boolean
                }
              ]
            }

            规则：
            1) 只抽取本页可见的表头字段填入 header；若本页无表头，header 各字段可为 null。
            2) rows 只包含本页可见的数据行；合计行设 is_summary_row 为 true，普通数据行为 false。
            3) building_nature 保持原文（如 居住、商业、其它、商住 等）。
            4) 字段名必须严格使用上述英文 snake_case，不要新增键名。
            """;

    /**
     * 容量指标核查表多模态识别（单页）：提取「本次报建建筑面积」下合计/商业类/住宅类三列数值。
     */
    public static final String CAPACITY_INDICATOR_VISION_SINGLE_PAGE_INSTRUCTION = """
            你是建筑工程文档识别助手。当前图片是「容量指标核查表」PDF 的唯一一页扫描件。
            请只根据图中可见内容抽取结构化数据，不要猜测；看不清或没有的字段用 null；数值只输出数字（不要单位、千分位）。

            任务：在表格中找到标题为「本次报建建筑面积（平方米）」的区域。该区域下方有四列子标题：
            「合计」「商业类」「住宅类」「其它」。
            定位与「计入容积率的建筑面积」同一数据行（若行标题表述略有差异，选取该四列下的第一行有效数值行）。
            仅提取前三个数值列，忽略「其它」列。

            禁止读取以下区域的数值：
            - 「已报建建筑面积（平方米）」
            - 「总建筑面积指标（平方米）」
            - 「至本次已报建总建筑面积（平方米）」
            - 「剩余建筑面积指标」等其他区块

            数值必须以单元格原文为准，禁止用商业类+住宅类推算合计。

            严格只输出一个 JSON 对象（不要 markdown 代码块、不要前后说明），顶层结构为：
            {
              "total_area": number|null,
              "commercial_area": number|null,
              "residential_area": number|null
            }

            字段映射：
            - total_area ← 「合计」
            - commercial_area ← 「商业类」
            - residential_area ← 「住宅类」
            """;

    @PostConstruct
    void initRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int ms = Math.max(1, apiTimeoutSeconds) * 1000;
        factory.setConnectTimeout(ms);
        factory.setReadTimeout(ms);
        this.restTemplate = new RestTemplate(factory);
    }

    private static void sleepBackoffMillis(long millis) {
        try {
            Thread.sleep(Math.min(Math.max(0L, millis), 60_000L));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String chatCompletion(List<Map<String, String>> messages, Double temperature, Integer maxTokens)
            throws Exception {
        List<Map<String, Object>> cast = new ArrayList<>();
        for (Map<String, String> m : messages) {
            Map<String, Object> one = new HashMap<>();
            one.put("role", m.get("role"));
            one.put("content", m.get("content"));
            cast.add(one);
        }
        return postChatCompletions(cast, model, temperature, maxTokens);
    }

    @Override
    public String chatCompletionMultimodal(List<Map<String, Object>> messages, Double temperature, Integer maxTokens,
            String modelOverride)
            throws Exception {
        String useModel = (modelOverride != null && !modelOverride.isBlank()) ? modelOverride.trim() : visionModel;
        return postChatCompletions(messages, useModel, temperature, maxTokens);
    }

    private String postChatCompletions(List<Map<String, Object>> messages, String modelName, Double temperature,
            Integer maxTokens)
            throws Exception {
        int attempts = Math.max(1, maxHttpAttempts);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return postChatCompletionsOnce(messages, modelName, temperature, maxTokens);
            } catch (ResourceAccessException | HttpServerErrorException e) {
                if (attempt < attempts) {
                    log.warn("LLM HTTP 可重试失败 attempt={}/{}: {}", attempt, attempts, e.getMessage());
                    sleepBackoffMillis(200L * attempt);
                    continue;
                }
                log.error("大模型调用失败: {}", e.getMessage(), e);
                throw new Exception("大模型调用失败: " + e.getMessage(), e);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429 && attempt < attempts) {
                    log.warn("LLM HTTP 429 限流，重试 attempt={}/{}", attempt, attempts);
                    sleepBackoffMillis(400L * attempt);
                    continue;
                }
                log.error("大模型调用失败: {}", e.getMessage(), e);
                throw new Exception("大模型调用失败: " + e.getMessage(), e);
            } catch (Exception e) {
                log.error("大模型调用失败: {}", e.getMessage(), e);
                throw new Exception("大模型调用失败: " + e.getMessage(), e);
            }
        }
        throw new IllegalStateException("LLM 重试循环未返回结果");
    }

    @SuppressWarnings("unchecked")
    private String postChatCompletionsOnce(List<Map<String, Object>> messages, String modelName, Double temperature,
            Integer maxTokens)
            throws Exception {
        String url = apiUrl + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("messages", messages);
        requestBody.put("temperature", temperature != null ? temperature : DEFAULT_TEMPERATURE);

        if (maxTokens != null) {
            requestBody.put("max_tokens", maxTokens);
        }

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> responseEntity = restTemplate.exchange(url,
                org.springframework.http.HttpMethod.POST, requestEntity,
                new org.springframework.core.ParameterizedTypeReference<>() {
                });

        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            throw new Exception("API请求失败，状态码: " + responseEntity.getStatusCode()
                    + ", 响应: " + responseEntity.getBody());
        }

        Map<String, Object> response = responseEntity.getBody();
        if (response == null || !response.containsKey("choices")) {
            throw new Exception("API响应格式错误: " + response);
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new Exception("API响应中没有choices数据");
        }

        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null || !message.containsKey("content")) {
            throw new Exception("API响应中没有message content");
        }

        String content = extractContentString(message.get("content"));
        log.info("大模型响应成功，内容长度: {}", content != null ? content.length() : 0);

        return content != null ? content.trim() : "";
    }

    /**
     * 兼容 content 为 String 或 content 为数组（多模态片段拼接为文本，通常最后一项为 text）
     */
    private String extractContentString(Object contentObj) {
        if (contentObj == null) {
            return "";
        }
        if (contentObj instanceof String s) {
            return s;
        }
        if (contentObj instanceof List<?> parts) {
            StringBuilder sb = new StringBuilder();
            for (Object p : parts) {
                if (p instanceof Map<?, ?> m) {
                    Object type = m.get("type");
                    if ("text".equals(type)) {
                        Object t = m.get("text");
                        if (t != null) {
                            sb.append(t.toString());
                        }
                    }
                }
            }
            return sb.toString();
        }
        return contentObj.toString();
    }

    @Override
    public String chatCompletion(List<Map<String, String>> messages) throws Exception {
        return chatCompletion(messages, DEFAULT_TEMPERATURE, DEFAULT_MAX_TOKENS);
    }

    @Override
    public Map<String, Object> analyzeRoomUsage(String surveyText, List<String> roomNumbers, String projectId,
            Long fileRecordId) throws Exception {
        if (surveyText == null || surveyText.trim().isEmpty()) {
            throw new IllegalArgumentException("勘测成果表文本不能为空");
        }

        String roomNumbersStr = roomNumbers != null && !roomNumbers.isEmpty()
                ? String.join(", ", roomNumbers.subList(0, Math.min(roomNumbers.size(), 200)))
                : "无";
        String prompt = buildInitialRoomUsagePrompt(roomNumbersStr);

        String fullPrompt = prompt + surveyText;

        // 构建消息
        List<Map<String, String>> messages = Arrays.asList(
                Map.of("role", "user", "content", fullPrompt));

        String response = chatCompletion(messages, 0.1, 2000);
        log.info("户室用途分析完成 response: {}", response);

        // 返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("prompt", fullPrompt);
        result.put("response", response);
        result.put("analysisRound", 1);
        return result;
    }

    @Override
    public Map<String, Object> supplementRoomUsage(String surveyText, List<String> roomNumbers,
            Map<String, String> filledRooms, Map<String, Object> previousAnalysis, String projectId, Long fileRecordId)
            throws Exception {

        // 计算未填充的房间
        List<String> unfilledRoomNumbers = roomNumbers.stream()
                .filter(room -> !filledRooms.containsKey(room) ||
                        filledRooms.get(room) == null ||
                        filledRooms.get(room).trim().isEmpty())
                .collect(Collectors.toList());

        if (unfilledRoomNumbers.isEmpty()) {
            log.info("所有房间已填充用途，无需补充分析");
            return previousAnalysis;
        }

        String previousResponse = (String) previousAnalysis.get("response");
        String filledExamples = filledRooms.entrySet().stream().limit(10)
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining(", "));
        String unfilledRoomsStr = String.join(", ",
                unfilledRoomNumbers.subList(0, Math.min(unfilledRoomNumbers.size(), 100)));
        String prompt = buildSupplementRoomUsagePrompt(previousResponse, filledExamples, unfilledRoomsStr);
        String fullPrompt = prompt + surveyText;

        // 构建消息
        List<Map<String, String>> messages = Arrays.asList(
                Map.of("role", "user", "content", fullPrompt));

        String response = chatCompletion(messages, 0.1, 2000);
        log.info("补充用途分析完成，未填充房间数: {}, response: {}", unfilledRoomNumbers.size(), response);

        // 返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("prompt", fullPrompt);
        result.put("response", response);
        result.put("analysisRound", 2);
        result.put("unfilledRooms", unfilledRoomNumbers);
        return result;
    }

    @Override
    public Map<String, Object> parseContract(String contractText, String projectId, Long fileRecordId)
            throws Exception {
        if (contractText == null || contractText.trim().isEmpty()) {
            throw new IllegalArgumentException("合同OCR文本不能为空");
        }
        String fullPrompt = CONTRACT_PARSE_INSTRUCTION + contractText.trim();
        List<Map<String, String>> messages = Arrays.asList(
                Map.of("role", "user", "content", fullPrompt));
        String response = chatCompletion(messages, 0.1, 1024);
        Map<String, Object> result = new HashMap<>();
        result.put("prompt", fullPrompt);
        result.put("response", response != null ? response.trim() : "");
        log.info("合同解析LLM调用完成，response长度: {}", response != null ? response.length() : 0);
        return result;
    }

    private static final String ROOM_TABLE_PARSE_INSTRUCTION = """
            你是实测报告户室面积对照表抽取助手。请从以下 HTML 表格内容中抽取所有户室行与合计行数值。
            输出要求：严格只输出一个 JSON 对象，禁止 markdown 代码块与任何说明文字。
            结构：
            {"rows":[{"level":"层次文本","number":"户室号","building_area":数字,"inner_area":数字,"balcony_area":数字,"shared_area":数字,"structure":"结构","usage":"用途","remark":"备注"}],
             "totals":{"building_area":数字或null,"inner_area":数字或null,"balcony_area":数字或null,"shared_area":数字或null}}
            数字字段不存在时用 null。合计行放入 totals，普通户室放入 rows。以下为 HTML：

            """;

    @Override
    public Map<String, Object> extractRoomTableFromHtml(String pageHtml, String projectId, Long fileRecordId)
            throws Exception {
        if (pageHtml == null || pageHtml.trim().isEmpty()) {
            throw new IllegalArgumentException("户室表 HTML 不能为空");
        }
        String fullPrompt = ROOM_TABLE_PARSE_INSTRUCTION + pageHtml.trim();
        List<Map<String, String>> messages = Arrays.asList(
                Map.of("role", "user", "content", fullPrompt));
        String response = chatCompletion(messages, 0.1, 4096);
        Map<String, Object> result = new HashMap<>();
        result.put("prompt", fullPrompt);
        result.put("response", response != null ? response.trim() : "");
        log.info("户室表 LLM 抽取完成, projectId={}, fileRecordId={}, responseLen={}",
                projectId, fileRecordId, response != null ? response.length() : 0);
        return result;
    }

    @Override
    public Map<String, Object> parseProjectPartySummary(String workbookText, String projectId, Long fileRecordId)
            throws Exception {
        if (workbookText == null || workbookText.trim().isEmpty()) {
            throw new IllegalArgumentException("项目方汇总文本不能为空");
        }
        String fullPrompt = PROJECT_PARTY_SUMMARY_PARSE_INSTRUCTION + workbookText.trim();
        List<Map<String, String>> messages = Arrays.asList(
                Map.of("role", "user", "content", fullPrompt));
        String response = chatCompletion(messages, 0.1, PROJECT_PARTY_SUMMARY_MAX_TOKENS);
        Map<String, Object> result = new HashMap<>();
        result.put("prompt", fullPrompt);
        result.put("response", response != null ? response.trim() : "");
        log.info("项目方汇总解析LLM调用完成，response长度: {}", response != null ? response.length() : 0);
        return result;
    }

    private String buildInitialRoomUsagePrompt(String roomNumbersStr) {
        String body = String.format(ROOM_USAGE_INITIAL_PROMPT_TEMPLATE, roomNumbersStr != null ? roomNumbersStr : "无");
        return body + "\n\n注意事项：\n" + ROOM_USAGE_INITIAL_PROMPT_NOTES + ROOM_USAGE_PROMPT_SUFFIX;
    }

    private String buildSupplementRoomUsagePrompt(String previousResponse, String filledExamples,
            String unfilledRoomsStr) {
        String body = String.format(
                ROOM_USAGE_SUPPLEMENT_PROMPT_TEMPLATE,
                previousResponse != null ? previousResponse : "",
                filledExamples != null ? filledExamples : "",
                unfilledRoomsStr != null ? unfilledRoomsStr : "");
        return body + "\n\n注意事项：\n" + ROOM_USAGE_SUPPLEMENT_PROMPT_NOTES + ROOM_USAGE_PROMPT_SUFFIX;
    }
}