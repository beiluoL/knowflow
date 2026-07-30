package com.knowflow.vo;

import lombok.Data;

import java.util.List;

/** 实体关系知识图谱：AI 抽取实体+关系构建的真正知识图谱。 */
@Data
public class EntityGraphVO {
    /** 实体节点列表。 */
    private List<EntityNodeVO> nodes;
    /** 实体关系边列表。 */
    private List<EntityEdgeVO> edges;
    /** 生成时间（ISO 格式）。 */
    private String generatedAt;
}
