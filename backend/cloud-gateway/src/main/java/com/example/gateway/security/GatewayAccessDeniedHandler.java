package com.example.gateway.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 已认证但缺少权限时返回 403
 */
public class GatewayAccessDeniedHandler implements ServerAccessDeniedHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        return GatewayResponseWriter.write(exchange.getResponse(), HttpStatus.FORBIDDEN, 403, "没有访问权限");
    }
}
