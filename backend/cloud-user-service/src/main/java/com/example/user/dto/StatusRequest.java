package com.example.user.dto;

import java.io.Serializable;

/**
 * 用户状态变更提交体
 */
public class StatusRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 1 启用，0 禁用 */
    private Integer status;

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
