package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.coding.entity.CodeSubmission;
import com.knowflow.coding.judge.JudgeStatus;
import com.knowflow.coding.mapper.CodeSubmissionMapper;
import com.knowflow.entity.LearningMistake;
import com.knowflow.entity.QuizAnswerRecord;
import com.knowflow.entity.QuizQuestion;
import com.knowflow.mapper.LearningMistakeMapper;
import com.knowflow.mapper.QuizAnswerRecordMapper;
import com.knowflow.mapper.QuizQuestionMapper;
import com.knowflow.service.LearningAnalyticsService;
import com.knowflow.service.MistakeService;
import com.knowflow.vo.LearningAnalyticsVO;
import com.knowflow.vo.LearningAnalyticsVO.Bucket;
import com.knowflow.vo.LearningAnalyticsVO.CodeSummary;
import com.knowflow.vo.LearningAnalyticsVO.DayPoint;
import com.knowflow.vo.LearningAnalyticsVO.MistakeSummary;
import com.knowflow.vo.LearningAnalyticsVO.QuizSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 3H 统一学习分析实现（确定性聚合，不依赖 AI）。
 */
@Service
@RequiredArgsConstructor
public class LearningAnalyticsServiceImpl implements LearningAnalyticsService {

    private static final int DEFAULT_WINDOW_DAYS = 30;
    private static final int MAX_WINDOW_DAYS = 365;

    private static final Map<String, String> TYPE_LABELS = Map.of(
            "SINGLE_CHOICE", "单选题",
            "MULTIPLE_CHOICE", "多选题",
            "TRUE_FALSE", "判断题",
            "FILL_BLANK", "填空题",
            "SHORT_ANSWER", "简答题",
            "CODE", "算法题");

    private static final Map<String, String> VERDICT_LABELS = Map.of(
            JudgeStatus.ACCEPTED, "通过",
            JudgeStatus.WRONG_ANSWER, "答案错误",
            JudgeStatus.COMPILE_ERROR, "编译错误",
            JudgeStatus.RUNTIME_ERROR, "运行时错误",
            JudgeStatus.TIME_LIMIT_EXCEEDED, "超时",
            JudgeStatus.MEMORY_LIMIT_EXCEEDED, "超内存");

    private final QuizAnswerRecordMapper answerRecordMapper;
    private final QuizQuestionMapper quizQuestionMapper;
    private final CodeSubmissionMapper codeSubmissionMapper;
    private final LearningMistakeMapper mistakeMapper;
    private final MistakeService mistakeService;

    @Override
    @Transactional(readOnly = true)
    public LearningAnalyticsVO overview(Long userId, Integer windowDays) {
        int days = windowDays == null || windowDays <= 0 ? DEFAULT_WINDOW_DAYS
                : Math.min(windowDays, MAX_WINDOW_DAYS);
        LocalDateTime from = LocalDate.now().atStartOfDay().minusDays(days - 1L);

        LearningAnalyticsVO vo = new LearningAnalyticsVO();
        vo.setWindowDays(days);
        vo.setQuiz(buildQuizSummary(userId, from, vo));
        vo.setCode(buildCodeSummary(userId, from));
        vo.setMistake(buildMistakeSummary(userId));
        return vo;
    }

