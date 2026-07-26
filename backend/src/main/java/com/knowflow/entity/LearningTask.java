package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_task")
/** 学习任务实体，记录用户待完成的学习事项及经验、精力消耗。 */
public class LearningTask extends BaseEntity {

    private Long userId;

    private String title;

    private String description;

    /** 任务类型，如文档阅读、章节学习等。 */
    private String type;

    private Long targetId;

    /** 完成任务获得的经验值。 */
    private Integer expReward;

    /** 完成任务消耗的精力值。 */
    private Integer energyCost;

    private LocalDateTime deadline;

    /** 任务状态，如 0 待完成 / 1 已完成。 */
    private Integer status;
}
