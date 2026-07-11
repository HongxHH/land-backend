package com.gov.landcheck.core.common;

/**
 * @Author: DangKong
 * @Date: 2023/3/1 14:19
 * @Description: 接口返回的各类常量信息
 */

public class MessageConstant {


    public static final String ROLE_EXIST = "角色已经存在！";
    public static final String ROLE_PERMISSION_EXIST = "角色权限已经存在！";
    public static final String DELETE_SUCCESS = "删除成功！";
    public static final String DELETE_FAILED = "删除失败！";
    public static final String OPERATE_SUCCESS = "操作成功！";
    public static final String USER_NOT_EXIST = "用户不存在！";
    public static final String USER_PASSWORD_ERROR = "密码错误！";
    public static final String NOT_LOGIN = "未登录！";

    private MessageConstant() {
        throw new IllegalStateException("MessageConstant class");
    }

    public static final Integer PARAMS_ERROR_CODE = 201;
    public static final Integer PROCESS_ERROR_CODE = 201;

    public static final String PARAMS_IS_NOT_NULL = "参数是必需的！";
    public static final String FILE_EMPTY = "文件为空！";
    public static final String PARAMS_LENGTH_REQUIRED = "参数的长度必须符合要求！";
    public static final String PARAMS_FORMAT_ERROR = "参数格式错误！";
    public static final String PARAMS_TYPE_ERROR = "类型转换错误";
    public static final String DATA_HAS_EXIST = "数据已经存在！";
    public static final String DATA_IS_NULL = "数据为空！";
    public static final String FORMAT_ERROR = "格式不支持！";
    public static final String DATA_DUPLICATE = "文件已经存在！";
    public static final String REQUEST_METHOD_ERROR = "请求方法不对！";
    public static final String FILE_SIZE_ERROR = "上传的文件超过大小限制!";

    public static final String USER_HAS_BANNED = "该账号已经被屏蔽，请联系管理员！";

    public static final String OPERATE_FAILED = "操作失败！";
    public static final String SUCCESS = "SUCCESS";

    public static final String FILE_NOT_FOUND = "文件不存在！";

    public static final String USER_EXIST = "用户名已经存在！";

}
