package com.knowflow.dto;

import lombok.Data;

/**
 * 记忆宫殿（知识复习扩展）新增/编辑入参。
 */
@Data
public class WbPalaceDTO {

    /** 宫殿名称 */
    private String name;

    /** 场景描述 */
    private String description;

    /** 场景主题：ROOM/STREET/CAMPUS/CUSTOM */
    private String theme;

    /** 封面主题色（十六进制） */
    private String coverColor;

    /** 归属知识库/分类ID */
    private Long categoryId;
}
