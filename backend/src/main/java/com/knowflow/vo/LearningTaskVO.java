package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学习任务视图对象，封装任务类型、奖励与截止时间等信息。
 */
@Data
public class LearningTaskVO {

    private Long id;

    private String title;

    private String description;

    /** 任务类型编码 */
    private String type;

    private Long targetId;

    /** 完成任务奖励经验值 */
    private Integer expReward;

    /** 完成任务消耗精力值 */
    private Integer energyCost;

    private LocalDateTime deadline;

    /** 任务状态编码 */
    private Integer status;
}
