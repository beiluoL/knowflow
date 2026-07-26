package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("doc_category")
/** 文档分类实体，支持树形结构（parentId 关联父分类）。 */
public class DocCategory extends BaseEntity {

    private String name;

    /** 分类编码，唯一英文短码，用于程序识别。 */
    private String code;

    /** 父分类 ID，顶级分类为 0 或 null。 */
    private Long parentId;

    private String icon;

    private String description;

    private Integer sortOrder;

    private Integer docCount;

    /** 状态位，如 0 禁用 / 1 启用。 */
    private Integer status;
}
