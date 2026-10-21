package com.gov.landcheck.file.task.context;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import com.gov.landcheck.core.audit.TargetType;
import com.gov.landcheck.core.bo.entity.ContractInfo;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.LandParcel;
import com.gov.landcheck.core.bo.entity.ParseFillSnapshot;
import com.gov.landcheck.core.bo.entity.ParsedDataHeader;
import com.gov.landcheck.core.bo.entity.ParsedDataItem;
import com.gov.landcheck.core.config.cache.event.ProjectDataChangedEvent;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.service.SurveyReportContractApprovalSyncService;
import com.gov.landcheck.file.dto.OCRPageResult;
import com.gov.landcheck.file.dto.ParseResult;
import com.gov.landcheck.file.service.parse.ParseArtifactCleanupService;
import com.gov.landcheck.file.task.base.TaskData;
import com.gov.landcheck.file.task.plan.ParsePipelinePlan;
import com.gov.landcheck.file.task.plan.ParsePipelinePlan.StageDef;
import com.gov.landcheck.file.task.processor.receiver.parse.ContractDataParser;
import com.gov.landcheck.file.task.processor.receiver.parse.ParseUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ContractParseModule implements FileParseModule {

    private final ContractDataParser contractDataParser;
    private final MongoTemplate mongoTemplate;
    private final FillChangePublisher fillChangePublisher;
    private final SurveyReportContractApprovalSyncService surveyReportContractApprovalSyncService;

    public ContractParseModule(
            ContractDataParser contractDataParser,
            MongoTemplate mongoTemplate,
            FillChangePublisher fillChangePublisher,
            SurveyReportContractApprovalSyncService surveyReportContractApprovalSyncService) {
        this.contractDataParser = contractDataParser;
        this.mongoTemplate = mongoTemplate;
        this.fillChangePublisher = fillChangePublisher;
        this.surveyReportContractApprovalSyncService = surveyReportContractApprovalSyncService;
    }

    @Override
    public FileContextType type() {
        return FileContextType.CONTRACT;
    }

    @Override
    public List<StageDef> stages() {
        return ParsePipelinePlan.defaultStages();
    }

    @Override
    public ParseResult parse(List<OCRPageResult> ocrResults, FileRecord fileRecord, ParsedDataHeader header)
            throws Exception {
        long startTime = System.currentTimeMillis();
        String contractText = ParseUtils.combineFirst20PagesText(ocrResults);
        List<ParsedDataItem> dataItems = contractDataParser.parseContractData(contractText, header);
        long processingTime = System.currentTimeMillis() - startTime;
        ParseResult result = new ParseResult(dataItems, new ArrayList<>());
        result.setProcessingTimeMs(processingTime);
        result.setParseEngine("LLM");
        log.info("数据解析完成: fileId={}, items={}, roomInfos={}, processingTime={}ms",
                fileRecord.getId(), dataItems.size(), 0, processingTime);
        return result;
    }

    @Override
    public void assertCriticalOutput(ParseResult result) {
        if (result == null) {
            return;
        }
        if (result.getDataItems() == null || result.getDataItems().isEmpty()) {
            throw new IllegalStateException("合同解析结果为空，判定为不可恢复错误");
        }
    }

    @Override
    public void fill(TaskData taskData) throws Exception {
        log.info("开始【合同】数据回填: fileId={}", taskData.getFileRecord().getId());

        Query query = new Query(Criteria.where("file_record_id").is(taskData.getFileRecord().getId()));
        ContractInfo existingContractInfo = mongoTemplate.findOne(query, ContractInfo.class);

        taskData.setPreFillContractExisted(existingContractInfo != null);
        if (existingContractInfo != null && taskData.getPreFillContractSnapshot() == null) {
            taskData.setPreFillContractSnapshot(ParseArtifactCleanupService.copyContractSnapshot(existingContractInfo));
        }

        ContractInfo contractInfo;
        boolean isUpdate;
        if (existingContractInfo != null) {
            contractInfo = existingContractInfo;
            isUpdate = true;
        } else {
            contractInfo = new ContractInfo();
            contractInfo.setProjectId(taskData.getFileRecord().getProjectId());
            contractInfo.setFileRecordId(taskData.getFileRecord().getId());
            isUpdate = false;
            taskData.setFillCreatedNewContract(true);
        }

        List<ParsedDataItem> items = taskData.getParsedDataItems();
        if (items != null) {
            for (ParsedDataItem item : items) {
                String normalizedKey = item.getNormalizedKey();
                if (normalizedKey == null) {
                    continue;
                }
                switch (normalizedKey) {
                    case "contract_number" -> contractInfo.setContractNumber(item.getNormalizedValue());
                    case "transferor" -> contractInfo.setTransferor(item.getNormalizedValue());
                    case "transferee" -> contractInfo.setTransferee(item.getNormalizedValue());
                    case "total_area" -> {
                        if (item.getNormalizedNumber() != null) {
                            contractInfo.setTotalArea(item.getNormalizedNumber());
                        }
                    }
                }
            }
        }

        if (isUpdate) {
            contractInfo.setUpdateTime(LocalDateTime.now());
            mongoTemplate.save(contractInfo);
        } else {
            contractInfo.preSave();
            mongoTemplate.save(contractInfo);
        }

        taskData.setContractInfo(contractInfo);

        syncSurveyContractApproval(contractInfo.getProjectId());

        fillChangePublisher.publish(
                ProjectDataChangedEvent.contractChanged(contractInfo.getProjectId(), contractInfo.getId()));
    }

    private void syncSurveyContractApproval(Long projectId) {
        if (projectId == null) {
            return;
        }
        fillChangePublisher.runAfterCommit(() -> {
            try {
                surveyReportContractApprovalSyncService.syncAllSurveyReportsInProject(projectId);
            } catch (Exception ex) {
                log.warn("同步实测报告合同/批文编号失败 projectId={}", projectId, ex);
            }
        });
    }

    @Override
    public void captureFillSnapshot(ParseFillSnapshot snapshot, Long fileRecordId) {
        Query byFile = new Query(Criteria.where("file_record_id").is(fileRecordId));
        ContractInfo existing = mongoTemplate.findOne(byFile, ContractInfo.class);
        snapshot.setHadExisting(existing != null);
        snapshot.setContractSnapshot(ParseArtifactCleanupService.copyContractSnapshot(existing));
    }

    @Override
    public void hydrateTaskData(TaskData taskData, ParseFillSnapshot snapshot) {
        boolean hadExisting = Boolean.TRUE.equals(snapshot.getHadExisting());
        taskData.setPreFillContractExisted(hadExisting);
        taskData.setPreFillContractSnapshot(
                ParseArtifactCleanupService.copyContractSnapshot(snapshot.getContractSnapshot()));
        taskData.setFillCreatedNewContract(!hadExisting);
    }

    @Override
    public void restoreFillSnapshot(ParseFillSnapshot snapshot) {
        Long fileRecordId = snapshot.getFileRecordId();
        if (fileRecordId == null) {
            return;
        }
        boolean hadExisting = Boolean.TRUE.equals(snapshot.getHadExisting());
        Query byFile = new Query(Criteria.where("file_record_id").is(fileRecordId));
        if (hadExisting && snapshot.getContractSnapshot() != null) {
            ContractInfo copy = ParseArtifactCleanupService.copyContractSnapshot(snapshot.getContractSnapshot());
            copy.setUpdateTime(LocalDateTime.now());
            mongoTemplate.save(copy);
        } else if (!hadExisting) {
            mongoTemplate.remove(byFile, ContractInfo.class);
        }
    }

    @Override
    public void revertFill(FileRecord fileRecord, TaskData taskData) {
        Long fileRecordId = fileRecord.getId();
        Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));
        if (taskData != null && taskData.getPreFillContractSnapshot() != null) {
            ContractInfo snapshot = taskData.getPreFillContractSnapshot();
            snapshot.setUpdateTime(LocalDateTime.now());
            mongoTemplate.save(snapshot);
            if (snapshot.getProjectId() != null && snapshot.getId() != null) {
                fillChangePublisher.publishImmediately(
                        ProjectDataChangedEvent.contractChanged(snapshot.getProjectId(), snapshot.getId()));
            }
            return;
        }
        if (taskData != null && taskData.isFillCreatedNewContract()) {
            List<ContractInfo> removed = mongoTemplate.find(query, ContractInfo.class);
            mongoTemplate.remove(query, ContractInfo.class);
            for (ContractInfo contract : removed) {
                if (contract.getProjectId() != null && contract.getId() != null) {
                    fillChangePublisher.publishImmediately(
                            ProjectDataChangedEvent.contractChanged(contract.getProjectId(), contract.getId()));
                }
            }
            return;
        }
        log.warn("合同回填回滚跳过（无快照且非本次新建，避免离线误删）: fileRecordId={}", fileRecordId);
    }

    @Override
    public void deleteBusinessData(FileRecord fileRecord) {
        Long fileRecordId = fileRecord.getId();
        Query query = new Query(Criteria.where("file_record_id").is(fileRecordId));
        List<ContractInfo> contracts = mongoTemplate.find(query, ContractInfo.class);
        Set<Long> affectedProjectIds = new HashSet<>();
        if (contracts != null) {
            for (ContractInfo contract : contracts) {
                if (contract != null && contract.getProjectId() != null) {
                    affectedProjectIds.add(contract.getProjectId());
                }
            }
        }
        if (contracts != null && !contracts.isEmpty()) {
            List<Long> contractIds = contracts.stream()
                    .map(ContractInfo::getId)
                    .filter(id -> id != null)
                    .toList();
            if (!contractIds.isEmpty()) {
                Query parcelQuery = new Query(Criteria.where("contract_id").in(contractIds));
                mongoTemplate.remove(parcelQuery, LandParcel.class).getDeletedCount();
            }
        }
        if (contracts != null && !contracts.isEmpty()) {
            for (ContractInfo contract : contracts) {
                fillChangePublisher.publishImmediately(
                        ProjectDataChangedEvent.contractChanged(contract.getProjectId(), contract.getId()));
            }
        }
        long contractDeletedCount = mongoTemplate.remove(query, ContractInfo.class).getDeletedCount();
        log.info("删除合同信息: fileRecordId={}, deletedCount={}", fileRecordId, contractDeletedCount);
        for (Long projectId : affectedProjectIds) {
            try {
                surveyReportContractApprovalSyncService.syncAllSurveyReportsInProject(projectId);
            } catch (Exception ex) {
                log.warn("同步实测报告合同/批文编号失败 projectId={}", projectId, ex);
            }
        }
    }

    @Override
    public void publishFillRestored(FileRecord fileRecord, TaskData taskData) {
        ContractInfo snapshot = taskData != null ? taskData.getPreFillContractSnapshot() : null;
        if (snapshot != null && snapshot.getProjectId() != null && snapshot.getId() != null) {
            fillChangePublisher.publishImmediately(
                    ProjectDataChangedEvent.contractChanged(snapshot.getProjectId(), snapshot.getId()));
        } else if (fileRecord.getProjectId() != null) {
            fillChangePublisher.publishImmediately(
                    ProjectDataChangedEvent.contractChanged(fileRecord.getProjectId(), null));
        }
    }

    @Override
    public PlaceholderCreated createPlaceholder(FileRecord fileRecord, Integer phase) {
        Query query = new Query(Criteria.where("file_record_id").is(fileRecord.getId()));
        ContractInfo existing = mongoTemplate.findOne(query, ContractInfo.class);
        if (existing != null) {
            return null;
        }
        ContractInfo contractInfo = new ContractInfo();
        contractInfo.setProjectId(fileRecord.getProjectId());
        contractInfo.setFileRecordId(fileRecord.getId());
        contractInfo.preSave();
        mongoTemplate.save(contractInfo);
        return new PlaceholderCreated(
                TargetType.CONTRACT,
                contractInfo,
                ProjectDataChangedEvent.contractChanged(contractInfo.getProjectId(), contractInfo.getId()),
                true);
    }
}
