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
}
