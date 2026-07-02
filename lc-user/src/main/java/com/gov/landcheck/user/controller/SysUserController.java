package com.gov.landcheck.user.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.core.bo.entity.SysUser;
import com.gov.landcheck.core.common.UserTypeConstants;
import com.gov.landcheck.user.dto.UserAdminLookupVO;
import com.gov.landcheck.user.dto.UserDTO;
import com.gov.landcheck.user.dto.UserQueryDTO;
import com.gov.landcheck.user.dto.UserTypeUpdateDTO;
import com.gov.landcheck.user.dto.UserVO;
import com.gov.landcheck.user.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * 系统用户控制器
 *
 * @author system
 * @date 2026/01/22
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/user")
@SaCheckRole(value = { UserTypeConstants.SUPER_ADMIN, UserTypeConstants.ADMIN }, mode = SaMode.OR)
public class SysUserController {

    @Resource
    private SysUserService sysUserService;

    @Operation(summary = "创建用户")
    @PostMapping("/create")
    public AjaxJson createUser(@Valid @RequestBody UserDTO userDTO) {
        return sysUserService.createUser(userDTO);
    }

    @Operation(summary = "更新用户信息")
    @PutMapping("/update/{userId}")
    public AjaxJson updateUser(@Parameter(description = "用户ID") @PathVariable Long userId,
            @Valid @RequestBody UserDTO userDTO) {
        return sysUserService.updateUser(userId, userDTO);
    }

    @Operation(summary = "更新用户权限类型", description = "仅超级管理员、管理员可调用；管理员不可将用户设为超级管理员，也不可修改已是超级管理员的用户。")
    @PutMapping("/user-type/{userId}")
    public AjaxJson updateUserType(@Parameter(description = "用户ID") @PathVariable Long userId,
            @Valid @RequestBody UserTypeUpdateDTO body) {
        return sysUserService.updateUserType(userId, body.getUserType());
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/delete/{userId}")
    public AjaxJson deleteUser(@Parameter(description = "用户ID") @PathVariable Long userId) {
        return sysUserService.deleteUser(userId);
    }

    @Operation(summary = "根据ID获取用户信息")
    @GetMapping("/get/{userId}")
    public AjaxJson getUserById(@Parameter(description = "用户ID") @PathVariable Long userId) {
        Optional<SysUser> userOpt = sysUserService.getUserById(userId);
        if (userOpt.isPresent()) {
            SysUser user = userOpt.get();
            UserVO userVO = new UserVO();
            userVO.setId(user.getId());
            userVO.setUsername(user.getUsername());
            userVO.setRealName(user.getRealName());
            userVO.setPhone(user.getPhone());
            userVO.setEmail(user.getEmail());
            userVO.setRoleId(user.getRoleId());
            userVO.setDeptId(user.getDeptId());
            userVO.setUserType(user.getUserType());
            userVO.setIsActive(user.getIsActive());
            userVO.setCreateTime(user.getCreateTime());
            userVO.setUpdateTime(user.getUpdateTime());
            userVO.setLastLogin(user.getLastLogin());
            return AjaxJson.getSuccessData(userVO);
        } else {
            return AjaxJson.getError("用户不存在");
        }
    }

    @Operation(summary = "根据用户名获取用户信息", description = "仅管理员；返回脱敏后的简要信息")
    @GetMapping("/get-by-username/{username}")
    public AjaxJson getUserByUsername(@Parameter(description = "用户名") @PathVariable String username) {
        Optional<SysUser> userOpt = sysUserService.getUserByUsername(username);
        if (userOpt.isPresent()) {
            return AjaxJson.getSuccessData(UserAdminLookupVO.fromSysUser(userOpt.get()));
        } else {
            return AjaxJson.getError("用户不存在");
        }
    }

    @Operation(summary = "分页查询用户列表")
    @GetMapping("/list")
    public AjaxJson listUsers(@Parameter(description = "用户名关键词") @RequestParam(required = false) String username,
            @Parameter(description = "真实姓名关键词") @RequestParam(required = false) String realName,
            @Parameter(description = "手机号") @RequestParam(required = false) String phone,
            @Parameter(description = "邮箱") @RequestParam(required = false) String email,
            @Parameter(description = "用户类型") @RequestParam(required = false) String userType,
            @Parameter(description = "是否启用（0否 1是）") @RequestParam(required = false) Integer isActive,
            @Parameter(description = "部门ID") @RequestParam(required = false) Long deptId,
            @Parameter(description = "角色ID") @RequestParam(required = false) Long roleId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer pageSize) {

        UserQueryDTO queryDTO = new UserQueryDTO();
        queryDTO.setUsername(username);
        queryDTO.setRealName(realName);
        queryDTO.setPhone(phone);
        queryDTO.setEmail(email);
        queryDTO.setUserType(userType);
        queryDTO.setIsActive(isActive);
        queryDTO.setDeptId(deptId);
        queryDTO.setRoleId(roleId);

        return sysUserService.listUsersPage(queryDTO, pageNum, pageSize);
    }

    @Operation(summary = "启用用户")
    @PutMapping("/enable/{userId}")
    public AjaxJson enableUser(@Parameter(description = "用户ID") @PathVariable Long userId) {
        return sysUserService.toggleUserStatus(userId, 1);
    }

    @Operation(summary = "禁用用户")
    @PutMapping("/disable/{userId}")
    public AjaxJson disableUser(@Parameter(description = "用户ID") @PathVariable Long userId) {
        return sysUserService.toggleUserStatus(userId, 0);
    }

    @Operation(summary = "检查用户名是否存在")
    @GetMapping("/check-username/{username}")
    public AjaxJson checkUsernameExists(@Parameter(description = "用户名") @PathVariable String username) {
        boolean exists = sysUserService.existsByUsername(username);
        return AjaxJson.getSuccessData(exists);
    }

    @Operation(summary = "检查用户名是否存在（排除指定用户）")
    @GetMapping("/check-username/{username}/exclude/{userId}")
    public AjaxJson checkUsernameExistsExclude(@Parameter(description = "用户名") @PathVariable String username,
            @Parameter(description = "排除的用户ID") @PathVariable Long userId) {
        boolean exists = sysUserService.existsByUsername(username, userId);
        return AjaxJson.getSuccessData(exists);
    }

}