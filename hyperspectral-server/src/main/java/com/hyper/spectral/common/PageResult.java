package com.hyper.spectral.common;

import java.util.List;

/**
 * 列表接口统一分页壳，当前阶段即使只有单页也保持稳定结构。
 */
public class PageResult<T> {

    private List<T> records;
    private long total;
    private int pageNo;
    private int pageSize;

    public PageResult() {
    }

    public PageResult(List<T> records, long total, int pageNo, int pageSize) {
        this.records = records;
        this.total = total;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
    }

    public static <T> PageResult<T> of(List<T> records, int pageNo, int pageSize) {
        return new PageResult<>(records, records.size(), pageNo, pageSize);
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPageNo() {
        return pageNo;
    }

    public void setPageNo(int pageNo) {
        this.pageNo = pageNo;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
