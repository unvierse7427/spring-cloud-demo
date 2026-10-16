package com.example.gateway.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 未携带或携带无效令牌时返回 401
 */
public class GatewayAuthEntryPoint implements ServerAuthenticationEntryPoint {

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        return GatewayResponseWriter.write(exchange.getResponse(), HttpStatus.UNAUTHORIZED, 401, "未登录或登录已过期");
    }
}
