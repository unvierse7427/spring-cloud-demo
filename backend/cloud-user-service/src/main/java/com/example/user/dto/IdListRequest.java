package com.example.user.dto;

import java.io.Serializable;
import java.util.List;

/**
 * 批量 id 提交体，用于用户角色、角色权限等关联关系的整体覆盖
 */
public class IdListRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<Long> ids;

    public List<Long> getIds() {
        return ids;
    }

    public void setIds(List<Long> ids) {
        this.ids = ids;
    }
}
