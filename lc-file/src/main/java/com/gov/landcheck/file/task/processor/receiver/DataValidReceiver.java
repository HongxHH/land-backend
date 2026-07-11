package com.gov.landcheck.file.task.processor.receiver;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.RoomInfo;
import com.gov.landcheck.core.bo.entity.SurveyReportInfo;
import com.gov.landcheck.core.common.ValidationResult;
import com.gov.landcheck.core.config.cache.event.ProjectDataChangedEvent;
import com.gov.landcheck.core.service.SurveyReportCalculationService;
import com.gov.landcheck.file.service.parse.SurveyReportFieldUpdates;

import lombok.extern.slf4j.Slf4j;

/**
 * 数据校验处理器实现：校验实测报告解析结果，并写回校验/计算字段。
 */
@Slf4j
@Service
public class DataValidReceiver {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private SurveyReportCalculationService calculationService;

    @Autowired(required = false)
    private ApplicationEventPublisher applicationEventPublisher;

    // @Transactional(rollbackFor = Exception.class, timeout = 300)
    public SurveyReportInfo validateSurveyData(SurveyReportInfo surveyReportInfo, FileRecord fileRecord)
            throws Exception {
        log.info("开始校验实测报告数据: fileId={}, surveyReportInfoId={}", fileRecord.getId(), surveyReportInfo.getId());

        List<RoomInfo> roomInfos = getRoomInfosByFileRecordId(fileRecord.getId());
        if (roomInfos == null || roomInfos.isEmpty()) {
            log.warn("文件 {} 未找到房间信息，跳过校验", fileRecord.getId());
            surveyReportInfo.setIsVerified(0);
            surveyReportInfo.setVerificationErrorReason("未找到房间信息，无法进行校验");
            mongoTemplate.save(surveyReportInfo);
            publish(ProjectDataChangedEvent.surveyReportChanged(surveyReportInfo.getProjectId(),
                    surveyReportInfo.getId(), false));
            return surveyReportInfo;
        }

        ValidationResult result = calculationService.calculateAndValidate(surveyReportInfo, roomInfos);

        surveyReportInfo.setIsVerified(result.isValid() ? 1 : 0);
        surveyReportInfo.setVerificationErrorReason(result.getErrorMessage());
        mongoTemplate.save(surveyReportInfo);

        try {
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.ORDERED, RoomInfo.class);
            LocalDateTime now = LocalDateTime.now();
            for (RoomInfo room : roomInfos) {
                if (room.getId() == null) {
                    continue;
                }
                Update update = new Update()
                        .set("is_calculate", room.getIsCalculate())
                        .set("usage_category", room.getUsageCategory())
                        .set("floor_area_type", room.getFloorAreaType())
                        .set("update_time", now);
                bulkOps.updateOne(Query.query(Criteria.where("_id").is(room.getId())), update);
            }
            bulkOps.execute();
        } catch (Exception ex) {
            revertValidationWrite(surveyReportInfo.getId());
            log.error(
                    "校验后批量更新房间字段失败（SurveyReportInfo 已写入校验结果，房间明细未全部更新）: fileRecordId={}, surveyReportInfoId={}, error={}",
                    fileRecord.getId(), surveyReportInfo.getId(), ex.getMessage(), ex);
            throw new IllegalStateException(
                    "房间明细批量更新失败，可能导致校验状态与房间字段不一致，请重试或人工核对。fileRecordId="
                            + fileRecord.getId(),
                    ex);
        }

        publish(ProjectDataChangedEvent.surveyReportChanged(surveyReportInfo.getProjectId(), surveyReportInfo.getId(),
                true));
        return surveyReportInfo;
    }

    private void revertValidationWrite(Long surveyReportInfoId) {
        if (surveyReportInfoId == null) {
            return;
        }
        Query query = new Query(Criteria.where("_id").is(surveyReportInfoId));
        Update update = SurveyReportFieldUpdates.appendComputedStatsReset(new Update())
                .set("update_time", LocalDateTime.now());
        mongoTemplate.updateFirst(query, update, SurveyReportInfo.class);
    }

    private List<RoomInfo> getRoomInfosByFileRecordId(Long fileRecordId) {
        Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));
        return mongoTemplate.find(query, RoomInfo.class);
    }

    private void publish(ProjectDataChangedEvent event) {
        if (applicationEventPublisher == null || event == null || !event.hasAnyId()) {
            return;
        }
        try {
            applicationEventPublisher.publishEvent(event);
        } catch (Exception ex) {
            log.warn("Publish ProjectDataChangedEvent failed, event={}", event, ex);
        }
    }
}