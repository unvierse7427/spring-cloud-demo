package com.example.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * JWT 相关配置，对应 application.yml 中的 cloud.jwt
 */
@Component
@ConfigurationProperties(prefix = "cloud.jwt")
public class JwtProperties {

    /** 签发者 */
    private String issuer = "cloud-auth-service";

    /** 密钥标识，写入 JWT 头部 kid */
    private String keyId = "cloud-jwt-key";

    /** 签名私钥位置：支持 classpath:/file: 前缀，也支持裸文件系统路径 */
    private String privateKeyLocation = "classpath:keys/private.pem";

    /** 访问令牌有效期 */
    private Duration accessTokenTtl = Duration.ofHours(2);

    /** 刷新令牌有效期 */
    private Duration refreshTokenTtl = Duration.ofDays(7);

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getPrivateKeyLocation() {
        return privateKeyLocation;
    }

    public void setPrivateKeyLocation(String privateKeyLocation) {
        this.privateKeyLocation = privateKeyLocation;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }
}
