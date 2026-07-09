package com.gov.landcheck.core.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.util.StringUtils;

import com.gov.landcheck.core.common.ValidationResult;

/**
 * 户室信息业务校验：楼层+房号唯一性、建筑面积分项平衡。
 */
public final class RoomInfoValidator {

    public static final BigDecimal AREA_TOLERANCE = new BigDecimal("0.01");

    private RoomInfoValidator() {
    }

    public static String normalizeKeyPart(String value) {
        return value != null ? value.trim() : "";
    }

    public static String buildIdentityKey(String roomLevel, String roomNumber) {
        return normalizeKeyPart(roomLevel) + "|" + normalizeKeyPart(roomNumber);
    }

    public static ValidationResult validateIdentity(String roomLevel, String roomNumber) {
        if (!StringUtils.hasText(normalizeKeyPart(roomLevel))) {
            return ValidationResult.failure("楼层不能为空");
        }
        if (!StringUtils.hasText(normalizeKeyPart(roomNumber))) {
            return ValidationResult.failure("房号不能为空");
        }
        return ValidationResult.success();
    }

    public static ValidationResult validateAreaBalance(BigDecimal buildingArea, BigDecimal innerArea,
            BigDecimal balconyArea, BigDecimal sharedArea) {
        BigDecimal building = nullToZero(buildingArea);
        BigDecimal inner = nullToZero(innerArea);
        BigDecimal balcony = nullToZero(balconyArea);
        BigDecimal shared = nullToZero(sharedArea);
        BigDecimal sum = inner.add(balcony).add(shared);
        BigDecimal diff = building.subtract(sum).abs();
        if (diff.compareTo(AREA_TOLERANCE) > 0) {
            return ValidationResult.failure(String.format(
                    "建筑面积(%s)必须等于套内面积(%s)+阳台面积(%s)+分摊面积(%s)=%s",
                    formatArea(building),
                    formatArea(inner),
                    formatArea(balcony),
                    formatArea(shared),
                    formatArea(sum)));
        }
        return ValidationResult.success();
    }

    public static ValidationResult validateRoom(String roomLevel, String roomNumber,
            BigDecimal buildingArea, BigDecimal innerArea, BigDecimal balconyArea, BigDecimal sharedArea) {
        ValidationResult identity = validateIdentity(roomLevel, roomNumber);
        if (!identity.isValid()) {
            return identity;
        }
        return validateAreaBalance(buildingArea, innerArea, balconyArea, sharedArea);
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static String formatArea(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
