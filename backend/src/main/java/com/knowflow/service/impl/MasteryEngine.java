package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.entity.CodeSubmitRecord;
import com.knowflow.entity.DocReadProgress;
import com.knowflow.entity.KgEntity;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.entity.LearningMistake;
import com.knowflow.entity.QuizAnswerRecord;
import com.knowflow.entity.ResourceKnowledgeMapping;
import com.knowflow.entity.WbRecallSession;
import com.knowflow.entity.WbReviewCard;
import com.knowflow.entity.WbReviewLog;
import com.knowflow.mapper.CodeSubmitRecordMapper;
import com.knowflow.mapper.DocReadProgressMapper;
import com.knowflow.mapper.LearningFlashcardMapper;
import com.knowflow.mapper.LearningMistakeMapper;
import com.knowflow.mapper.QuizAnswerRecordMapper;
import com.knowflow.mapper.ResourceKnowledgeMappingMapper;
import com.knowflow.mapper.WbRecallSessionMapper;
import com.knowflow.mapper.WbReviewCardMapper;
import com.knowflow.mapper.WbReviewLogMapper;
import com.knowflow.mastery.MasteryComputation;
import com.knowflow.mastery.MasteryConfig;
import com.knowflow.mastery.MasterySignal;
import com.knowflow.mastery.SignalType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 掌握度算法引擎（Knowledge Mastery Engine，Phase 2-B）。
 * <p>
 * 职责：将 LearningEvent 对应的「(user, knowledge)」聚合为 {@link MasteryComputation}（各维度信号 + 计数器 + 时间）。
 * 算法完全可解释、无 LLM/ML；整行重算保证幂等（不累计增量）。
 * mastery / confidence / risk / status 的合成在 KnowledgeMasteryService 完成。
 * </p>
 */
@Slf4j
@Service
public class MasteryEngine {

    private final ResourceKnowledgeMappingMapper mappingMapper;
    private final QuizAnswerRecordMapper quizAnswerRecordMapper;
    private final CodeSubmitRecordMapper codeSubmitRecordMapper;
    private final WbRecallSessionMapper wbRecallSessionMapper;
    private final WbReviewCardMapper wbReviewCardMapper;
    private final WbReviewLogMapper wbReviewLogMapper;
    private final LearningFlashcardMapper learningFlashcardMapper;
    private final LearningMistakeMapper learningMistakeMapper;
    private final DocReadProgressMapper docReadProgressMapper;

    public MasteryEngine(ResourceKnowledgeMappingMapper mappingMapper,
                         QuizAnswerRecordMapper quizAnswerRecordMapper,
                         CodeSubmitRecordMapper codeSubmitRecordMapper,
                         WbRecallSessionMapper wbRecallSessionMapper,
                         WbReviewCardMapper wbReviewCardMapper,
                         WbReviewLogMapper wbReviewLogMapper,
                         LearningFlashcardMapper learningFlashcardMapper,
                         LearningMistakeMapper learningMistakeMapper,
                         DocReadProgressMapper docReadProgressMapper) {
        this.mappingMapper = mappingMapper;
        this.quizAnswerRecordMapper = quizAnswerRecordMapper;
        this.codeSubmitRecordMapper = codeSubmitRecordMapper;
        this.wbRecallSessionMapper = wbRecallSessionMapper;
        this.wbReviewCardMapper = wbReviewCardMapper;
        this.wbReviewLogMapper = wbReviewLogMapper;
        this.learningFlashcardMapper = learningFlashcardMapper;
        this.learningMistakeMapper = learningMistakeMapper;
        this.docReadProgressMapper = docReadProgressMapper;
    }

    /**
     * 聚合计算单知识点掌握度（仅基于已接受映射 + 当前用户真实记录）。
     */
    public MasteryComputation compute(Long userId, Long knowledgeId) {
        MasteryComputation c = new MasteryComputation();
        if (userId == null || knowledgeId == null) {
            return c;
        }
        List<ResourceKnowledgeMapping> maps = mappingMapper.selectList(
                new LambdaQueryWrapper<ResourceKnowledgeMapping>()
                        .eq(ResourceKnowledgeMapping::getKnowledgeId, knowledgeId)
                        .eq(ResourceKnowledgeMapping::getStatus, MasteryConfig.MAP_STATUS_ACCEPTED));
        if (maps.isEmpty()) {
            return c;
        }
        aggregateQuiz(userId, maps, c);
        aggregateCoding(userId, maps, c);
        aggregateRecall(userId, maps, c);
        aggregateReviewAndFlashcard(userId, maps, c);
        aggregateMistake(userId, maps, c);
        aggregateLearning(userId, maps, c);
        return c;
    }

