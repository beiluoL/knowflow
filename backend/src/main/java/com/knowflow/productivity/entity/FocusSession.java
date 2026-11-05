package com.knowflow.productivity.entity;

import com.knowflow.common.persistence.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 表示持久化的专注会话记录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("focus_session")
public class FocusSession extends BaseEntity {

    private Long userId;

    private String mode;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer durationMin;

    private Integer distractionCount;

    private Integer completedPomodoros;

    private Long associatedTaskId;

    private Integer qualityRating;

    private String note;
}
