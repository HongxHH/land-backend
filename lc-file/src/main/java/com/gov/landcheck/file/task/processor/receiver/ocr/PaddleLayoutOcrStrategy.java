package com.gov.landcheck.file.task.processor.receiver.ocr;

import java.util.ArrayList;

import java.util.Base64;

import java.util.HashMap;

import java.util.List;

import java.util.Map;

import org.apache.hc.client5.http.classic.methods.HttpPost;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

import org.apache.hc.client5.http.impl.classic.HttpClients;

import org.apache.hc.core5.http.ClassicHttpResponse;

import org.apache.hc.core5.http.ContentType;

import org.apache.hc.core5.http.HttpEntity;

import org.apache.hc.core5.http.io.entity.EntityUtils;

import org.apache.hc.core5.http.io.entity.StringEntity;

import org.apache.hc.core5.util.Timeout;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Service;

import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.gov.landcheck.core.enums.FileContextType;

import com.gov.landcheck.file.dto.OCRPageResult;

import com.gov.landcheck.file.dto.OCRProcessResult;

import com.gov.landcheck.file.task.base.TaskData;

import com.gov.landcheck.file.task.base.TaskException;

import com.gov.landcheck.file.utils.GridFSUtils;

import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * 本地 Paddle 版面解析 OCR：从 GridFS 取 PDF，调用 layout-parsing API，解析版面结果。
 * 
 */

@Slf4j

@Service

public class PaddleLayoutOcrStrategy implements OcrProcessingStrategy {

    @Value("${ocr.api.url:http://localhost:8080/layout-parsing}")

    private String ocrApiUrl;

    @Value("${ocr.api.timeout:300000}")

    private int apiTimeout;

    @Resource

    private GridFSUtils gridFSUtils;

    @Resource

    private DoubaoVisionOcrStrategy doubaoVisionOcrStrategy;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final CloseableHttpClient httpClient = HttpClients.createDefault();

    @Override

    public boolean supports(FileContextType fileContextType) {

        return !doubaoVisionOcrStrategy.supports(fileContextType);

    }

    @Override

    public OCRProcessResult process(TaskData taskData) throws Exception {

        var fileRecord = taskData.getFileRecord();

        String gridfsId = StringUtils.hasText(taskData.getPreprocessGridfsId())

                ? taskData.getPreprocessGridfsId()

                : fileRecord.getGridfsId();

        return processGridfsPdf(gridfsId);

    }

    private OCRProcessResult processGridfsPdf(String gridfsId) throws Exception {

        long startTime = System.currentTimeMillis();

        try {

            Map<String, Object> requestBody = new HashMap<>();

            requestBody.put("file", getFileAsBase64(gridfsId));

            requestBody.put("fileType", 0);

            requestBody.put("visualize", false);

            requestBody.put("useDocOrientationClassify", false);

            requestBody.put("useDocUnwarping", false);

            String responseJson = callOCRApi(requestBody);

            JsonNode responseNode = objectMapper.readTree(responseJson);

            JsonNode errorCodeNode = responseNode.get("errorCode");

            if (errorCodeNode != null && !errorCodeNode.isNull() && errorCodeNode.asInt() != 0) {

                throw new TaskException(TaskException.ErrorCode.OCR_API_ERROR,

                        "OCR", null, null,

                        "OCR API 返回错误: errorCode=" + errorCodeNode.asInt()

                                + ", errorMsg=" + responseNode.path("errorMsg").asText("未知错误"));

            }

            JsonNode resultNode = responseNode.get("result");

            if (resultNode == null || resultNode.isNull()) {

                throw new TaskException(TaskException.ErrorCode.OCR_API_ERROR,

                        "OCR", null, null,

                        "OCR API 响应中缺少 result 节点，原始响应: " + truncateForLog(responseJson));

            }

            JsonNode layoutResults = resultNode.get("layoutParsingResults");

            if (layoutResults == null || !layoutResults.isArray()) {

                throw new TaskException(TaskException.ErrorCode.OCR_API_ERROR,

                        "OCR", null, null,

                        "OCR API 响应中缺少 layoutParsingResults，原始响应: " + truncateForLog(responseJson));

            }

            List<OCRPageResult> pageResults = parseOCRResults(layoutResults);

            long processingTime = System.currentTimeMillis() - startTime;

            OCRProcessResult result = new OCRProcessResult(pageResults);

            result.setProcessingTimeMs(processingTime);

            return result;

        } catch (TaskException e) {

            throw e;

        } catch (Exception e) {

            throw new TaskException(TaskException.ErrorCode.OCR_FAILED,

                    "OCR", null, null,

                    "OCR处理失败: " + e.getMessage(), e);

        }

    }