    // ============================================================
    // Quiz：正确率（拉普拉斯平滑）
    // ============================================================
    private void aggregateQuiz(Long userId, List<ResourceKnowledgeMapping> maps, MasteryComputation c) {
        List<Long> ids = idsOf(maps, MasteryConfig.RES_QUIZ);
        if (ids.isEmpty()) {
            return;
        }
        List<QuizAnswerRecord> recs = quizAnswerRecordMapper.selectList(
                new LambdaQueryWrapper<QuizAnswerRecord>()
                        .eq(QuizAnswerRecord::getUserId, userId)
                        .in(QuizAnswerRecord::getQuestionId, ids));
        if (recs.isEmpty()) {
            return;
        }
        int correct = 0;
        int wrong = 0;
        for (QuizAnswerRecord r : recs) {
            if (r.getIsCorrect() != null && r.getIsCorrect() == 1) {
                correct++;
            } else if (r.getIsCorrect() != null && r.getIsCorrect() == 0) {
                wrong++;
            }
        }
        int attempt = recs.size();
        double acc = (correct + MasteryConfig.LAPLACE_ALPHA) / (correct + wrong + 2 * MasteryConfig.LAPLACE_ALPHA);
        c.getSignals().add(new MasterySignal(SignalType.QUIZ, acc, attempt));
        c.setCorrectCount(c.getCorrectCount() + correct);
        c.setWrongCount(c.getWrongCount() + wrong);
        c.setAttemptCount(c.getAttemptCount() + attempt);

        recs.sort(Comparator.comparing(r -> r.getCreateTime() == null ? LocalDateTime.MIN : r.getCreateTime()));
        int runCorrect = 0;
        int runWrong = 0;
        for (int i = recs.size() - 1; i >= 0; i--) {
            Integer ic = recs.get(i).getIsCorrect();
            if (ic != null && ic == 1) {
                runCorrect++;
            } else if (ic != null && ic == 0) {
                runWrong++;
                break;
            } else {
                break;
            }
        }
        c.setConsecutiveCorrect(runCorrect);
        c.setConsecutiveWrong(runWrong);

        LocalDateTime last = recs.stream().map(QuizAnswerRecord::getCreateTime).max(LocalDateTime::compareTo).orElse(null);
        c.setLastAssessedAt(max(c.getLastAssessedAt(), last));
        c.setLastLearnedAt(max(c.getLastLearnedAt(), last));
    }

    // ============================================================
    // Coding：通过率（拉普拉斯平滑，权重高于 Quiz）
    // ============================================================
    private void aggregateCoding(Long userId, List<ResourceKnowledgeMapping> maps, MasteryComputation c) {
        List<Long> ids = idsOf(maps, MasteryConfig.RES_CODE_QUESTION);
        if (ids.isEmpty()) {
            return;
        }
        List<CodeSubmitRecord> recs = codeSubmitRecordMapper.selectList(
                new LambdaQueryWrapper<CodeSubmitRecord>()
                        .eq(CodeSubmitRecord::getUserId, userId)
                        .in(CodeSubmitRecord::getQuestionId, ids)
                        .eq(CodeSubmitRecord::getDeleted, 0));
        if (recs.isEmpty()) {
            return;
        }
        int attempt = recs.size();
        int pass = 0;
        for (CodeSubmitRecord r : recs) {
            if (r.getPassed() != null && r.getPassed() == 1) {
                pass++;
            }
        }
        double passRate = (pass + MasteryConfig.LAPLACE_ALPHA) / (attempt + 2 * MasteryConfig.LAPLACE_ALPHA);
        c.getSignals().add(new MasterySignal(SignalType.CODING, passRate, attempt));
        c.setCodingAttemptCount(c.getCodingAttemptCount() + attempt);
        c.setCodingPassCount(c.getCodingPassCount() + pass);
        LocalDateTime last = recs.stream().map(CodeSubmitRecord::getCreateTime).max(LocalDateTime::compareTo).orElse(null);
        c.setLastAssessedAt(max(c.getLastAssessedAt(), last));
        c.setLastLearnedAt(max(c.getLastLearnedAt(), last));
    }

