package com.example.rag.mapper;

import com.example.rag.dto.RoleOption;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 只读查询系统角色表，供上传文档时选择可见角色
 */
@Mapper
public interface SysRoleMapper {

    @Select("SELECT role_code AS roleCode, role_name AS roleName FROM sys_role ORDER BY id")
    List<RoleOption> listOptions();
}
