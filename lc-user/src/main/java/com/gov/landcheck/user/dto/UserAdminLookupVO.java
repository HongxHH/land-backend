package com.gov.landcheck.user.dto;

import com.gov.landcheck.core.bo.entity.SysUser;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 管理端按用户名查询时的最小视图：敏感联系方式脱敏。
 */
@Data
@Schema(description = "管理端用户简要信息（脱敏）")
public class UserAdminLookupVO {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "手机号（脱敏）")
    private String phoneMasked;

    @Schema(description = "邮箱（脱敏）")
    private String emailMasked;

    @Schema(description = "用户类型")
    private String userType;

    @Schema(description = "是否启用（0否 1是）")
    private Integer isActive;

    @Schema(description = "部门ID")
    private Long deptId;

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "上次登录时间")
    private LocalDateTime lastLogin;

    public static UserAdminLookupVO fromSysUser(SysUser user) {
        UserAdminLookupVO vo = new UserAdminLookupVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setPhoneMasked(maskPhone(user.getPhone()));
        vo.setEmailMasked(maskEmail(user.getEmail()));
        vo.setUserType(user.getUserType());
        vo.setIsActive(user.getIsActive());
        vo.setDeptId(user.getDeptId());
        vo.setRoleId(user.getRoleId());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        vo.setLastLogin(user.getLastLogin());
        return vo;
    }

    private static String maskPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return phone;
        }
        String p = phone.trim();
        if (p.length() < 7) {
            return "***";
        }
        return p.substring(0, 3) + "****" + p.substring(p.length() - 4);
    }

    private static String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return email;
        }
        String e = email.trim();
        int at = e.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = e.substring(0, at);
        String domain = e.substring(at);
        if (local.length() <= 1) {
            return "*" + domain;
        }
        return local.charAt(0) + "***" + domain;
    }
}
