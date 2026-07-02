package com.gov.landcheck.core.bo.entity;

import java.time.LocalDateTime;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.gov.landcheck.core.config.mongo.MongoIdEntity;
import com.gov.landcheck.core.enums.UploadStatusEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Author: HongHong
 * Date: 2025/12/20
 * Description:上传记录
 * :
 */

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "upload_record")
@Schema(description = "上传记录")
public class UploadRecord extends MongoIdEntity {
    @Field(name = "file_id")
    @Schema(description = "文件ID")
    private Long fileId;

    @Field(name = "file_name")
    @Schema(description = "文件名")
    private String fileName;

    @Field(name = "file_path")
    @Schema(description = "文件路径")
    private String filePath;

    @Field(name = "upload_user_id")
    @Schema(description = "上传人ID")
    private Long uploadUserId;

    @Field(name = "upload_user_name")
    @Schema(description = "上传人名称")
    private String uploadUserName;

    @Field(name = "upload_time")
    @Schema(description = "上传时间")
    private LocalDateTime uploadTime;

    @Field(name = "upload_status")
    @Schema(description = "上传状态")
    private UploadStatusEnum uploadStatus;

    @Field(name = "failure_reason")
    @Schema(description = "失败原因")
    private String failureReason;

}
