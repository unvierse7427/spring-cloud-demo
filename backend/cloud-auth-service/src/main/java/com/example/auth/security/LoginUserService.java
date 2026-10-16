package com.example.auth.security;

import com.example.auth.entity.SysUser;
import com.example.auth.mapper.SysUserMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 从数据库加载用户、角色与权限
 */
@Service
public class LoginUserService implements UserDetailsService {

    private final SysUserMapper sysUserMapper;

    public LoginUserService(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = sysUserMapper.selectByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在：" + username);
        }
        return new LoginUser(user, loadAuthorities(user.getId()));
    }

    /**
     * 加载用户的角色标识与权限码
     */
    public List<String> loadAuthorities(Long userId) {
        List<String> authorities = new ArrayList<>();
        authorities.addAll(sysUserMapper.selectRoleCodes(userId));
        authorities.addAll(sysUserMapper.selectPermCodes(userId));
        return authorities;
    }
}
