package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_user_chapter")
public class LearningUserChapter extends BaseEntity {

    private Long userId;

    private Long pathId;

    private Long chapterId;

    private LocalDateTime completeTime;
}
