package com.gov.landcheck.core.bo.entity;

import java.time.LocalDateTime;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.gov.landcheck.core.config.mongo.MongoIdEntity;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.enums.FileStateEnum;
import com.gov.landcheck.core.enums.FileType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 上传文件元数据实体类
 *
 * @author system
 * @date 2025/12/19
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "file_record")
@Schema(description = "上传文件元数据")
public class FileRecord extends MongoIdEntity {

    @Field(name = "project_id")
    @Schema(description = "所属项目ID")
    private Long projectId;

    @Field("thumb_gridfs_id")
    @Schema(description = "首页预览图的GridFS的ID")
    private String thumbGridfsId;

    @Field(name = "parse_job_id")
    @Schema(description = "关联 parse_job.id")
    private Long parseJobId;

    @Field(name = "file_type")
    @Schema(description = "文件类型（引用 FileType 枚举，表示文件格式：PDF/JPG/XLS等）", example = "PDF")
    private FileType fileType;

    @Field(name = "file_context_type")
    @Schema(description = "文件内容类型（引用 FileContextType 枚举，表示内容类型：CONTRACT/SURVEY_REPORT/PLANNING_REVIEW等）", example = "CONTRACT")
    private FileContextType fileContextType;

    @Field(name = "archive_id")
    @Indexed
    @Schema(description = "所属归档夹ID（file_archive.id），为空表示未归档/老数据")
    private Long archiveId;

    @Field(name = "original_name")
    @Schema(description = "原始文件名", example = "土地出让合同.pdf")
    private String originalName;

    @Field(name = "gridfs_id")
    @Schema(description = "大文件管理GridFS的ID")
    private String gridfsId;

    @Field(name = "md5")
    @Schema(description = "文件md5值", example = "d41d8cd98f00b204e9800998ecf8427e")
    private String md5;

    @Field(name = "file_size")
    @Schema(description = "文件大小（字节）", example = "1048576")
    private Long fileSize;

    @Field(name = "upload_user_id")
    @Schema(description = "上传人（sys_user.id）")
    private Long uploadUserId;

    @Field(name = "upload_user_name")
    @Schema(description = "上传人名称")
    private String uploadUserName;

    @Field(name = "upload_time")
    @Schema(description = "上传时间")
    private LocalDateTime uploadTime;

    @Field(name = "file_state")
    @Schema(description = "文件状态")
    private FileStateEnum fileState;

    @Field(name = "parse_message")
    @Schema(description = "解析失败原因或提示")
    private String parseMessage;

    @Field(name = "phase")
    @Schema(description = "期数")
    private Integer phase;

    @Field(name = "preprocess_gridfs_id")
    @Schema(description = "文件预处理得到的新文件grifsid")
    private String preprocessGridfsId;

    @Schema(description = "文件二进制内容（用于下载）")
    private transient byte[] fileContent;

    @Field(name = "remark")
    @Schema(description = "备注")
    private String remark;

    public byte[] getFileContent() {
        return fileContent;
    }

    public void setFileContent(byte[] fileContent) {
        this.fileContent = fileContent;
    }
}
