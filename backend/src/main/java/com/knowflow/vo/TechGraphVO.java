package com.knowflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 技术栈依赖图谱视图对象。
 * 描述某一技术领域内各技术栈（语言/框架/工具/数据库/算法）的依赖关系。
 */
@Data
public class TechGraphVO {

    /** 技术领域主题，如 "Spring Boot"、"Python 全栈"、"机器学习"。 */
    private String topic;

    /** AI 生成时间。 */
    private String generatedAt;

    /** 技术节点列表。 */
    private List<TechNodeVO> nodes;

    /** 依赖关系边列表。 */
    private List<TechEdgeVO> edges;
}
