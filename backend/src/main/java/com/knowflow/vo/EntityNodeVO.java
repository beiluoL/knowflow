package com.knowflow.vo;

import lombok.Data;

import java.util.List;

/** 知识实体图谱节点（前端渲染用）。 */
@Data
public class EntityNodeVO {
    /** 实体ID。 */
    private Long id;
    /** 实体名称。 */
    private String name;
    /** 实体类型：CONCEPT/TECHNIQUE/TERM/PRINCIPLE/TOOL/OTHER。 */
    private String type;
    /** 实体说明。 */
    private String description;
    /** 所属分类ID。 */
    private Long categoryId;
    /** 所属分类名称。 */
    private String categoryName;
    /** 重要度/被抽取次数。 */
    private Integer weight;
}
