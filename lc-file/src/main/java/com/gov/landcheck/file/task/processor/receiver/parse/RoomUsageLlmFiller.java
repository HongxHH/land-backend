package com.gov.landcheck.file.task.processor.receiver.parse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParsedDataHeader;
import com.gov.landcheck.core.bo.entity.RoomInfo;
import com.gov.landcheck.core.service.LLMService;

import lombok.extern.slf4j.Slf4j;

/**
 * 通过大模型分析勘测成果表，填充户室用途（无用途列时使用）
 */
@Slf4j
@Component
public class RoomUsageLlmFiller {

    private static final int MAX_USAGE_ROUNDS = 5;

    @Autowired
    private LLMService llmService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<RoomInfo> fillRoomUsageFromSurveyConclusion(
            List<RoomInfo> roomInfos, String surveyText, ParsedDataHeader header, FileRecord fileRecord) {
        if (roomInfos == null || roomInfos.isEmpty() || surveyText == null || surveyText.trim().isEmpty()) {
            return roomInfos;
        }
        Objects.requireNonNull(header, "ParsedDataHeader 不能为 null");
        log.debug("表格中没有用途列，开始通过大模型分析填充用途信息");

        try {
            List<String> allRoomNumbers = collectRoomNumbers(roomInfos);
            if (allRoomNumbers.isEmpty()) {
                log.warn("没有有效的房间号，无法进行用途分析");
                return roomInfos;
            }
            List<String> pendingRoomNumbers = collectUnfilledRoomNumbers(roomInfos);
            if (pendingRoomNumbers.isEmpty()) {
                log.debug("所有户室已有用途，跳过用途 LLM");
                return roomInfos;
            }
            log.debug("共 {} 个户室待填充用途（总 {}）", pendingRoomNumbers.size(), allRoomNumbers.size());

            String projectId = header.getProjectId() != null ? header.getProjectId().toString() : "unknown";
            int totalCount = allRoomNumbers.size();
            List<RoomInfo> filledRoomInfos = new ArrayList<>(roomInfos);
            String llmResponse = null;
            String lastPrompt = null;
            Map<String, Object> lastRoundResult = null;
            long previousFilledCount = countFilledUsage(filledRoomInfos);
            int stagnantRounds = 0;

            for (int round = 0; round < MAX_USAGE_ROUNDS; round++) {
                long filledCount = countFilledUsage(filledRoomInfos);
                if (filledCount >= totalCount) {
                    break;
                }
                if (filledCount == previousFilledCount) {
                    stagnantRounds++;
                    if (stagnantRounds >= 2) {
                        log.debug("用途填充连续 {} 轮无进展，提前结束", stagnantRounds);
                        break;
                    }
                } else {
                    stagnantRounds = 0;
                }
                previousFilledCount = filledCount;

                if (round == 0) {
                    pendingRoomNumbers = collectUnfilledRoomNumbers(filledRoomInfos);
                    if (pendingRoomNumbers.isEmpty()) {
                        break;
                    }
                    lastRoundResult = llmService.analyzeRoomUsage(surveyText, pendingRoomNumbers, projectId,
                            fileRecord != null ? fileRecord.getId() : null);
                    llmResponse = (String) lastRoundResult.get("response");
                    lastPrompt = (String) lastRoundResult.get("prompt");
                    Map<String, Object> usageRules = parseUsageResponse(llmResponse);
                    filledRoomInfos = applyUsageRulesToRooms(roomInfos, usageRules);
                } else {
                    pendingRoomNumbers = collectUnfilledRoomNumbers(filledRoomInfos);
                    if (pendingRoomNumbers.isEmpty()) {
                        break;
                    }
                    Map<String, String> filledRooms = filledRoomInfos.stream()
                            .filter(room -> room.getRoomUsage() != null && !room.getRoomUsage().trim().isEmpty())
                            .collect(Collectors.toMap(RoomInfo::getRoomNumber, RoomInfo::getRoomUsage));
                    try {
                        Map<String, Object> supplementResult = llmService.supplementRoomUsage(
                                surveyText, pendingRoomNumbers, filledRooms, lastRoundResult, projectId,
                                fileRecord != null ? fileRecord.getId() : null);
                        String supplementResponse = (String) supplementResult.get("response");
                        lastRoundResult = supplementResult;
                        lastPrompt = (String) supplementResult.get("prompt");
                        if (supplementResponse != null && !supplementResponse.trim().isEmpty()) {
                            Map<String, String> supplementRules = parseSupplementResponse(supplementResponse);
                            filledRoomInfos = applySupplementRulesToRooms(filledRoomInfos, supplementRules);
                            llmResponse = (llmResponse != null ? llmResponse + "\n\n=== 补充分析结果 ===\n" : "")
                                    + supplementResponse;
                        }
                    } catch (Exception e) {
                        log.warn("第 {} 轮补充分析失败: {}", round + 1, e.getMessage());
                        break;
                    }
                }
            }

            long filledCount = filledRoomInfos.stream()
                    .filter(room -> room.getRoomUsage() != null && !room.getRoomUsage().trim().isEmpty())
                    .count();
            header.setModelAnalysisResult(llmResponse);
            header.setModelPrompt(lastPrompt);

            log.debug("用途分析完成，已填充 {}/{} 个房间", filledCount, totalCount);
            return filledRoomInfos;
        } catch (Exception e) {
            Long fileRecordId = fileRecord != null ? fileRecord.getId() : null;
            log.error("填充户室用途时出错: fileRecordId={}, error={}", fileRecordId, e.getMessage(), e);
            header.setModelPrompt(null);
            header.setModelAnalysisResult(null);
            return roomInfos;
        }
    }

