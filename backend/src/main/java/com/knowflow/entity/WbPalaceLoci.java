package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库工作台·记忆宫殿位点（知识复习模块扩展）。
 * 位点 = 宫殿中的具体位置，绑定一个知识点；pos_x/pos_y 为画布百分比坐标，供拖拽编辑。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wb_palace_loci")
public class WbPalaceLoci extends BaseEntity {

    /** 所属用户ID */
    private Long userId;

    /** 所属宫殿ID（逻辑外键 wb_palace.id） */
    private Long palaceId;

    /** 关联收集箱条目ID（逻辑外键 wb_capture.id） */
    private Long captureId;

    /** 关联康奈尔笔记ID（逻辑外键 wb_note.id） */
    private Long noteId;

    /** 归属知识库/分类ID（逻辑外键 doc_category.id，与宫殿共享可选） */
    private Long categoryId;

    /** 位点名称，如「书桌左上角」 */
    private String name;

    /** 绑定的知识点内容 */
    private String knowledgePoint;

    /** 联想图像描述（越夸张越好记） */
    private String imageHint;

    /** 位点图标名（Icon 组件图标） */
    private String icon;

    /** 画布横向百分比坐标（0~100） */
    private Integer posX;

    /** 画布纵向百分比坐标（0~100） */
    private Integer posY;

    /** 漫游顺序（记忆宫殿按固定路线回忆） */
    private Integer sortOrder;
}
