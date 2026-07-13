package com.gov.landcheck.user.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.core.bo.entity.SysUser;
import com.gov.landcheck.core.common.UserTypeConstants;
import com.gov.landcheck.user.dto.UserDTO;

import cn.dev33.satoken.stp.StpUtil;

@ExtendWith(MockitoExtension.class)
class SysUserServiceImplTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    private SysUserServiceImpl service;
    private MockedStatic<StpUtil> stpUtil;

    @BeforeEach
    void setUp() {
        service = new SysUserServiceImpl();
        ReflectionTestUtils.setField(service, "mongoTemplate", mongoTemplate);
        ReflectionTestUtils.setField(service, "userPasswordEncoder", passwordEncoder);
        stpUtil = Mockito.mockStatic(StpUtil.class);
        stpUtil.when(() -> StpUtil.checkRole(UserTypeConstants.SUPER_ADMIN)).thenAnswer(invocation -> null);
        stpUtil.when(StpUtil::getLoginIdDefaultNull).thenReturn(1L);
    }

    @AfterEach
    void tearDown() {
        stpUtil.close();
    }

    @Test
    void updateUserTypeRejectsDemotingOnlyActiveSuperAdmin() {
        when(mongoTemplate.findOne(any(Query.class), eq(SysUser.class))).thenReturn(superAdmin());
        when(mongoTemplate.count(any(Query.class), eq(SysUser.class))).thenReturn(1L);

        AjaxJson result = service.updateUserType(1L, UserTypeConstants.USER);

        assertThat(result.getCode()).isEqualTo(AjaxJson.CODE_ERROR);
        assertThat(result.getMsg()).contains("至少保留一个启用的超级管理员");
        verify(mongoTemplate, never()).save(any(SysUser.class));
    }

    @Test
    void updateUserRejectsDisablingOnlyActiveSuperAdmin() {
        when(mongoTemplate.findOne(any(Query.class), eq(SysUser.class))).thenReturn(superAdmin());
        when(mongoTemplate.exists(any(Query.class), eq(SysUser.class))).thenReturn(false);
        when(mongoTemplate.count(any(Query.class), eq(SysUser.class))).thenReturn(1L);

        UserDTO dto = new UserDTO();
        dto.setUsername("admin");
        dto.setUserType(UserTypeConstants.SUPER_ADMIN);
        dto.setIsActive(0);

        AjaxJson result = service.updateUser(1L, dto);

        assertThat(result.getCode()).isEqualTo(AjaxJson.CODE_ERROR);
        assertThat(result.getMsg()).contains("至少保留一个启用的超级管理员");
        verify(mongoTemplate, never()).save(any(SysUser.class));
    }

    @Test
    void toggleUserStatusRejectsDisablingOnlyActiveSuperAdmin() {
        when(mongoTemplate.findOne(any(Query.class), eq(SysUser.class))).thenReturn(superAdmin());
        when(mongoTemplate.count(any(Query.class), eq(SysUser.class))).thenReturn(1L);

        AjaxJson result = service.toggleUserStatus(1L, 0);

        assertThat(result.getCode()).isEqualTo(AjaxJson.CODE_ERROR);
        assertThat(result.getMsg()).contains("至少保留一个启用的超级管理员");
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(SysUser.class));
    }

    @Test
    void deleteUserRejectsDeletingOnlyActiveSuperAdmin() {
        when(mongoTemplate.findOne(any(Query.class), eq(SysUser.class))).thenReturn(superAdmin());
        when(mongoTemplate.count(any(Query.class), eq(SysUser.class))).thenReturn(1L);

        AjaxJson result = service.deleteUser(1L);

        assertThat(result.getCode()).isEqualTo(AjaxJson.CODE_ERROR);
        assertThat(result.getMsg()).contains("至少保留一个启用的超级管理员");
        verify(mongoTemplate, never()).remove(any(Query.class), eq(SysUser.class));
    }

    @Test
    void updateUserTypeAllowsDemotingWhenAnotherActiveSuperAdminRemains() {
        SysUser admin = superAdmin();
        when(mongoTemplate.findOne(any(Query.class), eq(SysUser.class))).thenReturn(admin);
        when(mongoTemplate.count(any(Query.class), eq(SysUser.class))).thenReturn(2L);

        AjaxJson result = service.updateUserType(1L, UserTypeConstants.USER);

        assertThat(result.getCode()).isEqualTo(AjaxJson.CODE_SUCCESS);
        assertThat(admin.getUserType()).isEqualTo(UserTypeConstants.USER);
        verify(mongoTemplate).save(admin);
    }

    private static SysUser superAdmin() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setUserType(UserTypeConstants.SUPER_ADMIN);
        user.setIsActive(1);
        return user;
    }
}
