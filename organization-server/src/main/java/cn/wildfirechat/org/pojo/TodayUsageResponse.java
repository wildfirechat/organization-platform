package cn.wildfirechat.org.pojo;

/**
 * 「今日使用情况」的分页响应，在通用分页结构上额外带上两组人数，
 * 便于界面顶部直接显示“在线 N 人 / 未登录 M 人 / 共 T 人”。
 */
public class TodayUsageResponse extends PageResponse<TodayUsagePojo> {
    /** 在线人数 */
    public int onlineCount;
    /** 未登录人数 */
    public int notLoggedInCount;
}
