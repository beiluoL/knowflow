package com.knowflow.dto;

import com.knowflow.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文档列表分页查询条件，支持关键字、分类、难度与状态过滤。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DocQueryDTO extends PageQuery {

    private String keyword;

    private Long categoryId;

    /** 难度等级编码（具体以枚举为准） */
    private Integer difficulty;

    /** 文档状态编码（具体以枚举为准） */
    private Integer status;

    /**
     * 排序方式：relevance（相关度，仅在有 keyword 时生效）/ time（最新）/ view（最热）。
     * 缺省时：有关键词按相关度，无关键词按时间。
     */
    private String sort;
}
