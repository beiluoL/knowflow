package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 错题视图对象，封装题目、正误答案、掌握状态与复习记录。
 */
@Data
public class MistakeVO {

    private Long id;

    private Long userId;

    private String question;

    private String wrongAnswer;

    private String correctAnswer;

    private String category;

    /** 难度等级编码（具体以枚举为准） */
    private Integer difficulty;

    /** 原测验题ID（逻辑外键 quiz_question.id，可空兼容旧数据）——打通 错题→原题→解析→再练习 */
    private Long questionId;

    /** 原代码题ID（逻辑外键 code_question.id，可空兼容旧数据） */
    private Long codeQuestionId;

    /** 复习次数 */
    private Integer reviewCount;

    private LocalDateTime lastReviewTime;

    /** 掌握状态，0-未掌握 1-已掌握 */
    private Integer mastered;

    /** 错题来源描述 */
    private String source;

    /** Phase 3H：题型（关联 quiz_question 补全；沙箱类历史错题可能为空）。 */
    private String questionType;

    /** Phase 3H：知识点编码（关联 quiz_question 补全）。 */
    private String topicCode;

    private LocalDateTime createTime;
}
