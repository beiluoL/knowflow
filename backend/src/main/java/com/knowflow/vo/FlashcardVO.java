package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户级闪卡视图对象：支持来源溯源、间隔重复复习与导入/导出标识。
 */
@Data
public class FlashcardVO {

    private Long id;

    /** 所属用户（仅管理端可看到） */
    private Long userId;

    private Long pathId;

    private Long chapterId;

    /** 所属知识库/分类 ID */
    private Long categoryId;

    /** 知识库名称 */
    private String categoryName;

    /** 来源文档 ID */
    private Long docId;

    /** 文档标题 */
    private String docTitle;

    /** 正面问题 */
    private String front;

    /** 背面答案 */
    private String back;

    /** 自定义分类标签（兼容旧字段） */
    private String category;

    /** 难度：1简单 / 2中等 / 3困难 */
    private Integer difficulty;

    /** 逗号分隔自定义标签 */
    private String tags;

    /** 来源：MANUAL/AI_DOC/AI_KB/IMPORT */
    private String sourceType;

    /** 复习次数 */
    private Integer reviewCount;

    /** 当前复习间隔（天） */
    private Integer reviewInterval;

    /** 下次复习时间 */
    private LocalDateTime nextReviewTime;

    /** 上次复习时间 */
    private LocalDateTime lastReviewTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
