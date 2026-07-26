package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_user_path")
/** 用户学习路径订阅实体，记录用户对某路径的学习进度。 */
public class LearningUserPath extends BaseEntity {

    private Long userId;

    private Long pathId;

    /** 学习进度比例，取值范围 0~1。 */
    private BigDecimal progress;

    /** 已完成章节数。 */
    private Integer completedChapters;

    private LocalDateTime enrollTime;

    private LocalDateTime lastStudyTime;
}
