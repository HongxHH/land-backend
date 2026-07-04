package com.gov.landcheck.user.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.core.bo.entity.SysUser;
import com.gov.landcheck.core.common.MessageConstant;
import com.gov.landcheck.core.common.UserTypeConstants;
import com.gov.landcheck.core.config.query.MongoQueryBuilder;
import com.gov.landcheck.user.dto.ProfileUpdateRequest;
import com.gov.landcheck.user.dto.RegisterRequest;
import com.gov.landcheck.user.dto.UserDTO;
import com.gov.landcheck.user.dto.UserQueryDTO;
import com.gov.landcheck.user.dto.UserVO;
import com.gov.landcheck.user.service.SysUserService;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.SecureUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 系统用户服务实现类
 *
 * @author system
 * @date 2026/01/22
 */
@Service
@Slf4j
public class SysUserServiceImpl implements SysUserService {

    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private BCryptPasswordEncoder userPasswordEncoder;

    /**
     * 用户管理写操作：仅超级管理员。
     */
    private static void requireSuperAdminRole() {
        StpUtil.checkRole(UserTypeConstants.SUPER_ADMIN);
    }

    @Override
    public AjaxJson createUser(UserDTO userDTO) {
        requireSuperAdminRole();
        try {
            if (!UserTypeConstants.isAssignable(userDTO.getUserType())) {
                return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE,
                        "用户类型无效，允许：SUPER_ADMIN（超级管理员）、DEVELOPER（开发人员）、USER（普通用户）");
            }
            // 检查用户名是否已存在
            if (existsByUsername(userDTO.getUsername())) {
                return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "用户名已存在");
            }

            // 创建用户实体
            SysUser sysUser = new SysUser();
            sysUser.setUsername(userDTO.getUsername());
            sysUser.setPassword(userPasswordEncoder.encode(userDTO.getPassword()));
            sysUser.setRealName(userDTO.getRealName());
            sysUser.setPhone(userDTO.getPhone());
            sysUser.setEmail(userDTO.getEmail());
            sysUser.setRoleId(userDTO.getRoleId());
            sysUser.setDeptId(userDTO.getDeptId());
            sysUser.setUserType(userDTO.getUserType());
            sysUser.setIsActive(userDTO.getIsActive());
            sysUser.setCreateTime(LocalDateTime.now());
            sysUser.setUpdateTime(LocalDateTime.now());

            // 生成自增ID
            sysUser.preSave();

            // 新用户必须 insert，避免 sequence 落后时 save 按 _id 覆盖已有账号
            mongoTemplate.insert(sysUser);

