package com.example.rag.dto;

/**
 * 可选的角色项，用于上传文档时选择可见角色
 */
public class RoleOption {

    private String roleCode;

    private String roleName;

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }
}
