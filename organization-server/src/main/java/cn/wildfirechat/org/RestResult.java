package cn.wildfirechat.org;

public class RestResult {
    public enum RestCode {
        SUCCESS(0, "success"),
        ERROR_INVALID_MOBILE(1, "无效的电话号码"),
        ERROR_SEND_SMS_OVER_FREQUENCY(3, "请求验证码太频繁"),
        ERROR_SERVER_ERROR(4, "服务器异常"),
        ERROR_CODE_EXPIRED(5, "验证码已过期"),
        ERROR_CODE_INCORRECT(6, "验证码错误"),
        ERROR_SERVER_CONFIG_ERROR(7, "服务器配置错误"),
        ERROR_SESSION_EXPIRED(8, "会话不存在或已过期"),
        ERROR_SESSION_NOT_VERIFIED(9, "会话没有验证"),
        ERROR_SESSION_NOT_SCANED(10, "会话没有被扫码"),
        ERROR_SERVER_NOT_IMPLEMENT(11, "功能没有实现"),
        ERROR_GROUP_ANNOUNCEMENT_NOT_EXIST(12, "群公告不存在"),
        ERROR_NOT_LOGIN(13, "没有登录"),
        ERROR_NO_RIGHT(14, "没有权限"),
        ERROR_INVALID_PARAMETER(15, "无效参数"),
        ERROR_NOT_EXIST(16, "对象不存在"),
        ERROR_ALREADY_EXIST(17, "对象已经存在"),
        ERROR_SESSION_CANCELED(18, "会话已经取消"),
        ERROR_CODE_ACCOUNT_NOT_EXIST(19, "账户不存在"),
        ERROR_CODE_APPLICATION_ALREADY_EXIST(20, "应用已经存在"),
        ERROR_CODE_PASSWORD_INCORRECT(21, "密码错误"),
        ERROR_CODE_APPLICATION_TYPE_INVALID(22, "应用类型错误"),
        ERROR_CANNOT_MODIFY_OGR_PARENT(23, "不能修改组织的父节点，应该调用移动"),
        ERROR_OGR_CHILD_NOT_EMPTY(24, "组织的子节点非空"),
        ERROR_ORGANIZATION_NOT_EXIST(25, "组织不存在"),
        ERROR_CANNOT_MODIFY_EMP_PARENT(26, "不能修改员工的父节点，应该调用移动"),
        ERROR_DATA_ERROR(27, "数据错误，请联系管理员修复"),
        ERROR_PARENT_NOT_EXIST(28, "上级组织不存在"),
        ERROR_CREATE_ORG_HAS_GROUP_ID(29, "创建组织时有群组id，必须先创建组织成功后，再创建群组"),
        ERROR_UPDATE_ORG_ADD_GROUP(30, "不能修改组织的群组，应该调用创建群组或者解散群组"),
        ERROR_ORG_CREATE_GROUP_NEED_MANAGER(31, "组织创建群组需要组织有管理者"),
        ERROR_IM_SERVER_ERROR(32, "IM服务错误"),
        ERROR_CANNOT_DELETE_ORGANIZATION_MANAGER(33, " 不能删除组织负责人，应先将组织负责人更新为其他人");
        public int code;
        public String msg;

        RestCode(int code, String msg) {
            this.code = code;
            this.msg = msg;
        }

    }

    private int code;
    private String message;
    private Object result;

    public static RestResult ok(Object object) {
        return new RestResult(RestCode.SUCCESS, object);
    }

    public static RestResult error(RestCode code) {
        return new RestResult(code, null);
    }

    public static RestResult result(RestCode code, Object object) {
        return new RestResult(code, object);
    }

    public static RestResult result(int code, String message, Object object) {
        RestResult r = new RestResult(RestCode.SUCCESS, object);
        r.code = code;
        r.message = message;
        return r;
    }

    private RestResult(RestCode code, Object result) {
        this.code = code.code;
        this.message = code.msg;
        this.result = result;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }
}
