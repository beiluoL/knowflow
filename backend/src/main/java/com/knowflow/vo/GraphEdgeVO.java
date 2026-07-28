package com.knowflow.vo;

import lombok.Data;

/**
 * 知识图谱边视图对象，描述节点间关系。
 */
@Data
public class GraphEdgeVO {

    /** 起始节点 id。 */
    private String source;

    /** 目标节点 id。 */
    private String target;

    /** 关系类型：parent（父子分类）/ contains（分类包含文档）。 */
    private String relation;
}
