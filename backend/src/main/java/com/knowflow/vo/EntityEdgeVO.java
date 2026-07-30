package com.knowflow.vo;

import lombok.Data;

/** 知识实体图谱关系边（前端渲染用）。 */
@Data
public class EntityEdgeVO {
    /** 关系ID。 */
    private Long id;
    /** 源实体ID。 */
    private Long source;
    /** 目标实体ID。 */
    private Long target;
    /** 关系类型：RELATED_TO/PREREQUISITE/IS_A/PART_OF/USES/CONTRASTS。 */
    private String relation;
    /** 关系说明。 */
    private String description;
    /** 抽取来源文档ID。 */
    private Long docId;
}
