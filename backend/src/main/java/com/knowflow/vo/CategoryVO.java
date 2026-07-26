package com.knowflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 分类视图对象，支持树形结构（含子分类）与文档计数。
 */
@Data
public class CategoryVO {

    private Long id;

    private String name;

    /** 分类编码 */
    private String code;

    /** 父分类ID，顶级分类为 null */
    private Long parentId;

    private String icon;

    private String description;

    private Integer sortOrder;

    private Integer docCount;

    private List<CategoryVO> children;
}
