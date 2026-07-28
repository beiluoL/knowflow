package com.knowflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 知识图谱视图对象，包含节点集合与边集合。
 */
@Data
public class KnowledgeGraphVO {

    private List<GraphNodeVO> nodes;

    private List<GraphEdgeVO> edges;
}
