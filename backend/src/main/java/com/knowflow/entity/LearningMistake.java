package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_mistake")
public class LearningMistake extends BaseEntity {

    private Long userId;

    private String question;

    private String wrongAnswer;

    private String correctAnswer;

    private String category;

    private Integer difficulty;

    private Integer reviewCount;

    private LocalDateTime lastReviewTime;

    private Integer mastered;

    private String source;
}