    private QuizSummary buildQuizSummary(Long userId, LocalDateTime from, LearningAnalyticsVO vo) {
        List<QuizAnswerRecord> records = answerRecordMapper.selectList(
                new LambdaQueryWrapper<QuizAnswerRecord>()
                        .eq(QuizAnswerRecord::getUserId, userId)
                        .ge(QuizAnswerRecord::getCreateTime, from));

        // 批量取题目元数据，供题型/知识点分组
        List<Long> questionIds = records.stream().map(QuizAnswerRecord::getQuestionId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, QuizQuestion> questionMap = new HashMap<>();
        if (!questionIds.isEmpty()) {
            quizQuestionMapper.selectBatchIds(questionIds).forEach(q -> questionMap.put(q.getId(), q));
        }

        long graded = 0, correct = 0, timeCostSum = 0, timeCostCount = 0;
        Map<String, long[]> byType = new LinkedHashMap<>();
        Map<String, long[]> byTopic = new LinkedHashMap<>();
        // 每日 [answered, correct]，窗口内每一天都初始化（含 0）
        Map<String, long[]> daily = new LinkedHashMap<>();
        for (int i = 0; i < vo.getWindowDays(); i++) {
            daily.put(from.toLocalDate().plusDays(i).toString(), new long[2]);
        }

        for (QuizAnswerRecord r : records) {
            String day = r.getCreateTime() != null ? r.getCreateTime().toLocalDate().toString() : null;
            if (day != null && daily.containsKey(day)) {
                daily.get(day)[0]++;
            }
            if (r.getTimeCost() != null && r.getTimeCost() > 0) {
                timeCostSum += r.getTimeCost();
                timeCostCount++;
            }
            // 未评分（主观题待 AI/人工评分）不计入正确率
            if (r.getIsCorrect() == null) {
                continue;
            }
            graded++;
            boolean ok = r.getIsCorrect() == 1;
            if (ok) {
                correct++;
                if (day != null && daily.containsKey(day)) {
                    daily.get(day)[1]++;
                }
            }
            QuizQuestion q = r.getQuestionId() == null ? null : questionMap.get(r.getQuestionId());
            if (q != null) {
                accumulate(byType, q.getQuestionType(), ok);
                if (q.getTopicCode() != null && !q.getTopicCode().isBlank()) {
                    accumulate(byTopic, q.getTopicCode(), ok);
                }
            }
        }

        QuizSummary summary = new QuizSummary();
        summary.setTotalAnswered(records.size());
        summary.setGradedCount(graded);
        summary.setCorrectCount(correct);
        summary.setCorrectRate(rate(correct, graded));
        summary.setUngradedCount(records.size() - graded);
        summary.setAvgTimeCostSeconds(timeCostCount == 0 ? null
                : Math.round(timeCostSum * 10.0 / timeCostCount) / 10.0);

        vo.setByType(toBuckets(byType, TYPE_LABELS, false));
        vo.setByTopic(toBuckets(byTopic, Map.of(), true));
        List<DayPoint> trend = new ArrayList<>();
        daily.forEach((date, arr) -> {
            DayPoint p = new DayPoint();
            p.setDate(date);
            p.setAnswered(arr[0]);
            p.setCorrect(arr[1]);
            p.setRate(rate(arr[1], arr[0]));
            trend.add(p);
        });
        vo.setDailyTrend(trend);
        return summary;
    }

    private CodeSummary buildCodeSummary(Long userId, LocalDateTime from) {
        List<CodeSubmission> subs = codeSubmissionMapper.selectList(
                new LambdaQueryWrapper<CodeSubmission>()
                        .eq(CodeSubmission::getUserId, userId)
                        .eq(CodeSubmission::getSubmissionType, CodeSubmission.TYPE_SUBMIT)
                        .ge(CodeSubmission::getCreateTime, from)
                        .orderByAsc(CodeSubmission::getCreateTime));

        long total = 0, accepted = 0, platformFailure = 0;
        Map<String, long[]> verdict = new LinkedHashMap<>();
        // 每题按时间最早的正式提交判定首次通过
        Map<Long, String> firstVerdictByQuestion = new LinkedHashMap<>();

        for (CodeSubmission s : subs) {
            String status = s.getJudgeStatus();
            if (JudgeStatus.isPlatformFailure(status)) {
                platformFailure++;
                // 平台失败不进入该题首次提交判定的负例：只有真实用户失败才占首次
                continue;
            }
            if (!JudgeStatus.isFinished(status)) {
                continue;
            }
            total++;
            boolean ac = JudgeStatus.ACCEPTED.equals(status);
            if (ac) {
                accepted++;
            }
            verdict.merge(status, new long[]{1, ac ? 1 : 0}, (a, b) -> {
                a[0] += b[0];
                a[1] += b[1];
                return a;
            });
            if (s.getQuestionId() != null) {
                firstVerdictByQuestion.putIfAbsent(s.getQuestionId(), status);
            }
        }

        long attempted = firstVerdictByQuestion.size();
        long firstPass = firstVerdictByQuestion.values().stream()
                .filter(JudgeStatus.ACCEPTED::equals).count();

        CodeSummary summary = new CodeSummary();
        summary.setTotalSubmissions(total);
        summary.setAcceptedSubmissionCount(accepted);
        summary.setAttemptedQuestions(attempted);
        summary.setFirstPassQuestions(firstPass);
        summary.setFirstPassRate(rate(firstPass, attempted));
        summary.setExcludedPlatformFailure(platformFailure);
        List<Bucket> distribution = new ArrayList<>();
        verdict.forEach((k, arr) -> distribution.add(
                new Bucket(k, VERDICT_LABELS.getOrDefault(k, k), arr[0], arr[1])));
        distribution.sort(Comparator.comparing(Bucket::getKey));
        summary.setVerdictDistribution(distribution);
        return summary;
    }

    private MistakeSummary buildMistakeSummary(Long userId) {
        List<LearningMistake> all = mistakeMapper.selectList(
                new LambdaQueryWrapper<LearningMistake>()
                        .eq(LearningMistake::getUserId, userId));
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long pending = 0, mastered = 0, due = 0, reviewSum = 0;
        int maxReview = 0;
        for (LearningMistake m : all) {
            if (m.getMastered() != null && m.getMastered() == 1) {
                mastered++;
            } else {
                pending++;
                if (m.getLastReviewTime() == null || m.getLastReviewTime().isBefore(todayStart)) {
                    due++;
                }
            }
            int rc = m.getReviewCount() == null ? 0 : m.getReviewCount();
            reviewSum += rc;
            maxReview = Math.max(maxReview, rc);
        }
        MistakeSummary summary = new MistakeSummary();
        summary.setTotal(all.size());
        summary.setPending(pending);
        summary.setMastered(mastered);
        summary.setDueToday(due);
        summary.setWeeklyNew(mistakeService.getWeeklyNewCount(userId));
        summary.setAvgReviewCount(all.isEmpty() ? null
                : Math.round(reviewSum * 10.0 / all.size()) / 10.0);
        summary.setMaxReviewCount(all.isEmpty() ? 0 : maxReview);
        return summary;
    }

    private void accumulate(Map<String, long[]> target, String key, boolean correct) {
        if (key == null || key.isBlank()) {
            return;
        }
        long[] arr = target.computeIfAbsent(key, k -> new long[2]);
        arr[0]++;
        if (correct) {
            arr[1]++;
        }
    }

    private List<Bucket> toBuckets(Map<String, long[]> data, Map<String, String> labels, boolean sortByTotalDesc) {
        List<Bucket> list = new ArrayList<>();
        data.forEach((k, arr) -> list.add(new Bucket(k, labels.getOrDefault(k, k), arr[0], arr[1])));
        if (sortByTotalDesc) {
            list.sort(Comparator.comparing(Bucket::getTotal).reversed());
        }
        return list;
    }

    private static Double rate(long correct, long total) {
        return total == 0 ? null : Math.round(correct * 1000.0 / total) / 10.0;
    }
}
