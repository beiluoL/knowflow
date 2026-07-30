package com.knowflow.vo;

import lombok.Data;

/**
 * 技术栈节点视图对象。
 */
@Data
public class TechNodeVO {

    /** 节点唯一标识，如 tech-1 / cat-java。 */
    private String id;

    /** 技术名称，如 "Java"、"Spring Boot"、"MySQL"。 */
    private String name;

    /** 技术分类：LANGUAGE / FRAMEWORK / TOOL / DATABASE / ALGORITHM / PLATFORM。 */
    private String category;

    /** 分类中文名。 */
    private String categoryLabel;

    /** 简短说明。 */
    private String description;

    /** 难度等级：1入门 / 2中等 / 3进阶。 */
    private Integer difficulty;

    /** 关联知识库文档数。 */
    private Integer docCount;
}
