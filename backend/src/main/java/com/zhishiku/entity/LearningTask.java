package com.zhishiku.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_task")
public class LearningTask extends BaseEntity {

    private Long userId;

    private String title;

    private String description;

    private String type;

    private Long targetId;

    private Integer expReward;

    private Integer energyCost;

    private LocalDateTime deadline;

    private Integer status;
}
