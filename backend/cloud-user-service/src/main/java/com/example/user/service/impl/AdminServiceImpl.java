package com.example.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.common.log.LoginLog;
import com.example.common.log.OperationLog;
import com.example.common.log.mapper.LoginLogMapper;
import com.example.common.log.mapper.OperationLogMapper;
import com.example.common.redis.OnlineUser;
import com.example.common.redis.OnlineUserStore;
import com.example.common.result.PageResult;
import com.example.user.entity.SysPermission;
import com.example.user.entity.SysRole;
import com.example.user.entity.SysRolePermission;
import com.example.user.entity.SysUser;
import com.example.user.entity.SysUserRole;
import com.example.user.mapper.SysPermissionMapper;
import com.example.user.mapper.SysRoleMapper;
import com.example.user.mapper.SysRolePermissionMapper;
import com.example.user.mapper.SysUserMapper;
import com.example.user.mapper.SysUserRoleMapper;
import com.example.user.service.AdminService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 后台管理业务实现
 */
@Service
public class AdminServiceImpl implements AdminService {

    /** 内置管理员账号 id，不允许禁用 */
    private static final Long ADMIN_USER_ID = 1L;

    /** 内置管理员角色标识，不允许删除或改名 */
    private static final String ADMIN_ROLE_CODE = "ROLE_ADMIN";

    /** 单页最大条数，避免前端传入过大的 size */
    private static final long MAX_PAGE_SIZE = 100L;

    private final SysUserMapper sysUserMapper;

    private final SysRoleMapper sysRoleMapper;

    private final SysPermissionMapper sysPermissionMapper;

    private final SysUserRoleMapper sysUserRoleMapper;

    private final SysRolePermissionMapper sysRolePermissionMapper;

    private final LoginLogMapper loginLogMapper;

    private final OperationLogMapper operationLogMapper;

    private final OnlineUserStore onlineUserStore;

