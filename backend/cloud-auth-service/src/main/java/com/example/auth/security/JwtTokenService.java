package com.example.auth.security;

import com.example.auth.config.JwtProperties;
import com.example.common.security.JwtAuthConverter;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 使用 RSA 私钥（RS256）签发访问令牌与刷新令牌。
 * 网关与业务服务只需持有对应公钥即可离线验签。
 */
@Component
public class JwtTokenService {

    /** 权限声明名，与 JwtAuthConverter 保持一致 */
    public static final String CLAIM_AUTHORITIES = JwtAuthConverter.AUTHORITIES_CLAIM;

    /** 用户名声明名 */
    public static final String CLAIM_USERNAME = "username";

    /** 令牌类型声明名，取值 access / refresh */
    public static final String CLAIM_TOKEN_TYPE = "token_type";

    public static final String TOKEN_TYPE_ACCESS = "access";

    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private final JwtProperties properties;

    private final ResourceLoader resourceLoader;

    private JwtEncoder jwtEncoder;

    public JwtTokenService(JwtProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() throws IOException {
        this.jwtEncoder = new NimbusJwtEncoder(jwkSource());
    }

    /**
     * 由 private.pem / public.pem 构建 Nimbus 的 JWK 来源
     */
    private JWKSource<SecurityContext> jwkSource() throws IOException {
        RSAPrivateKey privateKey = readKey("classpath:keys/private.pem", RsaKeyConverters.pkcs8());
        RSAPublicKey publicKey = readKey("classpath:keys/public.pem", RsaKeyConverters.x509());
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(properties.getKeyId())
                .build();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    private <T> T readKey(String location, org.springframework.core.convert.converter.Converter<InputStream, T> converter)
            throws IOException {
        Resource resource = resourceLoader.getResource(location);
        try (InputStream inputStream = resource.getInputStream()) {
            return converter.convert(inputStream);
        }
    }

    /**
     * 签发令牌对
     */
    public TokenPair createToken(Long userId, String username, List<String> authorities) {
        Instant now = Instant.now();
        String accessToken = encode(userId, username, authorities, TOKEN_TYPE_ACCESS,
                now, now.plus(properties.getAccessTokenTtl()));
        String refreshToken = encode(userId, username, authorities, TOKEN_TYPE_REFRESH,
                now, now.plus(properties.getRefreshTokenTtl()));
        return new TokenPair(accessToken, refreshToken,
                properties.getAccessTokenTtl().getSeconds(),
                properties.getRefreshTokenTtl().getSeconds());
    }

    private String encode(Long userId, String username, List<String> authorities, String tokenType,
                         Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .subject(String.valueOf(userId))
                .id(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .claim(CLAIM_AUTHORITIES, authorities)
                .build();
        JwsHeader headers = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(properties.getKeyId())
                .type("JWT")
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }

    /**
     * 令牌对
     */
    public static class TokenPair {

        private final String accessToken;

        private final String refreshToken;

        private final long expiresIn;

        private final long refreshExpiresIn;

        public TokenPair(String accessToken, String refreshToken, long expiresIn, long refreshExpiresIn) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
            this.refreshExpiresIn = refreshExpiresIn;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public long getExpiresIn() {
            return expiresIn;
        }

        public long getRefreshExpiresIn() {
            return refreshExpiresIn;
        }
    }
}
