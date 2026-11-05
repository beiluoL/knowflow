package com.knowflow.learning.contract;

/** Persisted learning fact consumed by Reward; exposes no persistence operations. */
public interface LearningRewardEvent {
    Long getId();
    Long getUserId();
    String getEventType();
    String getMetadata();
}
