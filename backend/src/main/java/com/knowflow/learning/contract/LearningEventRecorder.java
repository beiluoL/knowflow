package com.knowflow.learning.contract;

import java.util.Map;

/** Best-effort append of a real activity; retains the existing independent transaction semantics. */
public interface LearningEventRecorder {
    /** Records a genuine activity; does not synthesize evidence or grant rewards directly. */
    void record(Long userId, LearningEventType type, String resourceType, Long resourceId,
                Map<String, Object> metadata);
}
