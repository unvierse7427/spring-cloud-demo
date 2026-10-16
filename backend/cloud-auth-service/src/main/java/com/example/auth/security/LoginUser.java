package com.example.auth.security;

import com.example.auth.entity.SysUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 登录用户信息，同时持有角色与权限码
 */
public class LoginUser implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final SysUser user;

    private final List<String> authorities;

    public LoginUser(SysUser user, List<String> authorities) {
        this.user = user;
        this.authorities = authorities;
    }

    public SysUser getUser() {
        return user;
    }

    /**
     * 全部权限标识：角色形如 ROLE_ADMIN，权限码形如 user:create
     */
    public List<String> getAuthorityValues() {
        return authorities;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.getStatus() != null && user.getStatus() == 1;
    }
}
