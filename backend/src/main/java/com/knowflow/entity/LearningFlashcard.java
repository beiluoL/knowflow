package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_flashcard")
/** 用户级学习闪卡实体：与知识库/文档来源关联，支持间隔重复复习。 */
public class LearningFlashcard extends BaseEntity {

    /** 所属用户 ID（必须，用户维度隔离） */
    private Long userId;

    /** 归属学习路径（可选） */
    private Long pathId;

    /** 归属学习章节（可选） */
    private Long chapterId;

    /** 关联知识库/分类 ID（doc_category.id） */
    private Long categoryId;

    /** 来源文档 ID（doc_document.id） */
    private Long docId;

    /** 正面：问题 / 概念 / 术语 */
    private String front;

    /** 背面：答案 / 解释 / 定义 */
    private String back;

    /** 用户自定义分类（字符串标签，兼容旧字段） */
    private String category;

    /** 难度：1 简单 / 2 中等 / 3 困难 */
    private Integer difficulty;

    /** 逗号分隔的自定义标签 */
    private String tags;

    /** 来源：MANUAL 手动 / AI_DOC AI 基于文档 / AI_KB AI 基于知识库 / IMPORT 批量导入 */
    private String sourceType;

    /** 已复习次数 */
    private Integer reviewCount;

    /** 当前复习间隔（天），间隔重复算法使用 */
    private Integer reviewInterval;

    /** 下次应复习时间 */
    private LocalDateTime nextReviewTime;

    /** 上次复习时间 */
    private LocalDateTime lastReviewTime;
}
