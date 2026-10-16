package com.example.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.user.entity.SysUser;
import com.example.user.mapper.SysUserMapper;
import com.example.user.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户业务实现，基于 MyBatis-Plus 访问 sys_user 表
 */
@Service
public class UserServiceImpl implements UserService {

    /** 新增用户未指定密码时使用的默认密码 */
    private static final String DEFAULT_PASSWORD = "123456";

    /** 内置管理员账号 id，不允许删除 */
    private static final Long ADMIN_ID = 1L;

    private final SysUserMapper sysUserMapper;

    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public List<SysUser> list(String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(condition -> condition
                    .like(SysUser::getUsername, value)
                    .or()
                    .like(SysUser::getNickname, value));
        }
        wrapper.orderByAsc(SysUser::getId);
        return sysUserMapper.selectList(wrapper);
    }

    @Override
    public SysUser getById(Long id) {
        return sysUserMapper.selectById(id);
    }

    @Override
    public SysUser create(SysUser user) {
        SysUser existing = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, user.getUsername())
                .last("LIMIT 1"));
        if (existing != null) {
            throw new IllegalArgumentException("用户名已存在：" + user.getUsername());
        }
        user.setId(null);
        String rawPassword = StringUtils.hasText(user.getPassword()) ? user.getPassword() : DEFAULT_PASSWORD;
        user.setPassword(passwordEncoder.encode(rawPassword));
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        sysUserMapper.insert(user);
        return sysUserMapper.selectById(user.getId());
    }

    @Override
    public boolean remove(Long id) {
        if (ADMIN_ID.equals(id)) {
            throw new IllegalArgumentException("内置管理员账号不允许删除");
        }
        return sysUserMapper.deleteById(id) > 0;
    }
}
