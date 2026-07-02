package com.gov.landcheck.core.service;

import java.util.List;
import java.util.Map;

/**
 * 大模型服务接口
 * 提供统一的LLM调用接口，支持不同模型和提示词模板
 *
 * @author system
 * @date 2025/01/20
 */
public interface LLMService {

    /**
     * 调用大模型进行聊天对话
     *
     * @param messages 消息列表，格式为 [{"role": "user", "content": "消息内容"}]
     * @param temperature 温度参数，控制随机性 (0.0-1.0)
     * @param maxTokens 最大token数限制
     * @return 模型响应内容
     * @throws Exception 调用失败时抛出异常
     */
    String chatCompletion(List<Map<String, String>> messages, Double temperature, Integer maxTokens) throws Exception;

    /**
     * 调用大模型进行聊天对话（使用默认参数）
     *
     * @param messages 消息列表
     * @return 模型响应内容
     * @throws Exception 调用失败时抛出异常
     */
    String chatCompletion(List<Map<String, String>> messages) throws Exception;

    /**
     * 分析户室用途（多轮对话架构）
     *
     * @param surveyText 勘测成果表文本内容
     * @param roomNumbers 房间号列表
     * @param projectId 项目ID
     * @param fileRecordId 来源文件ID，可为 null
     * @return 用途分析结果，包含prompt和response
     * @throws Exception 分析失败时抛出异常
     */
    Map<String, Object> analyzeRoomUsage(String surveyText, List<String> roomNumbers, String projectId, Long fileRecordId) throws Exception;

    /**
     * 验证和补充用途分析结果
     *
     * @param surveyText 勘测成果表文本内容
     * @param roomNumbers 所有房间号列表
     * @param filledRooms 已填充用途的房间映射
     * @param previousAnalysis 之前的分析结果
     * @param projectId 项目ID
     * @param fileRecordId 来源文件ID，可为 null
     * @return 补充分析结果
     * @throws Exception 分析失败时抛出异常
     */
    Map<String, Object> supplementRoomUsage(String surveyText, List<String> roomNumbers,
            Map<String, String> filledRooms, Map<String, Object> previousAnalysis, String projectId, Long fileRecordId) throws Exception;


    /**
     * 合同信息抽取：从合同 OCR 文本中提取合同编号、出让方、受让方（单次调用，返回结构化 JSON 字符串）
     *
     * @param contractText 合同 OCR 文本（建议为前 20 页合并文本）
     * @param projectId    项目 ID，可为 null
     * @param fileRecordId 来源文件 ID，可为 null
     * @return 包含 prompt、response 的 Map；response 为模型返回的 JSON 字符串
     */
    Map<String, Object> parseContract(String contractText, String projectId, Long fileRecordId) throws Exception;

    /**
     * 项目方汇总表抽取：从Excel转文本结果中抽取行级数据与底部三行汇总（返回结构化JSON字符串）。
     *
     * @param workbookText Excel文本
     * @param projectId 项目ID，可为 null
     * @param fileRecordId 来源文件ID，可为 null
     * @return 包含 prompt、response 的 Map；response 为模型返回的 JSON 字符串
     */
    Map<String, Object> parseProjectPartySummary(String workbookText, String projectId, Long fileRecordId) throws Exception;

    /**
     * 从单页户室面积对照表 HTML 抽取行级户室与合计（解析结构失败时的兜底）。
     *
     * @return 包含 prompt、response 的 Map；response 为 JSON 字符串
     */
    Map<String, Object> extractRoomTableFromHtml(String pageHtml, String projectId, Long fileRecordId) throws Exception;

    /**
     * 多模态对话：messages 中每条可为 {@code { "role":"user", "content": String | List&lt;Map&gt; }}，
     * content 为列表时项形如 {@code {"type":"text","text":"..."}} 或
     * {@code {"type":"image_url","image_url":{"url":"data:image/png;base64,..."}}}。
     *
     * @param model 模型 endpoint id，null 时使用配置中的 vision 模型
     */
    String chatCompletionMultimodal(List<Map<String, Object>> messages, Double temperature, Integer maxTokens, String model)
            throws Exception;
}