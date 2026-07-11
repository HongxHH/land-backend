package com.gov.landcheck.core.bo.entity;

import java.time.LocalDateTime;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.gov.landcheck.core.config.mongo.MongoIdEntity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户站内消息已读记录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "user_station_message_read")
@Schema(description = "用户站内消息已读记录")
public class UserStationMessageRead extends MongoIdEntity {

    @Field(name = "user_id")
    @Schema(description = "用户ID")
    private String userId;

    @Field(name = "message_id")
    @Schema(description = "站内消息ID")
    private Long messageId;

    @Field(name = "read_at")
    @Schema(description = "已读时间")
    private LocalDateTime readAt;
}

