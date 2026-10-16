package com.example.user.service;

import com.example.common.log.LoginLog;
import com.example.common.log.OperationLog;
import com.example.common.redis.OnlineUser;
import com.example.common.result.PageResult;
import com.example.user.entity.SysPermission;
import com.example.user.entity.SysRole;
import com.example.user.entity.SysUser;

import java.util.List;

/**
 * 后台管理业务接口：用户状态、角色权限、日志与在线用户
 */
public interface AdminService {

    /** 分页查询用户，可按关键字与状态过滤 */
    PageResult<SysUser> pageUsers(String keyword, Integer status, long page, long size);

    /** 启用/禁用用户 */
    void updateUserStatus(Long userId, Integer status);

    /** 查询用户已绑定的角色 id */
    List<Long> listUserRoleIds(Long userId);

    /** 覆盖式设置用户角色 */
    void assignUserRoles(Long userId, List<Long> roleIds);

    /** 查询全部角色 */
    List<SysRole> listRoles();

    /** 新增角色 */
    SysRole createRole(SysRole role);

    /** 修改角色 */
    SysRole updateRole(Long id, SysRole role);

    /** 删除角色（连带清理角色权限关联） */
    void removeRole(Long id);

    /** 查询角色已绑定的权限 id */
    List<Long> listRolePermissionIds(Long roleId);

    /** 覆盖式设置角色权限 */
    void assignRolePermissions(Long roleId, List<Long> permissionIds);

    /** 查询全部权限 */
    List<SysPermission> listPermissions();

    /** 分页查询登录日志 */
    PageResult<LoginLog> pageLoginLogs(String username, String loginType, Integer status, long page, long size);

    /** 分页查询接口调用日志 */
    PageResult<OperationLog> pageOperationLogs(String username, String requestUri, Integer success, long page, long size);

    /** 查询 Redis 中的在线用户 */
    List<OnlineUser> listOnlineUsers();
}
