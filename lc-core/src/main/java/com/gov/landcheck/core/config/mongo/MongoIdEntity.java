package com.gov.landcheck.core.config.mongo;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * MongoDB 实体基类，支持自增 ID
 */
@Data
public class MongoIdEntity implements Entity {

    /**
     * 自增 ID
     * 注意：需要在保存前调用 MongoIdGenerator.getNextId(collectionName) 生成 ID
     */
    @Id
    @Field(name = "_id")
    @Schema(description = "主键ID（自增）")
    private Long id;

    @Field(name = "create_time")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Field(name = "update_time")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Field(name = "is_deleted")
    @Schema(description = "逻辑删除（0正常 1删除）", example = "0")
    private Integer isDeleted;

    /**
     * 获取集合名称（用于生成自增ID）
     * 子类可以通过 @Document 注解指定，或重写此方法
     */
    @Transient
    public String getCollectionName() {
        // 尝试从 @Document 注解获取
        Document document = this.getClass().getAnnotation(Document.class);
        if (document != null && !document.collection().isEmpty()) {
            return document.collection();
        }
        // 默认使用类名转小写
        return this.getClass().getSimpleName().toLowerCase();
    }

    /**
     * 保存前的预处理，确保ID和必要字段已设置
     */
    public void preSave() {
        // 如果ID为空，则生成自增ID
        if (this.id == null) {
            this.id = MongoIdGenerator.getNextId(this.getCollectionName());
        }

        // 设置创建时间和更新时间
        LocalDateTime now = LocalDateTime.now();
        if (this.createTime == null) {
            this.createTime = now;
        }
        this.updateTime = now;

        // 设置逻辑删除标志
        if (this.isDeleted == null) {
            this.isDeleted = 0;
        }
    }
}
