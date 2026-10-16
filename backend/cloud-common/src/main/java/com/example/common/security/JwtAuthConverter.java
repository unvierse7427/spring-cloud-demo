package com.example.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 把 JWT 中的 authorities 声明转换为 Spring Security 的权限集合。
 * 该声明同时包含角色（ROLE_ADMIN）与权限码（user:create），
 * 因此 hasRole("ADMIN") 与 hasAuthority("user:create") 均可使用。
 */
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    /** JWT 中承载权限的声明名 */
    public static final String AUTHORITIES_CLAIM = "authorities";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        List<String> values = jwt.getClaimAsStringList(AUTHORITIES_CLAIM);
        if (values != null) {
            for (String value : values) {
                authorities.add(new SimpleGrantedAuthority(value));
            }
        }
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
