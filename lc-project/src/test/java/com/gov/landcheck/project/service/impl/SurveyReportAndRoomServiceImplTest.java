package com.gov.landcheck.project.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.core.bo.entity.RoomInfo;
import com.gov.landcheck.core.config.cache.model.CacheLoadOptionsFactory;
import com.gov.landcheck.core.config.cache.service.CacheInvalidationService;
import com.gov.landcheck.core.config.cache.service.CacheOpsService;
import com.gov.landcheck.core.service.SurveyReportCalculationService;
import com.gov.landcheck.project.cache.ProjectCacheKeys;
import com.gov.landcheck.project.dto.RoomInfoUpdateDTO;

@ExtendWith(MockitoExtension.class)
class SurveyReportAndRoomServiceImplTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private CacheOpsService cacheOpsService;

    @Mock
    private CacheInvalidationService cacheInvalidationService;

    @Mock
    private CacheLoadOptionsFactory cacheLoadOptionsFactory;

    @Mock
    private ProjectCacheKeys projectCacheKeys;

    @Mock
    private SurveyReportCalculationService calculationService;

    @InjectMocks
    private SurveyReportAndRoomServiceImpl service;

    @Test
    void updateRoomInfoRejectsAreaOnlyUpdateWhenExistingIdentityIsIncomplete() {
        RoomInfo existingRoom = room(1L, 10L, "", "101");
        when(mongoTemplate.findOne(any(Query.class), eq(RoomInfo.class))).thenReturn(existingRoom);

        RoomInfoUpdateDTO updateDTO = new RoomInfoUpdateDTO();
        updateDTO.setId(1L);
        updateDTO.setBuildingArea(new BigDecimal("123.45"));

        AjaxJson result = service.updateRoomInfo(updateDTO);

        assertThat(result.getCode()).isEqualTo(AjaxJson.CODE_ERROR);
        assertThat(result.getMsg()).isEqualTo("楼层不能为空");
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(RoomInfo.class));
    }

    @Test
    void updateRoomInfoRejectsDuplicateIdentityAfterNormalization() {
        RoomInfo existingRoom = room(1L, 10L, " 1 ", "101");
        RoomInfo duplicateRoom = room(2L, 10L, "1", "102");
        when(mongoTemplate.findOne(any(Query.class), eq(RoomInfo.class))).thenReturn(existingRoom);
        when(mongoTemplate.find(any(Query.class), eq(RoomInfo.class))).thenReturn(List.of(duplicateRoom));

        RoomInfoUpdateDTO updateDTO = new RoomInfoUpdateDTO();
        updateDTO.setId(1L);
        updateDTO.setRoomNumber(" 102 ");

        AjaxJson result = service.updateRoomInfo(updateDTO);

        assertThat(result.getCode()).isEqualTo(AjaxJson.CODE_ERROR);
        assertThat(result.getMsg()).isEqualTo("该实测报告下已存在楼层「1」房号「102」的户室");
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(RoomInfo.class));
    }

    private static RoomInfo room(Long id, Long surveyReportInfoId, String roomLevel, String roomNumber) {
        RoomInfo roomInfo = new RoomInfo();
        roomInfo.setId(id);
        roomInfo.setSurveyReportInfoId(surveyReportInfoId);
        roomInfo.setRoomLevel(roomLevel);
        roomInfo.setRoomNumber(roomNumber);
        return roomInfo;
    }
}