            log.info("创建用户成功: username={}, id={}", sysUser.getUsername(), sysUser.getId());
            return AjaxJson.getSuccess("创建用户成功");

        } catch (Exception e) {
            log.error("创建用户失败: username={}, error={}", userDTO.getUsername(), e.getMessage(), e);
            return AjaxJson.getError("创建用户失败: " + e.getMessage());
        }
    }

    @Override
    public AjaxJson register(RegisterRequest request) {
        try {
            String username = request.getUsername().trim();
            if (existsByUsername(username)) {
                return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "用户名已存在");
            }
            SysUser sysUser = new SysUser();
            sysUser.setUsername(username);
            sysUser.setPassword(userPasswordEncoder.encode(request.getPassword()));
            sysUser.setRealName(request.getRealName().trim());
            sysUser.setPhone(blankToNull(request.getPhone()));
            sysUser.setEmail(blankToNull(request.getEmail()));
            sysUser.setUserType(UserTypeConstants.USER);
            sysUser.setIsActive(1);
            sysUser.setRoleId(null);
            sysUser.setDeptId(null);
            sysUser.setCreateTime(LocalDateTime.now());
            sysUser.setUpdateTime(LocalDateTime.now());
            sysUser.preSave();
            mongoTemplate.insert(sysUser);
            log.info("用户注册成功: username={}, id={}", username, sysUser.getId());
            return AjaxJson.getSuccess("注册成功，请登录");
        } catch (Exception e) {
            log.error("用户注册失败: username={}, error={}", request.getUsername(), e.getMessage(), e);
            return AjaxJson.getError("注册失败: " + e.getMessage());
        }
    }

    private static String blankToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @Override
    public AjaxJson updateProfile(ProfileUpdateRequest request) {
        try {
            long userId = StpUtil.getLoginIdAsLong();
            Optional<SysUser> userOpt = getUserById(userId);
            if (userOpt.isEmpty()) {
                return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, MessageConstant.USER_NOT_EXIST);
            }
            SysUser user = userOpt.get();

            user.setRealName(request.getRealName().trim());
            user.setPhone(blankToNull(request.getPhone()));
            user.setEmail(blankToNull(request.getEmail()));

            if (StringUtils.hasText(request.getNewPassword())) {
                if (!StringUtils.hasText(request.getOldPassword())) {
                    return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "修改密码需提供原密码");
                }
                String newPassword = request.getNewPassword();
                if (newPassword.length() < 6 || newPassword.length() > 20) {
                    return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "新密码长度必须在6-20个字符之间");
                }
                if (!verifyPasswordForLogin(user, request.getOldPassword())) {
                    return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, MessageConstant.USER_PASSWORD_ERROR);
                }
                user.setPassword(userPasswordEncoder.encode(newPassword));
            }

            user.setUpdateTime(LocalDateTime.now());
            mongoTemplate.save(user);

            log.info("用户更新个人信息: id={}, username={}", user.getId(), user.getUsername());
            return AjaxJson.getSuccessData(toUserVO(user));
        } catch (Exception e) {
            log.error("更新个人信息失败: error={}", e.getMessage(), e);
            return AjaxJson.getError("更新个人信息失败: " + e.getMessage());
        }
    }

    private static UserVO toUserVO(SysUser user) {
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

    @Override
    public AjaxJson updateUserType(Long userId, String newUserType) {
        requireSuperAdminRole();
        try {
            if (!UserTypeConstants.isAssignable(newUserType)) {
                return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE,
                        "用户类型无效，允许：SUPER_ADMIN、DEVELOPER、USER");
            }

            Optional<SysUser> targetOpt = getUserById(userId);
            if (targetOpt.isEmpty()) {
                return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "用户不存在");
            }
            SysUser target = targetOpt.get();
            String currentType = target.getUserType();

            if (newUserType.equals(currentType)) {
                return AjaxJson.getSuccess("权限类型未变化");
            }

            target.setUserType(newUserType);
            target.setUpdateTime(LocalDateTime.now());
            mongoTemplate.save(target);
            log.warn("[AUDIT] user_type_changed operatorId={} targetId={} targetUsername={} oldType={} newType={}",
                    StpUtil.getLoginIdDefaultNull(), userId, target.getUsername(), currentType, newUserType);
            return AjaxJson.getSuccess("权限类型已更新");
        } catch (Exception e) {
            log.error("更新用户类型失败: userId={}, error={}", userId, e.getMessage(), e);
            return AjaxJson.getError("更新用户类型失败: " + e.getMessage());
        }
    }

    @Override
    public AjaxJson updateUserPassword(Long userId, String newPassword) {
        requireSuperAdminRole();
        try {
            Optional<SysUser> targetOpt = getUserById(userId);
            if (targetOpt.isEmpty()) {
                return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "用户不存在");
            }
            SysUser target = targetOpt.get();

            target.setPassword(userPasswordEncoder.encode(newPassword));
            target.setUpdateTime(LocalDateTime.now());
            mongoTemplate.save(target);

            log.warn("[AUDIT] user_password_reset operatorId={} targetId={} targetUsername={}",
                    StpUtil.getLoginIdDefaultNull(), userId, target.getUsername());
            return AjaxJson.getSuccess("密码已更新");
        } catch (Exception e) {
            log.error("重置用户密码失败: userId={}, error={}", userId, e.getMessage(), e);
            return AjaxJson.getError("重置用户密码失败: " + e.getMessage());
        }
    }

    @Override
    public AjaxJson updateUser(Long userId, UserDTO userDTO) {
        requireSuperAdminRole();
        try {
            if (!UserTypeConstants.isAssignable(userDTO.getUserType())) {
                return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE,
                        "用户类型无效，允许：SUPER_ADMIN、DEVELOPER、USER");
            }
            // 检查用户是否存在
            Optional<SysUser> existingUserOpt = getUserById(userId);
            if (existingUserOpt.isEmpty()) {
                return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "用户不存在");
            }

            // 检查用户名是否已被其他用户使用
            if (existsByUsername(userDTO.getUsername(), userId)) {
                return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "用户名已存在");
            }

            SysUser existingUser = existingUserOpt.get();
            existingUser.setUsername(userDTO.getUsername());

            // 如果密码不为空，则更新密码
            if (StringUtils.hasText(userDTO.getPassword())) {
                existingUser.setPassword(userPasswordEncoder.encode(userDTO.getPassword()));
            }

            existingUser.setRealName(userDTO.getRealName());
            existingUser.setPhone(userDTO.getPhone());
            existingUser.setEmail(userDTO.getEmail());
            existingUser.setRoleId(userDTO.getRoleId());
            existingUser.setDeptId(userDTO.getDeptId());
            existingUser.setUserType(userDTO.getUserType());
            existingUser.setIsActive(userDTO.getIsActive());
            existingUser.setUpdateTime(LocalDateTime.now());

            // 保存到数据库
            mongoTemplate.save(existingUser);

            log.info("更新用户信息成功: id={}, username={}", userId, existingUser.getUsername());
            return AjaxJson.getSuccess("更新用户信息成功");

        } catch (Exception e) {
            log.error("更新用户信息失败: id={}, error={}", userId, e.getMessage(), e);
            return AjaxJson.getError("更新用户信息失败: " + e.getMessage());
        }
    }

    @Override
    public AjaxJson deleteUser(Long userId) {
        requireSuperAdminRole();
        try {
            // 检查用户是否存在
            Optional<SysUser> userOpt = getUserById(userId);
            if (userOpt.isEmpty()) {
                return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "用户不存在");
            }
            String targetUsername = userOpt.get().getUsername();

            // 删除用户
            Query query = new Query(Criteria.where("id").is(userId));
            mongoTemplate.remove(query, SysUser.class);

            log.warn("[AUDIT] user_deleted operatorId={} targetId={} targetUsername={}",
                    StpUtil.getLoginIdDefaultNull(), userId, targetUsername);
            log.info("删除用户成功: id={}", userId);
            return AjaxJson.getSuccess("删除用户成功");

        } catch (Exception e) {
            log.error("删除用户失败: id={}, error={}", userId, e.getMessage(), e);
            return AjaxJson.getError("删除用户失败: " + e.getMessage());
        }
    }

    @Override
    public Optional<SysUser> getUserById(Long userId) {
        try {
            Query query = new Query(Criteria.where("id").is(userId));
            SysUser user = mongoTemplate.findOne(query, SysUser.class);
            return Optional.ofNullable(user);
        } catch (Exception e) {
            log.error("根据ID获取用户信息失败: id={}", userId, e);
            throw new IllegalStateException("查询用户数据失败", e);
        }
    }

    @Override
    public Optional<SysUser> getUserByUsername(String username) {
        try {
            Query query = new Query(Criteria.where("username").is(username));
            SysUser user = mongoTemplate.findOne(query, SysUser.class);
            return Optional.ofNullable(user);
        } catch (Exception e) {
            log.error("根据用户名获取用户信息失败: username={}", username, e);
            throw new IllegalStateException("查询用户数据失败", e);
        }
    }

    @Override
    public List<SysUser> listUsers(UserQueryDTO queryDTO) {
        try {
            Query query = buildQuery(queryDTO);
            return mongoTemplate.find(query, SysUser.class);
        } catch (Exception e) {
            log.error("查询用户列表失败: error={}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public AjaxJson listUsersPage(UserQueryDTO queryDTO, Integer pageNum, Integer pageSize) {
        try {
            // 设置默认分页参数
            if (pageNum == null || pageNum < 1) {
                pageNum = 1;
            }
            if (pageSize == null || pageSize < 1 || pageSize > 100) {
                pageSize = 10;
            }

            Query query = buildQuery(queryDTO);
            Pageable pageable = PageRequest.of(pageNum - 1, pageSize);

            // 查询总数
            long total = mongoTemplate.count(query, SysUser.class);

            // 分页查询
            query.with(pageable);
            List<SysUser> users = mongoTemplate.find(query, SysUser.class);

            // 转换为VO
            List<UserVO> userVOs = users.stream().map(this::convertToVO).toList();

            return AjaxJson.getSuccessData(userVOs).set("total", total);

        } catch (Exception e) {
            log.error("分页查询用户列表失败: error={}", e.getMessage(), e);
            return AjaxJson.getError("查询用户列表失败: " + e.getMessage());
        }
    }

    @Override
    public AjaxJson toggleUserStatus(Long userId, Integer isActive) {
        requireSuperAdminRole();
        try {
            // 检查用户是否存在
            Optional<SysUser> userOpt = getUserById(userId);
            if (userOpt.isEmpty()) {
                return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "用户不存在");
            }

            // 更新状态
            Query query = new Query(Criteria.where("id").is(userId));
            Update update = new Update()
                    .set("is_active", isActive)
                    .set("update_time", LocalDateTime.now());

            mongoTemplate.updateFirst(query, update, SysUser.class);

            String statusText = isActive == 1 ? "启用" : "禁用";
            log.info("{}用户成功: id={}", statusText, userId);
            return AjaxJson.getSuccess(statusText + "用户成功");

        } catch (Exception e) {
            log.error("切换用户状态失败: id={}, error={}", userId, e.getMessage(), e);
            return AjaxJson.getError("切换用户状态失败: " + e.getMessage());
        }
    }

    @Override
    public AjaxJson updateLastLogin(Long userId) {
        try {
            Query query = new Query(Criteria.where("id").is(userId));
            Update update = new Update()
                    .set("last_login", LocalDateTime.now())
                    .set("update_time", LocalDateTime.now());

            mongoTemplate.updateFirst(query, update, SysUser.class);

            log.info("更新用户最后登录时间成功: id={}", userId);
            return AjaxJson.getSuccess("更新登录时间成功");

        } catch (Exception e) {
            log.error("更新用户最后登录时间失败: id={}, error={}", userId, e.getMessage(), e);
            return AjaxJson.getError("更新登录时间失败: " + e.getMessage());
        }
    }

    @Override
    public boolean existsByUsername(String username) {
        try {
            Query query = new Query(Criteria.where("username").is(username));
            return mongoTemplate.exists(query, SysUser.class);
        } catch (Exception e) {
            log.error("检查用户名是否存在失败: username={}", username, e);
            throw new IllegalStateException("检查用户名失败", e);
        }
    }

    @Override
    public boolean existsByUsername(String username, Long excludeUserId) {
        try {
            Query query = new Query(Criteria.where("username").is(username).and("id").ne(excludeUserId));
            return mongoTemplate.exists(query, SysUser.class);
        } catch (Exception e) {
            log.error("检查用户名是否存在失败: username={}, excludeId={}", username, excludeUserId, e);
            throw new IllegalStateException("检查用户名失败", e);
        }
    }

    @Override
    public boolean verifyPasswordForLogin(SysUser user, String rawPassword) {
        if (user == null || !StringUtils.hasText(rawPassword)) {
            return false;
        }
        String stored = user.getPassword();
        if (!StringUtils.hasText(stored)) {
            return false;
        }
        if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
            return userPasswordEncoder.matches(rawPassword, stored);
        }
        String digest = SecureUtil.md5(rawPassword);
        if (!digest.equalsIgnoreCase(stored)) {
            return false;
        }
        user.setPassword(userPasswordEncoder.encode(rawPassword));
        user.setUpdateTime(LocalDateTime.now());
        mongoTemplate.save(user);
        return true;
    }

    /**
     * 构建查询条件
     */
    private Query buildQuery(UserQueryDTO queryDTO) {
        Query query = new Query();

        if (queryDTO != null) {
            List<Criteria> criteriaList = new ArrayList<>();

            if (StringUtils.hasText(queryDTO.getUsername())) {
                criteriaList.add(Criteria.where("username").regex(safeRegexFragment(queryDTO.getUsername()), "i"));
            }

            if (StringUtils.hasText(queryDTO.getRealName())) {
                criteriaList.add(Criteria.where("real_name").regex(safeRegexFragment(queryDTO.getRealName()), "i"));
            }

            if (StringUtils.hasText(queryDTO.getPhone())) {
                criteriaList.add(Criteria.where("phone").is(queryDTO.getPhone()));
            }

            if (StringUtils.hasText(queryDTO.getEmail())) {
                criteriaList.add(Criteria.where("email").is(queryDTO.getEmail()));
            }

            if (StringUtils.hasText(queryDTO.getUserType())) {
                criteriaList.add(Criteria.where("user_type").is(queryDTO.getUserType()));
            }

            if (queryDTO.getIsActive() != null) {
                criteriaList.add(Criteria.where("is_active").is(queryDTO.getIsActive()));
            }

            if (queryDTO.getDeptId() != null) {
                criteriaList.add(Criteria.where("dept_id").is(queryDTO.getDeptId()));
            }

            if (queryDTO.getRoleId() != null) {
                criteriaList.add(Criteria.where("role_id").is(queryDTO.getRoleId()));
            }

            if (!criteriaList.isEmpty()) {
                query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
            }
        }

        return query;
    }

    /**
     * 与 {@link MongoQueryBuilder} 一致：字面量匹配 + 长度上限，降低 ReDoS 风险。
     */
    private static String safeRegexFragment(String raw) {
        String t = raw.trim();
        if (t.length() > MongoQueryBuilder.MAX_REGEX_VALUE_LENGTH) {
            throw new IllegalArgumentException(
                    "筛选关键词长度超过上限 " + MongoQueryBuilder.MAX_REGEX_VALUE_LENGTH);
        }
        return Pattern.quote(t);
    }

    /**
     * 实体转换为VO
     */
    private UserVO convertToVO(SysUser sysUser) {
        UserVO userVO = new UserVO();
        userVO.setId(sysUser.getId());
        userVO.setUsername(sysUser.getUsername());
        userVO.setRealName(sysUser.getRealName());
        userVO.setPhone(sysUser.getPhone());
        userVO.setEmail(sysUser.getEmail());
        userVO.setRoleId(sysUser.getRoleId());
        userVO.setDeptId(sysUser.getDeptId());
        userVO.setUserType(sysUser.getUserType());
        userVO.setIsActive(sysUser.getIsActive());
        userVO.setCreateTime(sysUser.getCreateTime());
        userVO.setUpdateTime(sysUser.getUpdateTime());
        userVO.setLastLogin(sysUser.getLastLogin());
        return userVO;
    }

}