package com.gov.landcheck.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 当前登录用户修改个人信息（不含用户名、权限类型等管理字段）。
 */
@Data
@Schema(description = "当前用户修改个人信息")
public class ProfileUpdateRequest {

    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 50, message = "真实姓名不能超过50个字符")
    @Schema(description = "真实姓名", example = "张三")
    private String realName;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号（可选）")
    private String phone;

    @Pattern(regexp = "^$|^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", message = "邮箱格式不正确")
    @Schema(description = "邮箱（可选）")
    private String email;

    @Schema(description = "原密码（修改密码时必填）")
    private String oldPassword;

    @Schema(description = "新密码（留空表示不修改）")
    private String newPassword;
}
