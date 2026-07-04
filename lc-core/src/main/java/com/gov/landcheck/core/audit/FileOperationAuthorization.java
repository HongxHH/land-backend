package com.gov.landcheck.core.audit;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.Project;
import com.gov.landcheck.core.common.UserTypeConstants;

import cn.dev33.satoken.stp.StpUtil;

/**
 * 文件/项目破坏性操作授权：默认仅上传人、项目创建人或管理员可删改。
 */
public final class FileOperationAuthorization {

    private static final String DENY_FILE_MUTATE = "仅上传人或管理员可执行该操作";
    private static final String DENY_PROJECT_DELETE = "仅项目创建人或管理员可删除项目";

    private FileOperationAuthorization() {
    }

    public static boolean canMutateFile(FileRecord fileRecord) {
        if (fileRecord == null) {
            return false;
        }
        if (isPrivilegedOperator()) {
            return true;
        }
        Long operatorId = OperatorContext.getOperatorId();
        Long uploadUserId = fileRecord.getUploadUserId();
        return operatorId != null && uploadUserId != null && operatorId.equals(uploadUserId);
    }

    public static String denyReasonForFileMutate() {
        return DENY_FILE_MUTATE;
    }

    public static boolean canDeleteProject(Project project) {
        if (project == null) {
            return false;
        }
        if (isPrivilegedOperator()) {
            return true;
        }
        Long operatorId = OperatorContext.getOperatorId();
        Long createdBy = project.getCreatedBy();
        if (createdBy == null || createdBy <= 0L) {
            return false;
        }
        return operatorId != null && operatorId.equals(createdBy);
    }

    public static String denyReasonForProjectDelete() {
        return DENY_PROJECT_DELETE;
    }

    private static boolean isPrivilegedOperator() {
        try {
            return StpUtil.isLogin()
                    && (StpUtil.hasRole(UserTypeConstants.SUPER_ADMIN)
                            || StpUtil.hasRole(UserTypeConstants.DEVELOPER));
        } catch (Exception ex) {
            return false;
        }
    }
}