    private static List<String> collectRoomNumbers(List<RoomInfo> roomInfos) {
        List<String> roomNumbers = new ArrayList<>();
        for (RoomInfo room : roomInfos) {
            if (room.getRoomNumber() != null && !room.getRoomNumber().trim().isEmpty()) {
                roomNumbers.add(room.getRoomNumber().trim());
            }
        }
        return roomNumbers;
    }

    private static List<String> collectUnfilledRoomNumbers(List<RoomInfo> roomInfos) {
        List<String> roomNumbers = new ArrayList<>();
        for (RoomInfo room : roomInfos) {
            if (room.getRoomNumber() != null && !room.getRoomNumber().trim().isEmpty()
                    && (room.getRoomUsage() == null || room.getRoomUsage().trim().isEmpty())) {
                roomNumbers.add(room.getRoomNumber().trim());
            }
        }
        return roomNumbers;
    }

    private static long countFilledUsage(List<RoomInfo> roomInfos) {
        return roomInfos.stream()
                .filter(room -> room.getRoomUsage() != null && !room.getRoomUsage().trim().isEmpty())
                .count();
    }

    private Map<String, Object> parseUsageResponse(String llmResponse) {
        Map<String, Object> usageRules = new HashMap<>();
        usageRules.put("defaultUsage", "");
        usageRules.put("specialUsage", new HashMap<String, String>());
        usageRules.put("rangeUsage", new HashMap<String, String>());
        if (llmResponse == null || llmResponse.trim().isEmpty())
            return usageRules;

        try {
            int startIdx = llmResponse.indexOf('{');
            int endIdx = llmResponse.lastIndexOf('}') + 1;
            if (startIdx == -1 || endIdx <= startIdx)
                return usageRules;

            String jsonStr = llmResponse.substring(startIdx, endIdx);
            JsonNode root = objectMapper.readTree(jsonStr);
            if (root.has("默认用途"))
                usageRules.put("defaultUsage", root.get("默认用途").asText());
            if (root.has("特殊用途"))
                usageRules.put("specialUsage", jsonObjectToMap(root.get("特殊用途")));
            if (root.has("范围用途"))
                usageRules.put("rangeUsage", jsonObjectToMap(root.get("范围用途")));
        } catch (Exception e) {
            log.warn("解析大模型用途响应失败: {}", e.getMessage());
        }
        return usageRules;
    }

    private static Map<String, String> jsonObjectToMap(JsonNode node) {
        Map<String, String> map = new HashMap<>();
        if (node == null || !node.isObject())
            return map;
        node.properties().forEach(entry -> map.put(entry.getKey(), entry.getValue().asText()));
        return map;
    }

