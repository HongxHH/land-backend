package com.gov.landcheck.user.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.core.bo.entity.SysUser;
import com.gov.landcheck.core.common.MessageConstant;
import com.gov.landcheck.core.common.UserTypeConstants;
import com.gov.landcheck.user.dto.LoginRequest;
import com.gov.landcheck.user.dto.ProfileUpdateRequest;
import com.gov.landcheck.user.dto.RegisterRequest;
import com.gov.landcheck.user.dto.UserVO;
import com.gov.landcheck.user.service.SysUserService;

import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * 登录 / 登出（Sa-Token）；{@code /auth/login} 在配置中为匿名接口。
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Resource
    private SysUserService sysUserService;

    @Operation(summary = "自助注册", description = "仅创建普通用户（USER），无需登录")
    @PostMapping("/register")
    public AjaxJson register(@Valid @RequestBody RegisterRequest request) {
        return sysUserService.register(request);
    }

    @Operation(summary = "用户类型字典", description = "四类权限编码与中文名，供管理端下拉使用")
    @GetMapping("/user-types")
    public AjaxJson userTypes() {
        return AjaxJson.getSuccessData(UserTypeConstants.options());
    }

    @Operation(summary = "登录", description = "成功后在响应体 data 中返回 token、tokenName；后续请求头携带 satoken: token")
    @PostMapping("/login")
    public AjaxJson login(@Valid @RequestBody LoginRequest request) {
        Optional<SysUser> opt = sysUserService.getUserByUsername(request.getUsername().trim());
        if (opt.isEmpty()) {
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, MessageConstant.USER_NOT_EXIST);
        }
        SysUser user = opt.get();
        if (user.getIsActive() != null && user.getIsActive() == 0) {
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, MessageConstant.USER_HAS_BANNED);
        }
        if (!sysUserService.verifyPasswordForLogin(user, request.getPassword())) {
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, MessageConstant.USER_PASSWORD_ERROR);
        }

        StpUtil.login(user.getId());
        StpUtil.getSession().set("username", user.getUsername());
        String sessionUserType = user.getUserType();
        if (UserTypeConstants.LEGACY_DEPT_USER.equals(sessionUserType)) {
            sessionUserType = UserTypeConstants.USER;
        }
        StpUtil.getSession().set("userType", sessionUserType);

        sysUserService.updateLastLogin(user.getId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tokenName", StpUtil.getTokenName());
        data.put("token", StpUtil.getTokenValue());
        UserVO profile = new UserVO();
        profile.setId(user.getId());
        profile.setUsername(user.getUsername());
        profile.setRealName(user.getRealName());
        profile.setPhone(user.getPhone());
        profile.setEmail(user.getEmail());
        profile.setRoleId(user.getRoleId());
        profile.setDeptId(user.getDeptId());
        profile.setUserType(user.getUserType());
        profile.setIsActive(user.getIsActive());
        profile.setCreateTime(user.getCreateTime());
        profile.setUpdateTime(user.getUpdateTime());
        profile.setLastLogin(user.getLastLogin());
        data.put("user", profile);

        return AjaxJson.getSuccessData(data);
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public AjaxJson logout() {
        StpUtil.logout();
        return AjaxJson.getSuccess("已退出登录");
    }

    @Operation(summary = "当前登录用户")
    @GetMapping("/me")
    public AjaxJson me() {
        long loginId = StpUtil.getLoginIdAsLong();
        Optional<SysUser> opt = sysUserService.getUserById(loginId);
        if (opt.isEmpty()) {
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, MessageConstant.USER_NOT_EXIST);
        }
        return AjaxJson.getSuccessData(buildUserVO(opt.get()));
    }

    @Operation(summary = "修改当前用户个人信息", description = "可修改姓名、手机、邮箱；修改密码需提供原密码")
    @PutMapping("/profile")
    public AjaxJson updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        return sysUserService.updateProfile(request);
    }

    private static UserVO buildUserVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setRoleId(user.getRoleId());
        vo.setDeptId(user.getDeptId());
        vo.setUserType(user.getUserType());
        vo.setIsActive(user.getIsActive());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        vo.setLastLogin(user.getLastLogin());
        return vo;
    }
}
