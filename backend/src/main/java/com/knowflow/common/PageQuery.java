package com.knowflow.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 分页查询参数封装：提供页码/页大小归一化，防止非法或过大的分页参数。
 */
@Data
public class PageQuery implements Serializable {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    private String orderBy;

    private String orderDirection = "desc";

    /**
     * 页码归一化：为空或小于 1 时回退为第 1 页。
     */
    public Integer getPageNum() {
        if (pageNum == null || pageNum < 1) {
            return 1;
        }
        return pageNum;
    }

    /**
     * 页大小归一化：为空或小于 1 时回退为 10，且上限封顶 100 防止一次拉取过多。
     */
    public Integer getPageSize() {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }

    /**
     * F-09/F-13 修复：供未使用 PageQuery 对象的接口统一归一化分页参数。
     */
    public static int normalizePageNum(Integer pageNum) {
        return (pageNum == null || pageNum < 1) ? 1 : pageNum;
    }

    public static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }
}
