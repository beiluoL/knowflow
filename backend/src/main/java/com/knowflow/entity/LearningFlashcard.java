package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_flashcard")
/** 学习闪卡实体，用于间隔重复记忆的卡片。 */
public class LearningFlashcard extends BaseEntity {

    private Long pathId;

    private Long chapterId;

    private String front;

    private String back;

    private String category;

    private Integer difficulty;

    /** 已复习次数。 */
    private Integer reviewCount;

    /** 当前复习间隔（天），间隔重复算法使用。 */
    private Integer reviewInterval;

    /** 下次应复习时间。 */
    private LocalDateTime nextReviewTime;

    private LocalDateTime lastReviewTime;
}
