package com.gov.landcheck.core.util;

import org.springframework.util.StringUtils;

import com.gov.landcheck.core.common.ValidationResult;

/**
 * 户室信息业务校验：楼层+房号唯一性。
 */
public final class RoomInfoValidator {

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
}
