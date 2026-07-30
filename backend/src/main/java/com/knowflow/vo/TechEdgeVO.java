package com.knowflow.vo;

import lombok.Data;

/**
 * 技术栈依赖边视图对象。
 */
@Data
public class TechEdgeVO {

    /** 前置技术节点 ID。 */
    private String source;

    /** 目标技术节点 ID。 */
    private String target;

    /** 依赖类型：PREREQUISITE（前置知识）/ COMPONENT（组件关系）/ DEPENDS（依赖）。 */
    private String relation;

    /** 依赖强度：1弱 / 2中 / 3强。 */
    private Integer strength;

    /** 依赖说明。 */
    private String description;
}
