package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_user_path")
public class LearningUserPath extends BaseEntity {

    private Long userId;

    private Long pathId;

    private BigDecimal progress;

    private Integer completedChapters;

    private LocalDateTime enrollTime;

    private LocalDateTime lastStudyTime;
}
