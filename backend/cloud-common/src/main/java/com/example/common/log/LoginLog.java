package com.example.common.log;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录/登出日志，对应表 sys_login_log
 */
@TableName("sys_login_log")
public class LoginLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 登录 */
    public static final String TYPE_LOGIN = "LOGIN";

    /** 登出 */
    public static final String TYPE_LOGOUT = "LOGOUT";

    /** 刷新令牌 */
    public static final String TYPE_REFRESH = "REFRESH";

    /** 成功 */
    public static final int STATUS_SUCCESS = 1;

    /** 失败 */
    public static final int STATUS_FAILURE = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    private String loginType;

    private Integer status;

    private String message;

    private String clientIp;

    private String userAgent;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime loginTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getLoginType() {
        return loginType;
    }

    public void setLoginType(String loginType) {
        this.loginType = loginType;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public LocalDateTime getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(LocalDateTime loginTime) {
        this.loginTime = loginTime;
    }
}
