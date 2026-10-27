package com.knowflow.service;

import com.knowflow.entity.LearningEvent;
import com.knowflow.vo.LearningEventSummaryVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学习事件统计查询服务（Phase 1）。
 * <p>
 * 统一事件统计口径：所有「次数 / 正确率 / 最近行为」都基于 learning_event 计算，
 * 避免各业务模块各自统计导致口径不一致。供学习看板、AI 诊断（后续 Phase）、学习计划消费。
 * 统计逻辑只允许在此 Service，不允许塞进 Controller。
 */
public interface LearningEventQueryService {

    /** 学习事件统计摘要（今日 + 近 7 日），供看板 / AI 诊断消费。 */
    LearningEventSummaryVO summary(Long userId);

    /** 今日学习事件数。 */
    int todayEventCount(Long userId);

    /** 最近 limit 条学习事件（按发生时间倒序）。 */
    List<LearningEvent> recent(Long userId, int limit);

    /** 指定类型事件在 since 之后的条数。 */
    long countByTypeSince(Long userId, String eventType, LocalDateTime since);

    /** 答题正确率（0~1）：since 之后 QUESTION_ANSWERED 中 correct=true 占比；无答题返回 0。 */
    double quizAccuracySince(Long userId, LocalDateTime since);
}
