package com.knowflow.vo;

import lombok.Data;

/**
 * 知识图谱节点视图对象。
 */
@Data
public class GraphNodeVO {

    /** 节点唯一标识，如 cat-1 / doc-2。 */
    private String id;

    /** 节点展示名称。 */
    private String label;

    /** 节点类型：category（分类）/ doc（文档）。 */
    private String type;

    /** 节点权重（分类为文档数，文档为浏览量），用于布局与展示。 */
    private Integer value;
}
