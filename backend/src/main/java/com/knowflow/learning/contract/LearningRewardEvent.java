package com.knowflow.learning.contract;

/** 供奖励模块消费的持久化学习事实，不暴露持久化操作。 */
public interface LearningRewardEvent {
    Long getId();
    Long getUserId();
    String getEventType();
    String getMetadata();
}
