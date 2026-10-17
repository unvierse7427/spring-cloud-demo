package com.example.chat.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;

/**
 * WebSocket 握手阶段的令牌校验。
 *
 * <p>浏览器无法在 WebSocket 握手时自定义请求头，因此令牌通过 URL 参数传入，
 * 这里使用与网关、业务服务相同的公钥离线验签，校验签名与过期时间。
 */
@Component
public class JwtTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenVerifier.class);

    /** 用户名声明名，与认证中心签发时保持一致 */
    private static final String CLAIM_USERNAME = "username";

    private final NimbusJwtDecoder decoder;

    public JwtTokenVerifier(
            @Value("${spring.security.oauth2.resourceserver.jwt.public-key-location:classpath:keys/public.pem}")
            Resource publicKeyLocation) throws IOException {
        RSAPublicKey publicKey;
        try (InputStream inputStream = publicKeyLocation.getInputStream()) {
            publicKey = RsaKeyConverters.x509().convert(inputStream);
        }
        this.decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
    }

    /**
     * 校验令牌并解析登录身份，校验失败返回 null
     */
    public ChatUser verify(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            Jwt jwt = decoder.decode(token);
            return new ChatUser(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString(CLAIM_USERNAME));
        } catch (Exception e) {
            log.debug("WebSocket 帧令牌校验失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * WebSocket 连接上的登录身份
     */
    public record ChatUser(Long userId, String username) {
    }
}