    private List<RoomInfo> applyUsageRulesToRooms(List<RoomInfo> roomInfos, Map<String, Object> usageRules) {
        String defaultUsage = (String) usageRules.get("defaultUsage");
        @SuppressWarnings("unchecked")
        Map<String, String> specialUsage = (Map<String, String>) usageRules.get("specialUsage");
        @SuppressWarnings("unchecked")
        Map<String, String> rangeUsage = (Map<String, String>) usageRules.get("rangeUsage");

        List<RoomInfo> result = new ArrayList<>();
        for (RoomInfo room : roomInfos) {
            RoomInfo newRoom = copyRoomInfo(room);
            if ((newRoom.getRoomUsage() == null || newRoom.getRoomUsage().trim().isEmpty())
                    && room.getRoomNumber() != null) {
                String roomNumber = room.getRoomNumber().trim();
                String usage = null;
                if (specialUsage != null && specialUsage.containsKey(roomNumber)) {
                    usage = specialUsage.get(roomNumber);
                    log.debug("房间 {} 匹配特殊用途: {}", roomNumber, usage);
                } else if (rangeUsage != null && !rangeUsage.isEmpty()) {
                    usage = findRangeUsage(roomNumber, room.getRoomLevel(), rangeUsage);
                    if (usage != null)
                        log.debug("房间 {} (层:{}) 匹配范围用途: {}", roomNumber, room.getRoomLevel(), usage);
                }
                if (usage == null && defaultUsage != null && !defaultUsage.trim().isEmpty())
                    usage = defaultUsage;
                if (usage != null) {
                    newRoom.setRoomUsage(usage);
                    log.debug("房间 {} 填充用途: {}", roomNumber, usage);
                }
            }
            result.add(newRoom);
        }
        return result;
    }

    private Map<String, String> parseSupplementResponse(String supplementResponse) {
        Map<String, String> supplementUsage = new HashMap<>();
        if (supplementResponse == null || supplementResponse.trim().isEmpty())
            return supplementUsage;
        try {
            int startIdx = supplementResponse.indexOf('{');
            int endIdx = supplementResponse.lastIndexOf('}') + 1;
            if (startIdx != -1 && endIdx > startIdx) {
                JsonNode root = objectMapper.readTree(supplementResponse.substring(startIdx, endIdx));
                if (root.has("补充用途"))
                    supplementUsage = jsonObjectToMap(root.get("补充用途"));
            }
        } catch (Exception e) {
            log.warn("解析补充分析响应失败: {}", e.getMessage());
        }
        return supplementUsage;
    }

    private List<RoomInfo> applySupplementRulesToRooms(List<RoomInfo> roomInfos, Map<String, String> supplementRules) {
        if (supplementRules == null || supplementRules.isEmpty())
            return roomInfos;
        List<RoomInfo> result = new ArrayList<>();
        for (RoomInfo room : roomInfos) {
            RoomInfo newRoom = copyRoomInfo(room);
            if ((newRoom.getRoomUsage() == null || newRoom.getRoomUsage().trim().isEmpty())
                    && room.getRoomNumber() != null && supplementRules.containsKey(room.getRoomNumber().trim())) {
                String usage = supplementRules.get(room.getRoomNumber().trim());
                if (usage != null && !usage.trim().isEmpty()) {
                    newRoom.setRoomUsage(usage);
                    log.debug("房间 {} 通过补充规则填充用途: {}", room.getRoomNumber(), usage);
                }
            }
            result.add(newRoom);
        }
        return result;
    }

    private static RoomInfo copyRoomInfo(RoomInfo src) {
        RoomInfo dest = new RoomInfo();
        dest.setId(src.getId());
        dest.setFileRecordId(src.getFileRecordId());
        dest.setProjectId(src.getProjectId());
        dest.setRoomLevel(src.getRoomLevel());
        dest.setRoomNumber(src.getRoomNumber());
        dest.setBuildingArea(src.getBuildingArea());
        dest.setInnerArea(src.getInnerArea());
        dest.setBalconyArea(src.getBalconyArea());
        dest.setSharedArea(src.getSharedArea());
        dest.setRoomStructure(src.getRoomStructure());
        dest.setRoomUsage(src.getRoomUsage());
        dest.setRemark(src.getRemark());
        return dest;
    }

