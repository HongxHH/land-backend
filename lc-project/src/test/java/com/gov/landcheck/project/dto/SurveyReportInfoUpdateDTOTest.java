package com.gov.landcheck.project.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class SurveyReportInfoUpdateDTOTest {

    @Test
    void manualUpdatePayloadDoesNotExposeSystemVerificationFields() {
        Set<String> fields = Arrays.stream(SurveyReportInfoUpdateDTO.class.getDeclaredFields())
                .map(field -> field.getName())
                .collect(Collectors.toSet());

        assertFalse(fields.contains("isVerified"));
        assertFalse(fields.contains("verificationErrorReason"));
    }
}
