package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库工作台·康奈尔笔记（知识整理模块）。
 * 三分区结构：线索栏(cue)/笔记栏(note)/总结栏(summary)，支持主动回忆自测。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wb_note")
public class WbNote extends BaseEntity {

    /** 所属用户ID */
    private Long userId;

    /** 来源收集箱条目ID（逻辑外键 wb_capture.id） */
    private Long captureId;

    /** 归属知识库/分类ID（逻辑外键 doc_category.id） */
    private Long categoryId;

    /** 笔记标题 */
    private String title;

    /** 康奈尔-线索栏：关键问题/关键词，用于主动回忆自测 */
    private String cueColumn;

    /** 康奈尔-笔记栏：课堂/阅读主体内容 */
    private String noteColumn;

    /** 康奈尔-总结栏：用自己的话概括 */
    private String summaryColumn;

    /** 逗号分隔标签 */
    private String tags;

    /** 掌握度自评：0~100 */
    private Integer mastery;
}
