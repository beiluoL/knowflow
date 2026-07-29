package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("quiz_question")
/** 智能题库题目实体，支持单选/多选/填空/判断/简答等多种题型，区分 AI 生成与手动新增。 */
public class QuizQuestion extends BaseEntity {

    /** 题目标题 */
    private String title;

    /** 题干内容（支持 Markdown） */
    private String content;

    /** 题型：SINGLE_CHOICE / MULTIPLE_CHOICE / FILL_BLANK / TRUE_FALSE / SHORT_ANSWER */
    private String questionType;

    /** 选项 JSON 数组（选择题用） */
    private String options;

    /** 正确答案 */
    private String answer;

    /** 答案解析 */
    private String explanation;

    /** 难度：1 简单 / 2 中等 / 3 困难 */
    private Integer difficulty;

    /** 关联知识库ID（逻辑外键） */
    private Long categoryId;

    /** 关联文档ID（逻辑外键） */
    private Long docId;

    /** 标签 */
    private String tags;

    /** 来源：AI / MANUAL */
    private String source;

    /** 状态：0 草稿 / 1 已发布 */
    private Integer status;

    /** 排序值 */
    private Integer sortOrder;

    // ===== 题型常量 =====
    public static final String TYPE_SINGLE_CHOICE = "SINGLE_CHOICE";
    public static final String TYPE_MULTIPLE_CHOICE = "MULTIPLE_CHOICE";
    public static final String TYPE_FILL_BLANK = "FILL_BLANK";
    public static final String TYPE_TRUE_FALSE = "TRUE_FALSE";
    public static final String TYPE_SHORT_ANSWER = "SHORT_ANSWER";

    // ===== 来源常量 =====
    public static final String SOURCE_AI = "AI";
    public static final String SOURCE_MANUAL = "MANUAL";
}
