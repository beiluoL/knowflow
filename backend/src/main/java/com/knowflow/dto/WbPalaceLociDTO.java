package com.knowflow.dto;

import lombok.Data;

/**
 * 记忆宫殿位点（知识复习扩展）新增/编辑入参。
 */
@Data
public class WbPalaceLociDTO {

    /** 宫殿ID */
    private Long palaceId;

    /** 关联收集箱条目ID */
    private Long captureId;

    /** 关联康奈尔笔记ID */
    private Long noteId;

    /** 归属知识库/分类ID（逻辑外键 doc_category.id） */
    private Long categoryId;

    /** 位点名称 */
    private String name;

    /** 绑定的知识点内容 */
    private String knowledgePoint;

    /** 联想图像描述 */
    private String imageHint;

    /** 位点图标名 */
    private String icon;

    /** 画布横向百分比坐标（0~100） */
    private Integer posX;

    /** 画布纵向百分比坐标（0~100） */
    private Integer posY;

    /** 漫游顺序 */
    private Integer sortOrder;
}
