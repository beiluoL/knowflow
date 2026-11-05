package com.knowflow.learning.actions;

import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
import lombok.Data;

/** 仅维护确认元数据，真实任务始终保存在现有任务表中。 */
@Data
@TableName("learning_action_draft")
public class LearningActionDraft {
    @TableId(type = IdType.INPUT)
    private String id;

    private Long userId;
    private Integer version;
    private String state, payload, resultJson, idempotencyKey, requestHash;
    private Instant expiresAt, createTime;
}
