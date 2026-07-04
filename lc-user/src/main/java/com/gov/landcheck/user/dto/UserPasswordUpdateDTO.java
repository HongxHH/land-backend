package com.gov.landcheck.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 超级管理员重置用户密码（无需提交完整 {@link UserDTO}）。
 */
@Data
@Schema(description = "超级管理员重置用户密码请求")
public class UserPasswordUpdateDTO {

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度必须在6-20个字符之间")
    @Schema(description = "新密码", example = "123456")
    private String password;
}
