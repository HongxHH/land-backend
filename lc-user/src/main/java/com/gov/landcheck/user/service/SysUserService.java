package com.gov.landcheck.user.service;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.core.bo.entity.SysUser;
import com.gov.landcheck.user.dto.RegisterRequest;
import com.gov.landcheck.user.dto.UserDTO;
import com.gov.landcheck.user.dto.UserQueryDTO;
import com.gov.landcheck.user.dto.UserVO;

import java.util.List;
import java.util.Optional;

/**
 * 系统用户服务接口
 *
 * @author system
 * @date 2026/01/22
 */
public interface SysUserService {

    /**
     * 创建用户
     *
     * @param userDTO 用户信息
     * @return 创建结果
     */
    AjaxJson createUser(UserDTO userDTO);

    /**
     * 自助注册（固定为普通用户 {@link com.gov.landcheck.core.common.UserTypeConstants#USER}）。
     */
    AjaxJson register(RegisterRequest request);

    /**
     * 更新用户信息
     *
     * @param userId  用户ID
     * @param userDTO 用户信息
     * @return 更新结果
     */
    AjaxJson updateUser(Long userId, UserDTO userDTO);

    /**
     * 由超级管理员或管理员更新目标用户的 {@code user_type}（不修改密码及其他字段）。
     */
    AjaxJson updateUserType(Long userId, String newUserType);

    /**
     * 根据ID删除用户
     *
     * @param userId 用户ID
     * @return 删除结果
     */
    AjaxJson deleteUser(Long userId);

    /**
     * 根据ID获取用户信息
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    Optional<SysUser> getUserById(Long userId);

    /**
     * 根据用户名获取用户信息
     *
     * @param username 用户名
     * @return 用户信息
     */
    Optional<SysUser> getUserByUsername(String username);

    /**
     * 查询用户列表
     *
     * @param queryDTO 查询条件
     * @return 用户列表
     */
    List<SysUser> listUsers(UserQueryDTO queryDTO);

    /**
     * 分页查询用户列表
     *
     * @param queryDTO 查询条件
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 分页用户列表
     */
    AjaxJson listUsersPage(UserQueryDTO queryDTO, Integer pageNum, Integer pageSize);

    /**
     * 启用/禁用用户
     *
     * @param userId   用户ID
     * @param isActive 是否启用（0否 1是）
     * @return 操作结果
     */
    AjaxJson toggleUserStatus(Long userId, Integer isActive);

    /**
     * 更新用户最后登录时间
     *
     * @param userId 用户ID
     * @return 更新结果
     */
    AjaxJson updateLastLogin(Long userId);

    /**
     * 检查用户名是否存在
     *
     * @param username 用户名
     * @return 是否存在
     */
    boolean existsByUsername(String username);

    /**
     * 检查用户名是否存在（排除指定用户ID）
     *
     * @param username      用户名
     * @param excludeUserId 排除的用户ID
     * @return 是否存在
     */
    boolean existsByUsername(String username, Long excludeUserId);

    /**
     * 登录口令校验：支持 BCrypt；若为历史 MD5 且校验通过，则自动升级为 BCrypt 并写库。
     *
     * @param user        已加载的用户
     * @param rawPassword 明文口令（不落日志）
     * @return 是否匹配
     */
    boolean verifyPasswordForLogin(SysUser user, String rawPassword);

}