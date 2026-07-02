package com.gov.landcheck.file.task.processor.receiver.parse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.util.StringUtils;

import com.gov.landcheck.core.bo.entity.ParsedDataItem;
import com.gov.landcheck.core.bo.entity.RoomInfo;

import lombok.Getter;

/**
 * 户室表解析后处理：去重与 OCR 合计择优。
 */
public final class RoomTablePostProcessor {

    private static final BigDecimal TOTAL_TOLERANCE = new BigDecimal("0.05");

    private static final Map<String, Function<RoomInfo, BigDecimal>> DIMENSION_GETTERS = Map.of(
            "room_info_building_area_sum_from_ocr", RoomInfo::getBuildingArea,
            "room_info_inner_area_sum_from_ocr", RoomInfo::getInnerArea,
            "room_info_balcony_area_sum_from_ocr", RoomInfo::getBalconyArea,
            "room_info_shared_area_sum_from_ocr", RoomInfo::getSharedArea);

    private RoomTablePostProcessor() {
    }

    @Getter
    public static final class DedupResult {
        private final List<RoomInfo> rooms;
        private final int removedCount;

        public DedupResult(List<RoomInfo> rooms, int removedCount) {
            this.rooms = rooms;
            this.removedCount = removedCount;
        }
    }

    public static DedupResult deduplicateRooms(List<RoomInfo> roomInfos) {
        if (roomInfos == null || roomInfos.isEmpty()) {
            return new DedupResult(roomInfos != null ? roomInfos : List.of(), 0);
        }
        Map<String, RoomInfo> merged = new LinkedHashMap<>();
        int removed = 0;
        for (RoomInfo room : roomInfos) {
            String key = buildRoomKey(room);
            RoomInfo existing = merged.get(key);
            if (existing == null) {
                merged.put(key, room);
            } else {
                merged.put(key, pickBetterRoom(existing, room));
                removed++;
            }
        }
        return new DedupResult(new ArrayList<>(merged.values()), removed);
    }

    private static String buildRoomKey(RoomInfo room) {
        String level = normalizeKeyPart(room.getRoomLevel());
        String number = normalizeKeyPart(room.getRoomNumber());
        if (!StringUtils.hasText(number)) {
            BigDecimal area = room.getBuildingArea();
            return level + "|#area:" + (area != null ? area.toPlainString() : "null");
        }
        return level + "|" + number;
    }

    private static String normalizeKeyPart(String value) {
        return value != null ? value.trim() : "";
    }

    private static RoomInfo pickBetterRoom(RoomInfo a, RoomInfo b) {
        int scoreA = countNonNullAreas(a);
        int scoreB = countNonNullAreas(b);
        if (scoreB > scoreA) {
            return b;
        }
        if (scoreA > scoreB) {
            return a;
        }
        return b;
    }

    private static int countNonNullAreas(RoomInfo room) {
        int count = 0;
        if (room.getBuildingArea() != null) {
            count++;
        }
        if (room.getInnerArea() != null) {
            count++;
        }
        if (room.getBalconyArea() != null) {
            count++;
        }
        if (room.getSharedArea() != null) {
            count++;
        }
        return count;
    }

    public static List<ParsedDataItem> selectBestTotals(List<ParsedDataItem> totalItems, List<RoomInfo> roomInfos) {
        if (totalItems == null || totalItems.isEmpty()) {
            return List.of();
        }
        Map<String, List<ParsedDataItem>> grouped = new HashMap<>();
        for (ParsedDataItem item : totalItems) {
            if (item == null || !StringUtils.hasText(item.getNormalizedKey())) {
                continue;
            }
            grouped.computeIfAbsent(item.getNormalizedKey(), k -> new ArrayList<>()).add(item);
        }
        List<ParsedDataItem> selected = new ArrayList<>();
        for (Map.Entry<String, List<ParsedDataItem>> entry : grouped.entrySet()) {
            String key = entry.getKey();
            List<ParsedDataItem> candidates = entry.getValue();
            ParsedDataItem best = pickBestTotal(key, candidates, roomInfos);
            if (best != null) {
                selected.add(best);
                if (candidates.size() > 1) {
                    SurveyParseTelemetry.ocrTotalSelected(null, key, candidates.size(),
                            best.getValueNumber());
                }
            }
        }
        return selected;
    }

    private static ParsedDataItem pickBestTotal(String normalizedKey, List<ParsedDataItem> candidates,
            List<RoomInfo> roomInfos) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        BigDecimal rowSum = sumRoomDimension(normalizedKey, roomInfos);
        if (rowSum != null) {
            return candidates.stream()
                    .min(Comparator.comparing(c -> distance(c.getValueNumber(), rowSum)))
                    .orElse(candidates.get(0));
        }
        return candidates.stream()
                .max(Comparator.comparing(c -> c.getValueNumber() != null ? c.getValueNumber() : BigDecimal.ZERO))
                .orElse(candidates.get(0));
    }

    private static BigDecimal distance(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return new BigDecimal("999999");
        }
        return a.subtract(b).abs();
    }

    private static BigDecimal sumRoomDimension(String normalizedKey, List<RoomInfo> roomInfos) {
        Function<RoomInfo, BigDecimal> getter = DIMENSION_GETTERS.get(normalizedKey);
        if (getter == null || roomInfos == null || roomInfos.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        boolean any = false;
        for (RoomInfo room : roomInfos) {
            BigDecimal val = getter.apply(room);
            if (val != null) {
                sum = sum.add(val);
                any = true;
            }
        }
        return any ? sum : null;
    }

    public static boolean buildingTotalMismatch(List<RoomInfo> rooms, BigDecimal ocrBuildingTotal) {
        if (ocrBuildingTotal == null || rooms == null || rooms.isEmpty()) {
            return false;
        }
        BigDecimal sum = sumRoomDimension("room_info_building_area_sum_from_ocr", rooms);
        if (sum == null) {
            return false;
        }
        return sum.subtract(ocrBuildingTotal).abs().compareTo(TOTAL_TOLERANCE) > 0;
    }
}
