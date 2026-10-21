package com.gov.landcheck.file.task.context;

import java.util.List;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParseFillSnapshot;
import com.gov.landcheck.core.bo.entity.ParsedDataHeader;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.file.dto.OCRPageResult;
import com.gov.landcheck.file.dto.ParseResult;
import com.gov.landcheck.file.task.base.TaskData;
import com.gov.landcheck.file.task.plan.ParsePipelinePlan.StageDef;

/**
 * 一种文件内容类型在解析管道中的入口：阶段、解析、产物守卫、回填、校验。
 */
public interface FileParseModule {

    FileContextType type();

    List<StageDef> stages();

    ParseResult parse(List<OCRPageResult> ocrResults, FileRecord fileRecord, ParsedDataHeader header) throws Exception;

    void assertCriticalOutput(ParseResult result);

    void fill(TaskData taskData) throws Exception;

    default boolean skipFill(TaskData taskData) {
        return false;
    }

    default void validate(TaskData taskData) throws Exception {
        throw new UnsupportedOperationException("该类型不支持校验: " + type());
    }

    default void captureFillSnapshot(ParseFillSnapshot snapshot, Long fileRecordId) {
    }

    default void hydrateTaskData(TaskData taskData, ParseFillSnapshot snapshot) {
    }

    default void restoreFillSnapshot(ParseFillSnapshot snapshot) {
    }

    default void revertFill(FileRecord fileRecord, TaskData taskData) {
    }

    default void deleteBusinessData(FileRecord fileRecord) {
    }

    default void publishFillRestored(FileRecord fileRecord, TaskData taskData) {
    }

    /**
     * 上传后幂等创建业务占位。已存在时返回 null。
     */
    default PlaceholderCreated createPlaceholder(FileRecord fileRecord, Integer phase) {
        return null;
    }

    /**
     * 解析成功落库，调用方处于写库事务内。
     */
    default void onParseSucceeded(TaskData taskData) {
    }

    /**
     * 解析失败后修正业务表，避免界面仍显示未解析。
     */
    default void onParseFailed(FileRecord fileRecord, String errorMessage) {
    }
}