    public AdminServiceImpl(SysUserMapper sysUserMapper,
                            SysRoleMapper sysRoleMapper,
                            SysPermissionMapper sysPermissionMapper,
                            SysUserRoleMapper sysUserRoleMapper,
                            SysRolePermissionMapper sysRolePermissionMapper,
                            LoginLogMapper loginLogMapper,
                            OperationLogMapper operationLogMapper,
                            OnlineUserStore onlineUserStore) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysPermissionMapper = sysPermissionMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysRolePermissionMapper = sysRolePermissionMapper;
        this.loginLogMapper = loginLogMapper;
        this.operationLogMapper = operationLogMapper;
        this.onlineUserStore = onlineUserStore;
    }

    // ------------------------------------------------------------------
    // 用户与状态
    // ------------------------------------------------------------------

    @Override
    public PageResult<SysUser> pageUsers(String keyword, Integer status, long page, long size) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(condition -> condition
                    .like(SysUser::getUsername, value)
                    .or()
                    .like(SysUser::getNickname, value)
                    .or()
                    .like(SysUser::getEmail, value));
        }
        wrapper.eq(status != null, SysUser::getStatus, status);
        wrapper.orderByAsc(SysUser::getId);
        Page<SysUser> result = sysUserMapper.selectPage(toPage(page, size), wrapper);
        return new PageResult<>(result.getTotal(), result.getRecords());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatus(Long userId, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new IllegalArgumentException("状态值只能是 0（禁用）或 1（启用）");
        }
        SysUser existing = sysUserMapper.selectById(userId);
        if (existing == null) {
            throw new IllegalArgumentException("用户不存在：" + userId);
        }
        if (ADMIN_USER_ID.equals(userId) && status == 0) {
            throw new IllegalArgumentException("内置管理员账号不允许禁用");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setStatus(status);
        sysUserMapper.updateById(update);
    }

    @Override
    public List<Long> listUserRoleIds(Long userId) {
        List<SysUserRole> relations = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        List<Long> roleIds = new ArrayList<>(relations.size());
        for (SysUserRole relation : relations) {
            roleIds.add(relation.getRoleId());
        }
        return roleIds;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignUserRoles(Long userId, List<Long> roleIds) {
        if (sysUserMapper.selectById(userId) == null) {
            throw new IllegalArgumentException("用户不存在：" + userId);
        }
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        for (Long roleId : distinct(roleIds)) {
            if (sysRoleMapper.selectById(roleId) == null) {
                throw new IllegalArgumentException("角色不存在：" + roleId);
            }
            sysUserRoleMapper.insert(new SysUserRole(userId, roleId));
        }
    }

    // ------------------------------------------------------------------
    // 角色与权限
    // ------------------------------------------------------------------

    @Override
    public List<SysRole> listRoles() {
        return sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysRole createRole(SysRole role) {
        if (!StringUtils.hasText(role.getRoleCode())) {
            throw new IllegalArgumentException("角色标识不能为空");
        }
        if (!StringUtils.hasText(role.getRoleName())) {
            throw new IllegalArgumentException("角色名称不能为空");
        }
        String roleCode = role.getRoleCode().trim();
        Long duplicated = sysRoleMapper.selectCount(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, roleCode));
        if (duplicated != null && duplicated > 0) {
            throw new IllegalArgumentException("角色标识已存在：" + roleCode);
        }
        SysRole entity = new SysRole();
        entity.setRoleCode(roleCode);
        entity.setRoleName(role.getRoleName().trim());
        entity.setDescription(role.getDescription());
        sysRoleMapper.insert(entity);
        return sysRoleMapper.selectById(entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysRole updateRole(Long id, SysRole role) {
        SysRole existing = sysRoleMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("角色不存在：" + id);
        }
        boolean builtIn = ADMIN_ROLE_CODE.equals(existing.getRoleCode());
        if (StringUtils.hasText(role.getRoleCode()) && !role.getRoleCode().trim().equals(existing.getRoleCode())) {
            if (builtIn) {
                throw new IllegalArgumentException("内置管理员角色的标识不允许修改");
            }
            String roleCode = role.getRoleCode().trim();
            Long duplicated = sysRoleMapper.selectCount(
                    new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, roleCode));
            if (duplicated != null && duplicated > 0) {
                throw new IllegalArgumentException("角色标识已存在：" + roleCode);
            }
            existing.setRoleCode(roleCode);
        }
        if (StringUtils.hasText(role.getRoleName())) {
            existing.setRoleName(role.getRoleName().trim());
        }
        if (role.getDescription() != null) {
            existing.setDescription(role.getDescription());
        }
        // 交给数据库的 ON UPDATE CURRENT_TIMESTAMP 维护更新时间
        existing.setCreateTime(null);
        existing.setUpdateTime(null);
        sysRoleMapper.updateById(existing);
        return sysRoleMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeRole(Long id) {
        SysRole existing = sysRoleMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("角色不存在：" + id);
        }
        if (ADMIN_ROLE_CODE.equals(existing.getRoleCode())) {
            throw new IllegalArgumentException("内置管理员角色不允许删除");
        }
        Long bound = sysUserRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        if (bound != null && bound > 0) {
            throw new IllegalArgumentException("该角色已分配给 " + bound + " 个用户，请先解除关联");
        }
        sysRolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, id));
        sysRoleMapper.deleteById(id);
    }

    @Override
    public List<Long> listRolePermissionIds(Long roleId) {
        List<SysRolePermission> relations = sysRolePermissionMapper.selectList(
                new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, roleId));
        List<Long> permissionIds = new ArrayList<>(relations.size());
        for (SysRolePermission relation : relations) {
            permissionIds.add(relation.getPermissionId());
        }
        return permissionIds;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRolePermissions(Long roleId, List<Long> permissionIds) {
        if (sysRoleMapper.selectById(roleId) == null) {
            throw new IllegalArgumentException("角色不存在：" + roleId);
        }
        sysRolePermissionMapper.delete(
                new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, roleId));
        for (Long permissionId : distinct(permissionIds)) {
            if (sysPermissionMapper.selectById(permissionId) == null) {
                throw new IllegalArgumentException("权限不存在：" + permissionId);
            }
            sysRolePermissionMapper.insert(new SysRolePermission(roleId, permissionId));
        }
    }

    @Override
    public List<SysPermission> listPermissions() {
        return sysPermissionMapper.selectList(
                new LambdaQueryWrapper<SysPermission>().orderByAsc(SysPermission::getId));
    }

    // ------------------------------------------------------------------
    // 日志与在线用户
    // ------------------------------------------------------------------

    @Override
    public PageResult<LoginLog> pageLoginLogs(String username, String loginType, Integer status,
                                              long page, long size) {
        LambdaQueryWrapper<LoginLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(username), LoginLog::getUsername,
                username == null ? null : username.trim());
        wrapper.eq(StringUtils.hasText(loginType), LoginLog::getLoginType, loginType);
        wrapper.eq(status != null, LoginLog::getStatus, status);
        wrapper.orderByDesc(LoginLog::getId);
        Page<LoginLog> result = loginLogMapper.selectPage(toPage(page, size), wrapper);
        return new PageResult<>(result.getTotal(), result.getRecords());
    }

    @Override
    public PageResult<OperationLog> pageOperationLogs(String username, String requestUri, Integer success,
                                                      long page, long size) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(username), OperationLog::getUsername,
                username == null ? null : username.trim());
        wrapper.like(StringUtils.hasText(requestUri), OperationLog::getRequestUri,
                requestUri == null ? null : requestUri.trim());
        wrapper.eq(success != null, OperationLog::getSuccess, success);
        wrapper.orderByDesc(OperationLog::getId);
        Page<OperationLog> result = operationLogMapper.selectPage(toPage(page, size), wrapper);
        return new PageResult<>(result.getTotal(), result.getRecords());
    }

    @Override
    public List<OnlineUser> listOnlineUsers() {
        return onlineUserStore.listOnlineUsers();
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private <T> Page<T> toPage(long page, long size) {
        long pageNo = page < 1 ? 1 : page;
        long pageSize = size < 1 ? 10 : Math.min(size, MAX_PAGE_SIZE);
        return new Page<>(pageNo, pageSize);
    }

    private Set<Long> distinct(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> values = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) {
                values.add(id);
            }
        }
        return values;
    }
}
