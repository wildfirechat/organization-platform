package cn.wildfirechat.org.im.jpa;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;

/**
 * 野火 IM 服务的用户会话表 t_user_session（只读）。
 * <p>
 * 字段含义来源：官方文档《野火IM数据库表结构说明》第 17 节 t_user_session
 * https://docs.wildfirechat.cn/blogs/wfchat_schema_documentation.html
 * <p>
 * 表中一行 = 一个 (用户, 客户端设备)，唯一键为 (_cid, _uid)，
 * 所以同一用户有多台设备就会有多行。
 * <p>
 * 刻意不映射 _token / _voip_token / _secret / _db_secret 这些密钥类字段：
 * 业务用不到，少映射就少一条泄露途径。
 */
@Entity
@Table(name = "t_user_session")
public class UserSessionEntity implements Serializable {

    @Id
    @Column(name = "id")
    private Integer id;

    /** 用户 ID，与组织库 t_employee.employee_id 对应 */
    @Column(name = "_uid", length = 64)
    private String uid;

    /** 客户端 ID（设备唯一标识） */
    @Column(name = "_cid", length = 64)
    private String cid;

    /**
     * 客户端平台。取值见 cn.wildfirechat.proto.ProtoConstants.Platform
     * （本工程 src/lib/common-*.jar）：0-UNSET，1-iOS，2-Android，3-Windows，
     * 4-OSX，5-WEB，6-WX，7-LINUX，8-iPad，9-APad。
     * 注意：官方“数据库表结构说明”该字段写的是 0-iOS 起，与 SDK 整体差 1，
     * 以前端映射为准按 SDK 处理。
     */
    @Column(name = "_platform")
    private int platform;

    /** 登录 IP 地址 */
    @Column(name = "_ip", length = 40)
    private String ip;

    /**
     * 最后更新/活跃时间戳（官方文档原文：最后更新/活跃时间戳）。
     * 单位见 UserSessionRepository 的说明，读取时统一用 ImSessionTime 归一化成毫秒。
     */
    @Column(name = "_dt")
    private long dt;

    /** 软删除标记：0-正常，1-已登出/过期 */
    @Column(name = "_deleted")
    private int deleted;

    @Column(name = "_device_name", length = 64)
    private String deviceName;

    @Column(name = "_phone_name", length = 64)
    private String phoneName;

    /** 用户类型：0-普通用户，1-机器人，2-设备 */
    @Column(name = "_user_type")
    private int userType;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getCid() {
        return cid;
    }

    public void setCid(String cid) {
        this.cid = cid;
    }

    public int getPlatform() {
        return platform;
    }

    public void setPlatform(int platform) {
        this.platform = platform;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public long getDt() {
        return dt;
    }

    public void setDt(long dt) {
        this.dt = dt;
    }

    public int getDeleted() {
        return deleted;
    }

    public void setDeleted(int deleted) {
        this.deleted = deleted;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getPhoneName() {
        return phoneName;
    }

    public void setPhoneName(String phoneName) {
        this.phoneName = phoneName;
    }

    public int getUserType() {
        return userType;
    }

    public void setUserType(int userType) {
        this.userType = userType;
    }
}
