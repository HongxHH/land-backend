package com.gov.landcheck.core.bo.entity;

import com.gov.landcheck.core.config.mongo.MongoIdEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * 系统用户实体类
 *
 * @author system
 * @date 2025/12/19
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "sys_user")
@Schema(description = "系统用户")
public class SysUser extends MongoIdEntity {

    @Field(name = "username")
    @Schema(description = "用户名（唯一）", example = "admin")
    private String username;

    @Field(name = "password")
    @Schema(description = "密码（BCrypt 存储；历史数据可能为 MD5 摘要，登录成功后会自动升级为 BCrypt）")
    private String password;

    @Field(name = "real_name")
    @Schema(description = "真实姓名", example = "张三")
    private String realName;

    @Field(name = "phone")
    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @Field(name = "email")
    @Schema(description = "邮箱", example = "user@example.com")
    private String email;

    @Field(name = "role_id")
    @Schema(description = "角色ID（后续扩展 RBAC 用）")
    private Long roleId;

    @Field(name = "dept_id")
    @Schema(description = "部门ID（可选）")
    private Long deptId;

    @Field(name = "user_type")
    @Schema(description = "用户类型：SUPER_ADMIN=超级管理员，DEVELOPER=开发人员，USER=普通用户", example = "USER")
    private String userType;

    @Field(name = "is_active")
    @Schema(description = "是否启用（0否 1是）", example = "1")
    private Integer isActive;

    @Field(name = "last_login")
    @Schema(description = "上次登录时间")
    private LocalDateTime lastLogin;

}
