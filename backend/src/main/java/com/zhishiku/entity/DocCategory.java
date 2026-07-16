package com.zhishiku.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("doc_category")
public class DocCategory extends BaseEntity {

    private String name;

    private String code;

    private Long parentId;

    private String icon;

    private String description;

    private Integer sortOrder;

    private Integer docCount;

    private Integer status;
}
