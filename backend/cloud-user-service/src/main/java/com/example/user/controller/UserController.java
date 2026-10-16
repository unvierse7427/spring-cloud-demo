package com.example.user.controller;

import com.example.common.result.Result;
import com.example.user.entity.SysUser;
import com.example.user.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户接口
 * 对外访问路径：网关 /api/user/** -> 本服务 /user/**
 * 网关统一鉴权后，这里再按权限码做方法级校验
 */
@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('user:list')")
    public Result<List<SysUser>> list(@RequestParam(required = false) String keyword) {
        return Result.success(userService.list(keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:list')")
    public Result<SysUser> detail(@PathVariable Long id) {
        SysUser user = userService.getById(id);
        return user == null ? Result.fail(404, "用户不存在：" + id) : Result.success(user);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user:create')")
    public Result<SysUser> create(@RequestBody SysUser user) {
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            return Result.fail(400, "用户名不能为空");
        }
        return Result.success("新增成功", userService.create(user));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('user:delete')")
    public Result<Void> remove(@PathVariable Long id) {
        return userService.remove(id) ? Result.success() : Result.fail(404, "用户不存在：" + id);
    }
}