    private static String truncateForLog(String s) {

        if (s == null) {

            return "";

        }

        return s.length() > 800 ? s.substring(0, 800) + "…" : s;

    }

    private String getFileAsBase64(String gridfsId) throws Exception {

        byte[] fileBytes = gridFSUtils.getFileBytes(gridfsId);

        return Base64.getEncoder().encodeToString(fileBytes);

    }

    private String callOCRApi(Map<String, Object> requestBody) throws Exception {

        String requestJson = objectMapper.writeValueAsString(requestBody);

        HttpPost httpPost = new HttpPost(ocrApiUrl);

        httpPost.setEntity(new StringEntity(requestJson, ContentType.APPLICATION_JSON));

        httpPost.setHeader("Content-Type", "application/json");

        httpPost.setConfig(org.apache.hc.client5.http.config.RequestConfig.custom()

                .setConnectTimeout(Timeout.ofMilliseconds(apiTimeout))

                .setResponseTimeout(Timeout.ofMilliseconds(apiTimeout))

                .build());

        try (ClassicHttpResponse response = httpClient.execute(httpPost)) {

            int statusCode = response.getCode();

            HttpEntity entity = response.getEntity();

            String responseBody = entity == null ? "" : EntityUtils.toString(entity);

            if (statusCode != 200) {

                throw new TaskException(TaskException.ErrorCode.OCR_API_ERROR,

                        "OCR", null, null,

                        "OCR API 请求失败，状态码: " + statusCode + ", 响应: " + responseBody);

            }

            return responseBody;

        }

    }

    private List<OCRPageResult> parseOCRResults(JsonNode layoutResults) {

        List<OCRPageResult> pageResults = new ArrayList<>();

        for (int pageIndex = 0; pageIndex < layoutResults.size(); pageIndex++) {

            JsonNode pageResult = layoutResults.get(pageIndex);

            OCRPageResult ocrPageResult = new OCRPageResult(pageIndex + 1);

            try {

                ocrPageResult.setRawOcrResult(pageResult.toString());

                JsonNode markdownNode = pageResult.get("markdown");

                if (markdownNode != null && markdownNode.has("text")) {

                    ocrPageResult.setMarkdownText(markdownNode.get("text").asText());

                }

                JsonNode outputImagesNode = pageResult.get("outputImages");

                if (outputImagesNode != null && !outputImagesNode.isNull()) {

                    List<String> outputImages = new ArrayList<>();

                    outputImagesNode.fieldNames().forEachRemaining(fieldName ->

                    outputImages.add(outputImagesNode.get(fieldName).asText()));

                    if (!outputImages.isEmpty()) {

                        ocrPageResult.setOutputImages(outputImages);

                    }

                }

                ocrPageResult.setSuccess(true);

            } catch (Exception e) {

                log.warn("解析第{}页 OCR 结果失败: {}", pageIndex + 1, e.getMessage());

                ocrPageResult.setSuccess(false);

                ocrPageResult.setErrorMessage(e.getMessage());

            }

            pageResults.add(ocrPageResult);

        }

        return pageResults;

    }

}
