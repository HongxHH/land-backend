package com.gov.landcheck.file.vo;

import java.time.LocalDateTime;

import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.enums.FileStateEnum;
import com.gov.landcheck.core.enums.FileType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文件信息视图对象
 * 用于通用文件查询接口返回，在 FileRecord 基础上增加按类型扩展的信息（如实测报告校验状态）。
 *
 * @author system
 */
@Data
@Schema(description = "文件信息视图对象")
public class FileRecordVO {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "逻辑删除（0正常 1删除）")
    private Integer isDeleted;

    @Schema(description = "所属项目ID")
    private Long projectId;

    @Schema(description = "首页预览图的GridFS的ID")
    private String thumbGridfsId;

    @Schema(description = "关联 parse_job.id")
    private Long parseJobId;

    @Schema(description = "文件类型（PDF/JPG/XLS等）")
    private FileType fileType;

    @Schema(description = "文件内容类型（CONTRACT/SURVEY_REPORT/OTHER）")
    private FileContextType fileContextType;

    @Schema(description = "所属归档夹ID")
    private Long archiveId;

    @Schema(description = "原始文件名")
    private String originalName;

    @Schema(description = "大文件管理GridFS的ID")
    private String gridfsId;

    @Schema(description = "文件md5值")
    private String md5;

    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    @Schema(description = "上传人ID")
    private Long uploadUserId;

    @Schema(description = "上传人名称")
    private String uploadUserName;

    @Schema(description = "上传时间")
    private LocalDateTime uploadTime;

    @Schema(description = "文件状态")
    private FileStateEnum fileState;

    @Schema(description = "解析失败原因或提示")
    private String parseMessage;

    @Schema(description = "期数")
    private Integer phase;

    @Schema(description = "文件预处理得到的新文件gridfsId")
    private String preprocessGridfsId;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "是否通过校验（0否 1是），仅当 fileContextType 为 SURVEY_REPORT 时有值", example = "1")
    private Integer isVerified;

    @Schema(description = "校验出错原因，仅当 fileContextType 为 SURVEY_REPORT 时有值", example = "建筑面积总和不一致")
    private String verificationErrorReason;
}
