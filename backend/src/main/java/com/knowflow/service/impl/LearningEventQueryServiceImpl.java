package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.LearningEventType;
import com.knowflow.entity.LearningEvent;
import com.knowflow.mapper.LearningEventMapper;
import com.knowflow.service.LearningEventQueryService;
import com.knowflow.vo.LearningEventSummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 学习事件统计查询服务实现。
 * <p>统计完全基于 learning_event 表（统一口径），不重复读各业务表。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningEventQueryServiceImpl implements LearningEventQueryService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LearningEventMapper learningEventMapper;

    @Override
    public LearningEventSummaryVO summary(Long userId) {
        LocalDateTime weekAgo = LocalDate.now().minusDays(6).atStartOfDay();
        LearningEventSummaryVO vo = new LearningEventSummaryVO();
        vo.setTodayEventCount(todayEventCount(userId));

        long answered = countByTypeSince(userId, LearningEventType.QUESTION_ANSWERED.name(), weekAgo);
        long correct = countQuizCorrectSince(userId, weekAgo);
        vo.setQuizAnswered7d((int) answered);
        vo.setQuizCorrect7d((int) correct);
        vo.setQuizAccuracy7d(answered == 0 ? 0d : Math.round(correct * 1000d / answered) / 1000d);

        vo.setFlashcardReviewed7d((int) countByTypeSince(userId, LearningEventType.FLASHCARD_REVIEWED.name(), weekAgo));
        vo.setCodeSubmitted7d((int) countByTypeSince(userId, LearningEventType.CODE_SUBMITTED.name(), weekAgo));
        vo.setMistakesReviewed7d((int) countByTypeSince(userId, LearningEventType.MISTAKE_REVIEW.name(), weekAgo));
        vo.setTasksCompleted7d((int) countByTypeSince(userId, LearningEventType.LEARNING_TASK_COMPLETE.name(), weekAgo));

        List<LearningEvent> recent = recent(userId, 20);
        Set<String> types = new LinkedHashSet<>();
        for (LearningEvent e : recent) {
            if (e.getEventType() != null) {
                types.add(e.getEventType());
            }
        }
        vo.setRecentTypes(new ArrayList<>(types).size() > 10
                ? new ArrayList<>(types).subList(0, 10) : new ArrayList<>(types));
        return vo;
    }

    @Override
    public int todayEventCount(Long userId) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        return Math.toIntExact(learningEventMapper.selectCount(new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, userId)
                .ge(LearningEvent::getCreateTime, start)));
    }

    @Override
    public List<LearningEvent> recent(Long userId, int limit) {
        int l = Math.max(1, Math.min(limit, 100));
        return learningEventMapper.selectList(new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, userId)
                .orderByDesc(LearningEvent::getCreateTime)
                .last("LIMIT " + l));
    }

    @Override
    public long countByTypeSince(Long userId, String eventType, LocalDateTime since) {
        return learningEventMapper.selectCount(new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, userId)
                .eq(LearningEvent::getEventType, eventType)
                .ge(LearningEvent::getCreateTime, since));
    }

    @Override
    public double quizAccuracySince(Long userId, LocalDateTime since) {
        long answered = countByTypeSince(userId, LearningEventType.QUESTION_ANSWERED.name(), since);
        if (answered == 0) {
            return 0d;
        }
        return Math.round(countQuizCorrectSince(userId, since) * 1000d / answered) / 1000d;
    }

    /** 统计 since 之后 QUESTION_ANSWERED 中 metadata.correct=true 的条数。 */
    private long countQuizCorrectSince(Long userId, LocalDateTime since) {
        List<LearningEvent> events = learningEventMapper.selectList(new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, userId)
                .eq(LearningEvent::getEventType, LearningEventType.QUESTION_ANSWERED.name())
                .ge(LearningEvent::getCreateTime, since));
        long correct = 0;
        for (LearningEvent e : events) {
            if (isMetadataTrue(e.getMetadata(), "correct")) {
                correct++;
            }
        }
        return correct;
    }

    private boolean isMetadataTrue(String metadata, String key) {
        if (metadata == null || metadata.isBlank()) {
            return false;
        }
        try {
            JsonNode node = MAPPER.readTree(metadata);
            JsonNode v = node.get(key);
            return v != null && v.asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }
}
