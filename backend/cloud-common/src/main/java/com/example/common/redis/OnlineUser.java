package com.example.common.redis;

import java.io.Serializable;

/**
 * Redis 中保存的在线用户信息
 */
public class OnlineUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;

    private String username;

    private String nickname;

    /** 登录来源 IP */
    private String clientIp;

    private String userAgent;

    /** 本次登录时间，格式 yyyy-MM-dd HH:mm:ss */
    private String loginTime;

    /** 最近一次活跃时间，每次接口调用会被刷新 */
    private String lastActiveTime;

    public OnlineUser() {
    }

    public OnlineUser(Long userId, String username, String nickname) {
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
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

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
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

    public String getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(String loginTime) {
        this.loginTime = loginTime;
    }

    public String getLastActiveTime() {
        return lastActiveTime;
    }

    public void setLastActiveTime(String lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }
}
