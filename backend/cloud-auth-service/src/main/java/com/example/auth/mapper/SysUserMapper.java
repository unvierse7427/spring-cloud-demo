package com.example.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.auth.entity.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT * FROM sys_user WHERE username = #{username} LIMIT 1")
    SysUser selectByUsername(@Param("username") String username);

    @Select("SELECT r.role_code FROM sys_role r "
            + "INNER JOIN sys_user_role ur ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{userId}")
    List<String> selectRoleCodes(@Param("userId") Long userId);

    @Select("SELECT DISTINCT p.perm_code FROM sys_permission p "
            + "INNER JOIN sys_role_permission rp ON rp.permission_id = p.id "
            + "INNER JOIN sys_user_role ur ON ur.role_id = rp.role_id "
            + "WHERE ur.user_id = #{userId}")
    List<String> selectPermCodes(@Param("userId") Long userId);
}
