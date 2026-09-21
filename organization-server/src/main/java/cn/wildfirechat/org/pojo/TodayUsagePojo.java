package cn.wildfirechat.org.pojo;

/**
 * 「今日使用情况」列表中的一行。
 * <p>
 * 列表包含部门（含所有子部门）的全部成员，分成两组：
 * 「在线」的用户排在前面（按登录时间倒序），「未登录」的排在后面
 * （按部门、姓名排序，保证每次导出的顺序稳定）。
 * <p>
 * 注意：一个用户即使有多台设备（t_user_session 里多行），这里也只出现一行，
 * 登录时间取该用户当天最新的一条会话。
 */
public class TodayUsagePojo {

    /** 在线：今天登录过且会话仍然有效（t_user_session._deleted = 0） */
    public static final int STATUS_ONLINE = 0;
    /** 未登录：部门内其余成员，含“今天登录过但已登出”的人 */
    public static final int STATUS_NOT_LOGGED_IN = 1;

    /** 状态：0-在线，1-未登录 */
    public int status;
    /** IM 用户 ID，同时是组织库的 employee_id */
    public String employeeId;
    /** 姓名，取自组织库 t_employee */
    public String name;
    /** 手机号，取自组织库 t_employee */
    public String mobile;
    /** 所在部门名称，取自组织库；一个人多部门时用“、”连接 */
    public String departmentName;
    /** 头像，取自组织库 t_employee */
    public String portraitUrl;
    /** 客户端平台原始值，取值见 ProtoConstants.Platform；未登录用户为 0 */
    public int platform;
    /** 登录 IP；未登录用户为空 */
    public String ip;
    /**
     * 登录时间，毫秒时间戳（已归一化）；未登录用户为 0。
     * 取自 t_user_session._dt —— 官方文档称其为“最后更新/活跃时间戳”，
     * 客户端每次登录都会更新该会话行，业务上作为登录时间使用。
     */
    public long loginTime;
}
