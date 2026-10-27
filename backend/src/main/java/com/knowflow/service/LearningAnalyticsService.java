package com.knowflow.service;

import com.knowflow.vo.LearningAnalyticsVO;

/**
 * Phase 3H：统一学习分析服务。
 * 所有指标口径见 {@link LearningAnalyticsVO} 类注释与 docs/LEARNING_ANALYTICS_DESIGN.md。
 */
public interface LearningAnalyticsService {

    /**
     * 当前用户最近 windowDays 天（练习/代码侧）学习分析概览；错题统计为全量口径。
     */
    LearningAnalyticsVO overview(Long userId, Integer windowDays);
}
