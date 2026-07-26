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

    /** 复习次数 */
    private Integer reviewCount;

    private LocalDateTime lastReviewTime;

    /** 掌握状态，0-未掌握 1-已掌握 */
    private Integer mastered;

    /** 错题来源描述 */
    private String source;

    private LocalDateTime createTime;
}
