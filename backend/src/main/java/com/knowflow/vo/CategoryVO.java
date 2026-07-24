package com.knowflow.vo;

import lombok.Data;

import java.util.List;

@Data
public class CategoryVO {

    private Long id;

    private String name;

    private String code;

    private Long parentId;

    private String icon;

    private String description;

    private Integer sortOrder;

    private Integer docCount;

    private List<CategoryVO> children;
}