    // ============================================================
    // Recall：主动回忆平均分 / 100
    // ============================================================
    private void aggregateRecall(Long userId, List<ResourceKnowledgeMapping> maps, MasteryComputation c) {
        List<Long> ids = idsOf(maps, MasteryConfig.RES_RECALL_SESSION);
        if (ids.isEmpty()) {
            return;
        }
        List<WbRecallSession> recs = wbRecallSessionMapper.selectList(
                new LambdaQueryWrapper<WbRecallSession>()
                        .eq(WbRecallSession::getUserId, userId)
                        .in(WbRecallSession::getId, ids)
                        .eq(WbRecallSession::getStatus, "COMPLETED"));
        if (recs.isEmpty()) {
            return;
        }
        int total = 0;
        int count = 0;
        for (WbRecallSession r : recs) {
            int s = avgRoundScore(r);
            total += s;
            count++;
        }
        int avg = count == 0 ? 0 : Math.round((float) total / count);
        c.getSignals().add(new MasterySignal(SignalType.RECALL, avg / 100.0, count));
        c.setRecallCount(c.getRecallCount() + count);
        c.setRecallAvgScore(avg);
        LocalDateTime last = recs.stream().map(WbRecallSession::getCompletedTime).max(LocalDateTime::compareTo).orElse(null);
        c.setLastReviewedAt(max(c.getLastReviewedAt(), last));
    }

    private int avgRoundScore(WbRecallSession r) {
        int sum = 0;
        int n = 0;
        if (r.getRound1Score() != null) {
            sum += r.getRound1Score();
            n++;
        }
        if (r.getRound2Score() != null) {
            sum += r.getRound2Score();
            n++;
        }
        if (r.getRound3Score() != null) {
            sum += r.getRound3Score();
            n++;
        }
        return n == 0 ? 0 : Math.round((float) sum / n);
    }

    // ============================================================
    // Review：SM-2 稳定度（REVIEW_CARD + FLASHCARD 合并）
    // ============================================================
    private void aggregateReviewAndFlashcard(Long userId, List<ResourceKnowledgeMapping> maps, MasteryComputation c) {
        List<Long> cardIds = idsOf(maps, MasteryConfig.RES_REVIEW_CARD);
        double stabilitySum = 0.0;
        int cardCount = 0;
        double intervalSum = 0.0;
        int withInterval = 0;
        int reviewLogCount = 0;
        double qualitySum = 0.0;

        if (!cardIds.isEmpty()) {
            List<WbReviewCard> cards = wbReviewCardMapper.selectList(
                    new LambdaQueryWrapper<WbReviewCard>()
                            .eq(WbReviewCard::getUserId, userId)
                            .in(WbReviewCard::getId, cardIds));
            for (WbReviewCard card : cards) {
                int rep = card.getRepetitions() == null ? 0 : card.getRepetitions();
                int lapse = card.getLapseCount() == null ? 0 : card.getLapseCount();
                c.setLapseWeight(c.getLapseWeight() + lapse);
                c.setReviewCount(c.getReviewCount() + (card.getReviewCount() == null ? 0 : card.getReviewCount()));
                double stability = (double) (rep + 1) / (rep + lapse + 1);
                stabilitySum += stability;
                cardCount++;
                if (card.getIntervalDay() != null) {
                    intervalSum += Math.min(card.getIntervalDay(), MasteryConfig.REVIEW_INTERVAL_CAP_DAYS)
                            / (double) MasteryConfig.REVIEW_INTERVAL_CAP_DAYS;
                    withInterval++;
                }
                c.setNextReviewAt(min(c.getNextReviewAt(), card.getNextReviewTime()));
                c.setLastReviewedAt(max(c.getLastReviewedAt(), card.getLastReviewTime()));
            }
            List<WbReviewLog> logs = wbReviewLogMapper.selectList(
                    new LambdaQueryWrapper<WbReviewLog>()
                            .eq(WbReviewLog::getUserId, userId)
                            .in(WbReviewLog::getCardId, cardIds));
            for (WbReviewLog log : logs) {
                if (log.getQuality() != null) {
                    qualitySum += log.getQuality();
                    reviewLogCount++;
                }
            }
        }

        // FLASHCARD 作为复习信号补充（无 SM-2，用复习次数代理）
        List<Long> fIds = idsOf(maps, MasteryConfig.RES_FLASHCARD);
        int flashcardCount = 0;
        int flashcardReviewSum = 0;
        if (!fIds.isEmpty()) {
            List<LearningFlashcard> cards = learningFlashcardMapper.selectList(
                    new LambdaQueryWrapper<LearningFlashcard>()
                            .eq(LearningFlashcard::getUserId, userId)
                            .in(LearningFlashcard::getId, fIds));
            for (LearningFlashcard card : cards) {
                int rc = card.getReviewCount() == null ? 0 : card.getReviewCount();
                flashcardReviewSum += rc;
                flashcardCount++;
                c.setReviewCount(c.getReviewCount() + rc);
                c.setNextReviewAt(min(c.getNextReviewAt(), card.getNextReviewTime()));
                c.setLastReviewedAt(max(c.getLastReviewedAt(), card.getLastReviewTime()));
            }
        }

        double strength;
        if (cardCount > 0) {
            double stabilityAvg = stabilitySum / cardCount;
            double intervalAvg = withInterval > 0 ? intervalSum / withInterval : 0.0;
            strength = MasteryConfig.REVIEW_STABILITY_WEIGHT * stabilityAvg
                    + MasteryConfig.REVIEW_INTERVAL_WEIGHT * intervalAvg;
            if (reviewLogCount > 0) {
                double qualityStrength = (qualitySum / reviewLogCount) / 3.0;
                strength = strength * 0.7 + qualityStrength * 0.3;
            }
        } else if (flashcardCount > 0) {
            double avgReview = (double) flashcardReviewSum / flashcardCount;
            strength = Math.min(avgReview, MasteryConfig.FLASHCARD_REVIEW_CAP) / MasteryConfig.FLASHCARD_REVIEW_CAP;
        } else {
            return;
        }
        int sample = cardCount + flashcardCount + reviewLogCount;
        c.getSignals().add(new MasterySignal(SignalType.REVIEW, clamp01(strength), sample));
    }

