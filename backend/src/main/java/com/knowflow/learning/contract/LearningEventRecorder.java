package com.knowflow.learning.contract;

import java.util.Map;

/** 尽力追加真实活动，保留现有独立事务语义。 */
public interface LearningEventRecorder {
    /** 记录真实活动，不伪造证据，也不直接发放奖励。 */
    void record(Long userId, LearningEventType type, String resourceType, Long resourceId,
                Map<String, Object> metadata);
}
