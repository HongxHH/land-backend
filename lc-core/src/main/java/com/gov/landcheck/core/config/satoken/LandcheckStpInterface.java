package com.gov.landcheck.core.config.satoken;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.gov.landcheck.core.common.UserTypeConstants;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 权限与角色加载：权限码后续可接 RBAC；当前将 userType 暴露为角色便于注解鉴权。
 */
@Component
public class LandcheckStpInterface implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        SaSession session = StpUtil.getSessionByLoginId(loginId, false);
        if (session == null) {
            return Collections.emptyList();
        }
        String userType = (String) session.get("userType");
        if (userType == null || userType.isBlank()) {
            return Collections.emptyList();
        }
        if (UserTypeConstants.LEGACY_DEPT_USER.equals(userType)) {
            userType = UserTypeConstants.USER;
        }
        return List.of(userType);
    }
}
