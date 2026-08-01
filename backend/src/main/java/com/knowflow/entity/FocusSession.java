package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

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
