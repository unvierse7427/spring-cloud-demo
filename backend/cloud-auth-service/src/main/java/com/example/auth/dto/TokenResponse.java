package com.example.auth.dto;

/**
 * 令牌响应
 */
public class TokenResponse {

    private String tokenType = "Bearer";

    private String accessToken;

    private String refreshToken;

    /** 访问令牌有效期（秒） */
    private long expiresIn;

    /** 刷新令牌有效期（秒） */
    private long refreshExpiresIn;

    public TokenResponse() {
    }

    public TokenResponse(String accessToken, String refreshToken, long expiresIn, long refreshExpiresIn) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.refreshExpiresIn = refreshExpiresIn;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public long getRefreshExpiresIn() {
        return refreshExpiresIn;
    }

    public void setRefreshExpiresIn(long refreshExpiresIn) {
        this.refreshExpiresIn = refreshExpiresIn;
    }
}
