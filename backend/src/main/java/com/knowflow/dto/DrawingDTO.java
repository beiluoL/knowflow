package com.knowflow.dto;

import lombok.Data;

/**
 * 绘图新增/更新入参。data 为前端整图 JSON（vue-flow 的 nodes/edges），后端原样持久化。
 */
@Data
public class DrawingDTO {

    /** 标题（创建/更新均可传，更新时留空则不改动标题） */
    private String title;

    /** 图类型（flowchart 等），可空，默认 flowchart */
    private String type;

    /** 整图数据（JSON 对象）：{ nodes, edges }；留空则保留原数据 */
    private Object data;
}
