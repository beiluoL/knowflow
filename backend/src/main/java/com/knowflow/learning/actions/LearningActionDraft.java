package com.knowflow.learning.actions;

import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
import lombok.Data;

/** Confirmation metadata only. Real tasks always live in existing task tables. */
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
