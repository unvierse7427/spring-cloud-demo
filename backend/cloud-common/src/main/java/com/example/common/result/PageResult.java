package com.example.common.result;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 统一分页结果
 *
 * @param <T> 记录类型
 */
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总记录数 */
    private long total;

    /** 当前页数据 */
    private List<T> records;

    public PageResult() {
        this(0L, Collections.emptyList());
    }

    public PageResult(long total, List<T> records) {
        this.total = total;
        this.records = records == null ? Collections.emptyList() : records;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }
}
