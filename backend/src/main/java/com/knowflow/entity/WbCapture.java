package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库工作台·收集箱（知识输入模块）：快速捕获入口。
 * 手动摘录、网页剪藏、文档划线、AI 生成均落到此表，经整理后派生笔记/卡片/故事。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wb_capture")
public class WbCapture extends BaseEntity {

    /** 所属用户ID */
    private Long userId;

    /** 标题/一句话摘要 */
    private String title;

    /** 正文内容（Markdown） */
    private String content;

    /** 来源：MANUAL 手记 / DOC 文档 / WEB 网页 / AI 生成 / IMPORT 导入 */
    private String sourceType;

    /** 来源链接（网页剪藏时使用） */
    private String sourceUrl;

    /** 来源文档ID（逻辑外键 doc_document.id） */
    private Long docId;

    /** 归属知识库/分类ID（逻辑外键 doc_category.id） */
    private Long categoryId;

    /** 逗号分隔标签 */
    private String tags;

    /** 流转状态：INBOX 待整理 / PROCESSED 已整理 / ARCHIVED 已归档 */
    private String status;

    /** 是否标星：1 是 / 0 否 */
    private Integer starred;
}
