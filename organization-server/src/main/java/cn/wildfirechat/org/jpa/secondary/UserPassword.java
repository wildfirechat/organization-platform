package cn.wildfirechat.org.jpa.secondary;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "user_password")
public class UserPassword {
    @Id
    @Column(length = 128, name = "user_id")
    private String userId;

    @Column(name = "password")
    private String password;

    @Column(name = "salt")
    private String salt;

    @Column(name = "reset_code")
    private String resetCode;

    @Column(name = "reset_code_time")
    private long resetCodeTime;

    @Column(name = "try_count")
    private int tryCount;

    @Column(name = "last_try_time")
    private long lastTryTime;

    public UserPassword() {
    }

    public UserPassword(String userId) {
        this.userId = userId;
    }

    public UserPassword(String userId, String password, String salt) {
        this.userId = userId;
        this.password = password;
        this.salt = salt;
        this.resetCodeTime = 0;
        this.tryCount = 0;
        this.lastTryTime = 0;
    }

    public UserPassword(String userId, String password, String salt, String resetCode, long resetCodeTime) {
        this.userId = userId;
        this.password = password;
        this.salt = salt;
        this.resetCode = resetCode;
        this.resetCodeTime = resetCodeTime;
        this.tryCount = 0;
        this.lastTryTime = 0;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public String getResetCode() {
        return resetCode;
    }

    public void setResetCode(String resetCode) {
        this.resetCode = resetCode;
    }

    public long getResetCodeTime() {
        return resetCodeTime;
    }

    public void setResetCodeTime(long resetCodeTime) {
        this.resetCodeTime = resetCodeTime;
    }

    public int getTryCount() {
        return tryCount;
    }

    public void setTryCount(int tryCount) {
        this.tryCount = tryCount;
    }

    public long getLastTryTime() {
        return lastTryTime;
    }

    public void setLastTryTime(long lastTryTime) {
        this.lastTryTime = lastTryTime;
    }
}
