package com.gov.landcheck.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户DTO - 用于新增和更新用户信息
 *
 * @author system
 * @date 2026/01/22
 */
@Data
@Schema(description = "用户请求数据")
public class UserDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度必须在3-20个字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    @Schema(description = "用户名（唯一）", example = "admin")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度必须在6-20个字符之间")
    @Schema(description = "密码（前端需要加密传输）", example = "123456")
    private String password;

    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 50, message = "真实姓名不能超过50个字符")
    @Schema(description = "真实姓名", example = "张三")
    private String realName;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", message = "邮箱格式不正确")
    @Schema(description = "邮箱", example = "user@example.com")
    private String email;

    @Schema(description = "角色ID（后续扩展RBAC用）", example = "1")
    private Long roleId;

    @Schema(description = "部门ID（可选）", example = "1")
    private Long deptId;

    @NotBlank(message = "用户类型不能为空")
    @Schema(description = "用户类型：SUPER_ADMIN=超级管理员，ADMIN=管理员，DEVELOPER=开发人员，USER=普通用户", example = "USER")
    private String userType;

    @NotNull(message = "是否启用不能为空")
    @Schema(description = "是否启用（0否 1是）", example = "1")
    private Integer isActive;

}