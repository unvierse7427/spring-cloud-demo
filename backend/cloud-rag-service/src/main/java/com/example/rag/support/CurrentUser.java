package com.example.rag.support;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * 从 SecurityContext 中读取当前登录身份，令牌的 subject 即用户 id
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id() {
        return Long.valueOf(jwt().getSubject());
    }

    public static String name() {
        String username = jwt().getClaimAsString("username");
        return username == null ? jwt().getSubject() : username;
    }

    /**
     * 当前用户的权限标识集合，同时包含角色码（ROLE_ADMIN）与权限码（user:create），
     * 未认证时返回空集合。
     */
    public static List<String> authorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return List.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    private static Jwt jwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("当前请求未携带有效的登录信息");
        }
        return jwt;
    }
}
