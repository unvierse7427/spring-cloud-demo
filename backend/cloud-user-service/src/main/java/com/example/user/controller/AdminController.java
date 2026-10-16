package com.example.user.controller;

import com.example.common.log.LoginLog;
import com.example.common.log.OperationLog;
import com.example.common.redis.OnlineUser;
import com.example.common.result.PageResult;
import com.example.common.result.Result;
import com.example.user.dto.IdListRequest;
import com.example.user.dto.StatusRequest;
import com.example.user.entity.SysPermission;
import com.example.user.entity.SysRole;
import com.example.user.entity.SysUser;
import com.example.user.service.AdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台管理接口
 *
 * <p>对外访问路径：网关 /api/admin/** -> 本服务 /admin/**。
 * 整个控制器只对拥有 ROLE_ADMIN 的账号开放。
 */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // ------------------------------ 用户与状态 ------------------------------

    @GetMapping("/user/page")
    public Result<PageResult<SysUser>> pageUsers(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Integer status,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size) {
        return Result.success(adminService.pageUsers(keyword, status, page, size));
    }

    @PutMapping("/user/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id, @RequestBody StatusRequest request) {
        adminService.updateUserStatus(id, request.getStatus());
        return Result.success("状态已更新", null);
    }

    @GetMapping("/user/{id}/roles")
    public Result<List<Long>> userRoleIds(@PathVariable Long id) {
        return Result.success(adminService.listUserRoleIds(id));
    }

    @PutMapping("/user/{id}/roles")
    public Result<Void> assignUserRoles(@PathVariable Long id, @RequestBody IdListRequest request) {
        adminService.assignUserRoles(id, request.getIds());
        return Result.success("角色已更新", null);
    }

    // ------------------------------ 角色与权限 ------------------------------

    @GetMapping("/role/list")
    public Result<List<SysRole>> listRoles() {
        return Result.success(adminService.listRoles());
    }

    @PostMapping("/role")
    public Result<SysRole> createRole(@RequestBody SysRole role) {
        return Result.success("新增成功", adminService.createRole(role));
    }

    @PutMapping("/role/{id}")
    public Result<SysRole> updateRole(@PathVariable Long id, @RequestBody SysRole role) {
        return Result.success("修改成功", adminService.updateRole(id, role));
    }

    @DeleteMapping("/role/{id}")
    public Result<Void> removeRole(@PathVariable Long id) {
        adminService.removeRole(id);
        return Result.success("删除成功", null);
    }

    @GetMapping("/role/{id}/permissions")
    public Result<List<Long>> rolePermissionIds(@PathVariable Long id) {
        return Result.success(adminService.listRolePermissionIds(id));
    }

    @PutMapping("/role/{id}/permissions")
    public Result<Void> assignRolePermissions(@PathVariable Long id, @RequestBody IdListRequest request) {
        adminService.assignRolePermissions(id, request.getIds());
        return Result.success("权限已更新", null);
    }

    @GetMapping("/permission/list")
    public Result<List<SysPermission>> listPermissions() {
        return Result.success(adminService.listPermissions());
    }

    // ------------------------------ 日志与在线用户 ------------------------------

    @GetMapping("/log/login")
    public Result<PageResult<LoginLog>> pageLoginLogs(@RequestParam(required = false) String username,
                                                      @RequestParam(required = false) String loginType,
                                                      @RequestParam(required = false) Integer status,
                                                      @RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "10") long size) {
        return Result.success(adminService.pageLoginLogs(username, loginType, status, page, size));
    }

    @GetMapping("/log/operation")
    public Result<PageResult<OperationLog>> pageOperationLogs(@RequestParam(required = false) String username,
                                                              @RequestParam(required = false) String requestUri,
                                                              @RequestParam(required = false) Integer success,
                                                              @RequestParam(defaultValue = "1") long page,
                                                              @RequestParam(defaultValue = "10") long size) {
        return Result.success(adminService.pageOperationLogs(username, requestUri, success, page, size));
    }

    @GetMapping("/online")
    public Result<List<OnlineUser>> listOnlineUsers() {
        return Result.success(adminService.listOnlineUsers());
    }
}