    // ============================================================
    // Mistake：已掌握错题比（拉普拉斯平滑）
    // ============================================================
    private void aggregateMistake(Long userId, List<ResourceKnowledgeMapping> maps, MasteryComputation c) {
        List<Long> ids = idsOf(maps, MasteryConfig.RES_MISTAKE);
        if (ids.isEmpty()) {
            return;
        }
        List<LearningMistake> recs = learningMistakeMapper.selectList(
                new LambdaQueryWrapper<LearningMistake>()
                        .eq(LearningMistake::getUserId, userId)
                        .in(LearningMistake::getId, ids));
        if (recs.isEmpty()) {
            return;
        }
        int count = recs.size();
        int mastered = 0;
        for (LearningMistake m : recs) {
            if (m.getMastered() != null && m.getMastered() == 1) {
                mastered++;
            }
        }
        double ratio = (mastered + MasteryConfig.LAPLACE_ALPHA) / (count + MasteryConfig.LAPLACE_ALPHA);
        c.getSignals().add(new MasterySignal(SignalType.MISTAKE, ratio, count));
        c.setMistakeCount(c.getMistakeCount() + count);
        c.setMistakeMastered(c.getMistakeMastered() + mastered);
        LocalDateTime last = recs.stream().map(LearningMistake::getLastReviewTime).max(LocalDateTime::compareTo).orElse(null);
        c.setLastReviewedAt(max(c.getLastReviewedAt(), last));
    }

    // ============================================================
    // Learning：文档阅读进度（曝光信号）
    // ============================================================
    private void aggregateLearning(Long userId, List<ResourceKnowledgeMapping> maps, MasteryComputation c) {
        List<Long> ids = idsOf(maps, MasteryConfig.RES_DOC);
        if (ids.isEmpty()) {
            return;
        }
        List<DocReadProgress> recs = docReadProgressMapper.selectList(
                new LambdaQueryWrapper<DocReadProgress>()
                        .eq(DocReadProgress::getUserId, userId)
                        .in(DocReadProgress::getDocId, ids));
        if (recs.isEmpty()) {
            return;
        }
        int sample = 0;
        double progressSum = 0.0;
        for (DocReadProgress r : recs) {
            if (r.getProgress() != null && r.getProgress().compareTo(BigDecimal.ZERO) > 0) {
                sample++;
                progressSum += r.getProgress().doubleValue();
            }
        }
        if (sample == 0) {
            return;
        }
        double strength = progressSum / sample;
        c.getSignals().add(new MasterySignal(SignalType.LEARNING, clamp01(strength), sample));
        LocalDateTime last = recs.stream().map(DocReadProgress::getLastReadTime).max(LocalDateTime::compareTo).orElse(null);
        c.setLastLearnedAt(max(c.getLastLearnedAt(), last));
    }

    // ============================================================
    // 工具
    // ============================================================
    private List<Long> idsOf(List<ResourceKnowledgeMapping> maps, String type) {
        List<Long> ids = new ArrayList<>();
        for (ResourceKnowledgeMapping m : maps) {
            if (type.equals(m.getResourceType())) {
                ids.add(m.getResourceId());
            }
        }
        return ids;
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isBefore(b) ? a : b;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : Math.min(v, 1.0);
    }
}
