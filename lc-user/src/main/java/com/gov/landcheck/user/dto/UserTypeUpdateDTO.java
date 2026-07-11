package com.gov.landcheck.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 仅更新用户类型（供超级管理员调整权限，无需提交密码等完整 {@link UserDTO}）。
 */
@Data
@Schema(description = "更新用户类型请求")
public class UserTypeUpdateDTO {

    @NotBlank(message = "用户类型不能为空")
    @Schema(description = "SUPER_ADMIN / DEVELOPER / USER", example = "DEVELOPER")
    private String userType;
}
