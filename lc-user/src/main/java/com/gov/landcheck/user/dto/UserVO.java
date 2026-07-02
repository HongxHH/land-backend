package com.gov.landcheck.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户VO - 用于返回用户信息
 *
 * @author system
 * @date 2026/01/22
 */
@Data
@Schema(description = "用户信息响应")
public class UserVO {

    @Schema(description = "用户ID", example = "1")
    private Long id;

    @Schema(description = "用户名（唯一）", example = "admin")
    private String username;

    @Schema(description = "真实姓名", example = "张三")
    private String realName;

    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @Schema(description = "邮箱", example = "user@example.com")
    private String email;

    @Schema(description = "角色ID（后续扩展RBAC用）", example = "1")
    private Long roleId;

    @Schema(description = "部门ID（可选）", example = "1")
    private Long deptId;

    @Schema(description = "用户类型：SUPER_ADMIN/ADMIN/DEVELOPER/USER（或历史 DEPT_USER）", example = "USER")
    private String userType;

    @Schema(description = "是否启用（0否 1是）", example = "1")
    private Integer isActive;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "上次登录时间")
    private LocalDateTime lastLogin;

}