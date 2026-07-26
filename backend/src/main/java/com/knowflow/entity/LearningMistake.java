package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_mistake")
/** 错题本实体，记录用户答错的题目用于复习巩固。 */
public class LearningMistake extends BaseEntity {

    private Long userId;

    private String question;

    private String wrongAnswer;

    private String correctAnswer;

    private String category;

    private Integer difficulty;

    private Integer reviewCount;

    private LocalDateTime lastReviewTime;

    /** 是否已掌握，0 未掌握 / 1 已掌握。 */
    private Integer mastered;

    /** 题目来源，如来源文档 ID 或学习路径。 */
    private String source;
}
