package com.example.user.service;

import com.example.user.entity.SysUser;

import java.util.List;

/**
 * 用户业务接口
 */
public interface UserService {

    /**
     * 查询用户列表，keyword 为空时返回全部
     */
    List<SysUser> list(String keyword);

    /**
     * 按主键查询，不存在返回 null
     */
    SysUser getById(Long id);

    /**
     * 新增用户并返回落库后的数据
     */
    SysUser create(SysUser user);

    /**
     * 按主键删除，返回是否删除成功
     */
    boolean remove(Long id);
}
