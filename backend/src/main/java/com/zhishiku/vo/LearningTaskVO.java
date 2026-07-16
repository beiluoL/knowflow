package com.zhishiku.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LearningTaskVO {

    private Long id;

    private String title;

    private String description;

    private String type;

    private Long targetId;

    private Integer expReward;

    private Integer energyCost;

    private LocalDateTime deadline;

    private Integer status;
}
