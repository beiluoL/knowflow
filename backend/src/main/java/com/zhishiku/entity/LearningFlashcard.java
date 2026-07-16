package com.zhishiku.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_flashcard")
public class LearningFlashcard extends BaseEntity {

    private Long pathId;

    private Long chapterId;

    private String front;

    private String back;

    private String category;

    private Integer difficulty;

    private Integer reviewCount;
}
