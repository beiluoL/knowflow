package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库工作台·记忆宫殿（知识复习模块扩展）。
 * 宫殿 = 一个熟悉的空间场景，用于挂载多个「位点」以空间位置关联知识点。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wb_palace")
public class WbPalace extends BaseEntity {

    /** 所属用户ID */
    private Long userId;

    /** 宫殿名称，如「我的书房」 */
    private String name;

    /** 场景描述 */
    private String description;

    /** 场景主题：ROOM 房间 / STREET 街道 / CAMPUS 校园 / CUSTOM 自定义 */
    private String theme;

    /** 封面主题色（十六进制） */
    private String coverColor;

    /** 归属知识库/分类ID（逻辑外键 doc_category.id） */
    private Long categoryId;
}