    String findRangeUsage(String roomNumber, String roomLevel, Map<String, String> rangeUsage) {
        if (rangeUsage == null || rangeUsage.isEmpty())
            return null;
        try {
            for (Map.Entry<String, String> entry : rangeUsage.entrySet()) {
                String range = entry.getKey();
                if (range.contains("层") && range.contains("-")) {
                    try {
                        String[] parts = range.replace("层", "").split("-");
                        if (parts.length == 2) {
                            int startLevel = Integer.parseInt(parts[0].trim());
                            int endLevel = Integer.parseInt(parts[1].trim());
                            int levelToCheck = levelToCheck(roomLevel, roomNumber);
                            if (levelToCheck >= startLevel && levelToCheck <= endLevel) {
                                log.debug("房间 {} (层数:{}) 匹配层级范围 {}: {}", roomNumber, levelToCheck, range,
                                        entry.getValue());
                                return entry.getValue();
                            }
                        }
                    } catch (NumberFormatException ignored) {
                    }
                } else if (range.contains("层") && !range.contains("-")) {
                    try {
                        int targetLevel = Integer.parseInt(range.replace("层", "").trim());
                        int levelToCheck = levelToCheck(roomLevel, roomNumber);
                        if (levelToCheck == targetLevel) {
                            log.debug("房间 {} (层数:{}) 匹配单层规则 {}: {}", roomNumber, roomLevel, range, entry.getValue());
                            return entry.getValue();
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            int roomNum = Integer.parseInt(roomNumber.replaceAll("[^0-9]", ""));
            for (Map.Entry<String, String> entry : rangeUsage.entrySet()) {
                String range = entry.getKey();
                if (range.contains("层"))
                    continue;
                if (range.contains("-")) {
                    String[] parts = range.split("-");
                    if (parts.length == 2) {
                        try {
                            int start = Integer.parseInt(parts[0].replaceAll("[^0-9]", ""));
                            int end = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
                            if (roomNum >= start && roomNum <= end) {
                                log.debug("房间 {} 匹配房间号范围 {}: {}", roomNumber, range, entry.getValue());
                                return entry.getValue();
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                } else {
                    try {
                        int singleRoom = Integer.parseInt(range.replaceAll("[^0-9]", ""));
                        if (roomNum == singleRoom) {
                            log.debug("房间 {} 匹配单个房间号 {}: {}", roomNumber, range, entry.getValue());
                            return entry.getValue();
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (NumberFormatException e) {
            log.debug("房间号 {} 无法解析为数字，使用字符串匹配", roomNumber);
            for (Map.Entry<String, String> entry : rangeUsage.entrySet()) {
                if (entry.getKey().contains(roomNumber) || roomNumber.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private int levelToCheck(String roomLevel, String roomNumber) {
        if (roomLevel != null && !roomLevel.trim().isEmpty()) {
            try {
                return Integer.parseInt(roomLevel.trim());
            } catch (NumberFormatException e) {
                return extractFloorLevel(roomNumber);
            }
        }
        return extractFloorLevel(roomNumber);
    }

    int extractFloorLevel(String roomNumber) {
        if (roomNumber == null || roomNumber.trim().isEmpty())
            return -1;
        try {
            String numericPart = roomNumber.replaceAll("[^0-9]", "");
            if (numericPart.length() >= 3) {
                int levelDigits = numericPart.length() == 3 ? 1 : 2;
                return Integer.parseInt(numericPart.substring(0, levelDigits));
            } else if (numericPart.length() == 2) {
                return Integer.parseInt(String.valueOf(numericPart.charAt(0)));
            } else {
                return Integer.parseInt(numericPart);
            }
        } catch (Exception e) {
            log.warn("无法从房间号 {} 提取层数: {}", roomNumber, e.getMessage());
            return -1;
        }
    }
}
