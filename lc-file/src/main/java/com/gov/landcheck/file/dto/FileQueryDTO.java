package com.gov.landcheck.file.dto;

import java.time.LocalDateTime;

import com.gov.landcheck.core.bo.dto.BaseQueryDTO;
import com.gov.landcheck.core.config.query.QueryField;
import com.gov.landcheck.core.config.query.QueryType;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.enums.FileStateEnum;
import com.gov.landcheck.core.enums.FileType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件通用查询DTO，配合 {@link com.gov.landcheck.core.config.query.MongoQueryBuilder}
 * 动态构建查询条件。
 * 时间范围与未归档条件在 Service 层合并。
 *
 * @author system
 * @date 2026/01/24
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "文件通用查询请求")
public class FileQueryDTO extends BaseQueryDTO {

    @QueryField("project_id")
    @Schema(description = "项目ID")
    private Long projectId;

    @QueryField("_id")
    @Schema(description = "文件ID")
    private String fileId;

    @QueryField("file_type")
    @Schema(description = "文件类型（PDF/JPG/XLS等）")
    private FileType fileType;

    @QueryField("file_context_type")
    @Schema(description = "文件内容类型（CONTRACT/SURVEY_REPORT/OTHER）")
    private FileContextType fileContextType;

    @QueryField(value = "original_name", type = QueryType.REGEX)
    @Schema(description = "原始文件名（支持模糊查询）")
    private String originalName;

    @Schema(description = "上传时间开始范围（动态构建不支持，在 Service 层合并）")
    private LocalDateTime uploadTimeStart;

    @Schema(description = "上传时间结束范围（动态构建不支持，在 Service 层合并）")
    private LocalDateTime uploadTimeEnd;

    @QueryField("file_state")
    @Schema(description = "文件状态")
    private FileStateEnum fileState;

    @QueryField("archive_id")
    @Schema(description = "归档夹ID，可选；传则只查该归档下文件")
    private Long archiveId;

    @Schema(description = "为 true 时仅查询未归档文件（archive_id 为空）")
    private Boolean unarchivedOnly;

    @Schema(description = "校验状态筛选：PASSED-已通过，FAILED-未通过，UNVERIFIED-未校验（Service 层与 SurveyReportInfo 关联）")
    private String verifyStatus;

}