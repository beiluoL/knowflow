package com.knowflow.dto;

import lombok.Data;

/**
 * 思维导图新增/更新入参。data 为前端整图 JSON（节点/连线/视图），后端原样持久化。
 */
@Data
public class MindMapDTO {

    /** 标题（创建/更新均可传，更新时留空则不改动标题） */
    private String title;

    /** 整图数据（JSON 对象）：{ nodes, edges, view }；留空则保留原数据 */
    private Object data;
}
