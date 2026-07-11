package com.gov.landcheck.core.bo.entity;

import java.time.LocalDateTime;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.gov.landcheck.core.config.mongo.MongoIdEntity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 站内广播消息持久化实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "station_message")
@Schema(description = "站内广播消息")
public class StationMessage extends MongoIdEntity {

    @Field(name = "scene")
    @Schema(description = "场景编码")
    private String scene;

    @Field(name = "title")
    @Schema(description = "标题")
    private String title;

    @Field(name = "content")
    @Schema(description = "内容")
    private String content;

    @Field(name = "business_id")
    @Schema(description = "业务ID（如 taskId）")
    private String businessId;

    @Field(name = "project_id")
    @Schema(description = "项目ID")
    private Long projectId;

    @Field(name = "file_id")
    @Schema(description = "文件ID")
    private Long fileId;

    @Field(name = "topic_key")
    @Schema(description = "广播topic键")
    private String topicKey;

    @Field(name = "sent_at")
    @Schema(description = "广播发送时间")
    private LocalDateTime sentAt;
}

