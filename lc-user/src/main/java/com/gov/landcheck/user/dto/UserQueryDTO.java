package com.gov.landcheck.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户查询DTO
 *
 * @author system
 * @date 2026/01/22
 */
@Data
@Schema(description = "用户查询条件")
public class UserQueryDTO {

    @Schema(description = "用户名关键词", example = "admin")
    private String username;

    @Schema(description = "真实姓名关键词", example = "张三")
    private String realName;

    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @Schema(description = "邮箱", example = "user@example.com")
    private String email;

    @Schema(description = "用户类型（ADMIN/DEPT_USER/DEVELOPER）", example = "ADMIN")
    private String userType;

    @Schema(description = "是否启用（0否 1是）", example = "1")
    private Integer isActive;

    @Schema(description = "部门ID", example = "1")
    private Long deptId;

    @Schema(description = "角色ID", example = "1")
    private Long roleId;

}