package com.example.gateway.config;

import com.example.common.security.JwtAuthConverter;
import com.example.gateway.security.GatewayAccessDeniedHandler;
import com.example.gateway.security.GatewayAuthEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * 网关统一鉴权：所有请求先在此校验 JWT，未通过直接返回 401/403，不转发到业务服务
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /** 无需认证即可访问的路由 */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/login",
            "/api/auth/refresh",
            "/actuator/**"
    };

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http.csrf().disable()
                .httpBasic().disable()
                .formLogin().disable()
                .logout().disable()
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(new GatewayAuthEntryPoint())
                        .accessDeniedHandler(new GatewayAccessDeniedHandler())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                new ReactiveJwtAuthenticationConverterAdapter(new JwtAuthConverter()))))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(new GatewayAuthEntryPoint())
                        .accessDeniedHandler(new GatewayAccessDeniedHandler()));
        return http.build();
    }
}
