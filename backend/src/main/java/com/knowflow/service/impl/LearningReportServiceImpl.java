package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.entity.CodeSubmitRecord;
import com.knowflow.entity.DocCategory;
import com.knowflow.entity.DocReadProgress;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.entity.LearningMistake;
import com.knowflow.entity.QuizAnswerRecord;
import com.knowflow.entity.UserCheckIn;
import com.knowflow.mapper.CodeSubmitRecordMapper;
import com.knowflow.mapper.DocCategoryMapper;
import com.knowflow.mapper.DocReadProgressMapper;
import com.knowflow.mapper.LearningFlashcardMapper;
import com.knowflow.mapper.LearningMistakeMapper;
import com.knowflow.mapper.QuizAnswerRecordMapper;
import com.knowflow.mapper.UserCheckInMapper;
import com.knowflow.service.LearningReportService;
import com.knowflow.vo.LearningReportVO;
import com.knowflow.vo.LearningReportVO.CategoryItem;
import com.knowflow.vo.LearningReportVO.DailyItem;
import com.knowflow.vo.LearningReportVO.WeeklyItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 学习报告业务服务实现：按周期聚合签到、闪卡、错题、代码、阅读、测验等多源学习数据。
 * 各表单独查询后在 Java 层聚合，不使用 JOIN，避免复杂 SQL。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningReportServiceImpl implements LearningReportService {

    /** all 周期统一从 2000-01-01 起算，等价于不限制。 */
    private static final LocalDate ALL_START = LocalDate.of(2000, 1, 1);

    /** dailyActivity 固定取最近 30 天。 */
    private static final int DAILY_DAYS = 30;

    /** weeklyTrend 固定取最近 8 周。 */
    private static final int WEEKLY_WEEKS = 8;

    /** 闪卡已掌握阈值：复习次数 >=3。 */
    private static final int FLASHCARD_MASTER_THRESHOLD = 3;

    /** 知识库掌握度 Top N。 */
    private static final int MASTERY_TOP_N = 5;

    private final UserCheckInMapper checkInMapper;
    private final LearningFlashcardMapper flashcardMapper;
    private final LearningMistakeMapper mistakeMapper;
    private final CodeSubmitRecordMapper codeSubmitRecordMapper;
    private final DocReadProgressMapper readProgressMapper;
    private final QuizAnswerRecordMapper quizAnswerRecordMapper;
    private final DocCategoryMapper categoryMapper;

    @Override
    public LearningReportVO getReport(Long userId, String period) {
        LocalDate end = LocalDate.now();
        LocalDate start = switch (period == null ? "month" : period.toLowerCase()) {
            case "week" -> end.minusDays(6);
            case "all" -> ALL_START;
            default -> end.minusDays(29);
        };
        // 归一化 period，避免前端传入异常值
        String normalizedPeriod = switch (period == null ? "month" : period.toLowerCase()) {
            case "week" -> "week";
            case "all" -> "all";
            default -> "month";
        };

        LearningReportVO vo = new LearningReportVO();
        vo.setPeriod(normalizedPeriod);
        vo.setStartDate(start.toString());
        vo.setEndDate(end.toString());

        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt = end.plusDays(1).atStartOfDay();

        // ===== 签到 =====
        List<UserCheckIn> checkIns = checkInMapper.selectList(new LambdaQueryWrapper<UserCheckIn>()
                .eq(UserCheckIn::getUserId, userId)
                .ge(UserCheckIn::getCheckDate, start)
                .le(UserCheckIn::getCheckDate, end));
        vo.setCheckinDays(checkIns.size());
        vo.setContinuousDays(currentContinuousDays(userId));

        // ===== 闪卡 =====
        List<LearningFlashcard> flashcards = flashcardMapper.selectList(
                new LambdaQueryWrapper<LearningFlashcard>().eq(LearningFlashcard::getUserId, userId));
        LocalDateTime now = LocalDateTime.now();
        int flashcardReviewed = 0;
        int flashcardMastered = 0;
        for (LearningFlashcard c : flashcards) {
            // 期内复习次数：上次复习时间落在周期内即计 1 次
            if (c.getLastReviewTime() != null
                    && !c.getLastReviewTime().isBefore(startDt)
                    && c.getLastReviewTime().isBefore(endDt)) {
                flashcardReviewed++;
            }
            // 已掌握快照：复习次数 >=3 且下次复习时间在未来
            int rc = c.getReviewCount() != null ? c.getReviewCount() : 0;
            if (rc >= FLASHCARD_MASTER_THRESHOLD
                    && c.getNextReviewTime() != null
                    && c.getNextReviewTime().isAfter(now)) {
                flashcardMastered++;
            }
        }
        vo.setFlashcardReviewed(flashcardReviewed);
        vo.setFlashcardMastered(flashcardMastered);

        // ===== 错题 =====
        List<LearningMistake> mistakes = mistakeMapper.selectList(new LambdaQueryWrapper<LearningMistake>()
                .eq(LearningMistake::getUserId, userId)
                .ge(LearningMistake::getCreateTime, startDt)
                .lt(LearningMistake::getCreateTime, endDt));
        vo.setMistakeCount(mistakes.size());
        // 已掌握错题数为当前快照（mastered=1），不按周期过滤
        long mistakeMastered = mistakeMapper.selectCount(new LambdaQueryWrapper<LearningMistake>()
                .eq(LearningMistake::getUserId, userId)
                .eq(LearningMistake::getMastered, 1));
        vo.setMistakeMastered((int) mistakeMastered);

        // ===== 代码提交 =====
        List<CodeSubmitRecord> submits = codeSubmitRecordMapper.selectList(
                new LambdaQueryWrapper<CodeSubmitRecord>()
                        .eq(CodeSubmitRecord::getUserId, userId)
                        .eq(CodeSubmitRecord::getDeleted, 0)
                        .ge(CodeSubmitRecord::getCreateTime, startDt)
                        .lt(CodeSubmitRecord::getCreateTime, endDt));
        vo.setCodeSubmissions(submits.size());
        int codePassed = 0;
        for (CodeSubmitRecord s : submits) {
            if (s.getPassed() != null && s.getPassed() == 1) {
                codePassed++;
            }
        }
        vo.setCodePassed(codePassed);

        // ===== 文档阅读 + 学习时长 =====
        List<DocReadProgress> reads = readProgressMapper.selectList(
                new LambdaQueryWrapper<DocReadProgress>().eq(DocReadProgress::getUserId, userId));
        int docsRead = 0;
        int studySeconds = 0;
        for (DocReadProgress r : reads) {
            BigDecimal progress = r.getProgress();
            if (progress != null && progress.compareTo(BigDecimal.ZERO) > 0) {
                docsRead++;
            }
            if (r.getReadSeconds() != null) {
                studySeconds += r.getReadSeconds();
            }
        }
        vo.setDocsRead(docsRead);
        vo.setStudyMinutes(studySeconds / 60);

        // ===== 测验答题 =====
        List<QuizAnswerRecord> quizzes = quizAnswerRecordMapper.selectList(
                new LambdaQueryWrapper<QuizAnswerRecord>()
                        .eq(QuizAnswerRecord::getUserId, userId)
                        .ge(QuizAnswerRecord::getCreateTime, startDt)
                        .lt(QuizAnswerRecord::getCreateTime, endDt));
        vo.setQuizAnswered(quizzes.size());
        int quizCorrect = 0;
        for (QuizAnswerRecord q : quizzes) {
            if (q.getIsCorrect() != null && q.getIsCorrect() == 1) {
                quizCorrect++;
            }
        }
        vo.setQuizCorrect(quizCorrect);

        // ===== 每日学习活跃度（最近 30 天） =====
        vo.setDailyActivity(buildDailyActivity(userId, end));

        // ===== 知识库掌握度 Top 5 =====
        vo.setCategoryMastery(buildCategoryMastery(userId));

        // ===== 最近 8 周趋势 =====
        vo.setWeeklyTrend(buildWeeklyTrend(userId, end));

        return vo;
    }

    /** 当前连续打卡天数：今日已打卡取今日记录；否则取昨日记录（streak 仍存活）；再否则为 0。 */
    private int currentContinuousDays(Long userId) {
        LocalDate today = LocalDate.now();
        UserCheckIn todayRecord = checkInMapper.selectOne(new LambdaQueryWrapper<UserCheckIn>()
                .eq(UserCheckIn::getUserId, userId)
                .eq(UserCheckIn::getCheckDate, today)
                .last("LIMIT 1"));
        if (todayRecord != null && todayRecord.getContinuousDays() != null) {
            return todayRecord.getContinuousDays();
        }
        UserCheckIn yesterdayRecord = checkInMapper.selectOne(new LambdaQueryWrapper<UserCheckIn>()
                .eq(UserCheckIn::getUserId, userId)
                .eq(UserCheckIn::getCheckDate, today.minusDays(1))
                .last("LIMIT 1"));
        return yesterdayRecord != null && yesterdayRecord.getContinuousDays() != null
                ? yesterdayRecord.getContinuousDays() : 0;
    }

    /** 构建最近 30 天每日学习活跃度：分钟数来自阅读时长，计数来自签到/复习/提交/答题/阅读事件。 */
    private List<DailyItem> buildDailyActivity(Long userId, LocalDate end) {
        LocalDate start = end.minusDays(DAILY_DAYS - 1L);
        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt = end.plusDays(1).atStartOfDay();

        // 阅读时长按日聚合（按 last_read_time 落点）
        Map<LocalDate, Integer> minutesByDay = new HashMap<>();
        List<DocReadProgress> reads = readProgressMapper.selectList(
                new LambdaQueryWrapper<DocReadProgress>().eq(DocReadProgress::getUserId, userId));
        for (DocReadProgress r : reads) {
            if (r.getLastReadTime() == null || r.getReadSeconds() == null) {
                continue;
            }
            LocalDate d = r.getLastReadTime().toLocalDate();
            if (d.isBefore(start) || d.isAfter(end)) {
                continue;
            }
            minutesByDay.merge(d, r.getReadSeconds() / 60, Integer::sum);
        }

        // 事件计数按日聚合
        Map<LocalDate, Integer> countByDay = new HashMap<>();
        // 签到
        for (UserCheckIn c : checkInMapper.selectList(new LambdaQueryWrapper<UserCheckIn>()
                .eq(UserCheckIn::getUserId, userId)
                .ge(UserCheckIn::getCheckDate, start)
                .le(UserCheckIn::getCheckDate, end))) {
            countByDay.merge(c.getCheckDate(), 1, Integer::sum);
        }
        // 闪卡复习
        for (LearningFlashcard c : flashcardMapper.selectList(
                new LambdaQueryWrapper<LearningFlashcard>().eq(LearningFlashcard::getUserId, userId))) {
            if (c.getLastReviewTime() == null) {
                continue;
            }
            LocalDate d = c.getLastReviewTime().toLocalDate();
            if (!d.isBefore(start) && !d.isAfter(end)) {
                countByDay.merge(d, 1, Integer::sum);
            }
        }
        // 代码提交
        for (CodeSubmitRecord s : codeSubmitRecordMapper.selectList(
                new LambdaQueryWrapper<CodeSubmitRecord>()
                        .eq(CodeSubmitRecord::getUserId, userId)
                        .eq(CodeSubmitRecord::getDeleted, 0)
                        .ge(CodeSubmitRecord::getCreateTime, startDt)
                        .lt(CodeSubmitRecord::getCreateTime, endDt))) {
            if (s.getCreateTime() != null) {
                countByDay.merge(s.getCreateTime().toLocalDate(), 1, Integer::sum);
            }
        }
        // 测验答题
        for (QuizAnswerRecord q : quizAnswerRecordMapper.selectList(
                new LambdaQueryWrapper<QuizAnswerRecord>()
                        .eq(QuizAnswerRecord::getUserId, userId)
                        .ge(QuizAnswerRecord::getCreateTime, startDt)
                        .lt(QuizAnswerRecord::getCreateTime, endDt))) {
            if (q.getCreateTime() != null) {
                countByDay.merge(q.getCreateTime().toLocalDate(), 1, Integer::sum);
            }
        }
        // 阅读事件（每条进度记录按 last_read_time 落点计 1 次）
        for (DocReadProgress r : reads) {
            if (r.getLastReadTime() == null) {
                continue;
            }
            LocalDate d = r.getLastReadTime().toLocalDate();
            if (!d.isBefore(start) && !d.isAfter(end)) {
                countByDay.merge(d, 1, Integer::sum);
            }
        }

        List<DailyItem> result = new ArrayList<>(DAILY_DAYS);
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            DailyItem item = new DailyItem();
            item.setDate(d.toString());
            item.setMinutes(minutesByDay.getOrDefault(d, 0));
            item.setCount(countByDay.getOrDefault(d, 0));
            result.add(item);
        }
        return result;
    }

    /**
     * 构建知识库掌握度 Top 5：
     * 闪卡按 category_id 分组（关联 doc_category 取 name），
     * 错题按 category 字符串分组，按名称合并 {total, mastered}。
     * 闪卡 mastered：review_count>=3 且 next_review_time 在未来；
     * 错题 mastered：mastered=1。
     */
    private List<CategoryItem> buildCategoryMastery(Long userId) {
        Map<String, int[]> bucket = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();

        // 闪卡按 category_id 聚合
        List<LearningFlashcard> flashcards = flashcardMapper.selectList(
                new LambdaQueryWrapper<LearningFlashcard>().eq(LearningFlashcard::getUserId, userId));
        Set<Long> categoryIds = new HashSet<>();
        for (LearningFlashcard c : flashcards) {
            if (c.getCategoryId() != null) {
                categoryIds.add(c.getCategoryId());
            }
        }
        Map<Long, String> nameById = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            for (DocCategory cat : categoryMapper.selectBatchIds(categoryIds)) {
                nameById.put(cat.getId(), cat.getName());
            }
        }
        for (LearningFlashcard c : flashcards) {
            if (c.getCategoryId() == null) {
                continue;
            }
            String name = nameById.get(c.getCategoryId());
            if (name == null || name.isBlank()) {
                continue;
            }
            int[] arr = bucket.computeIfAbsent(name, k -> new int[2]);
            arr[0]++;
            int rc = c.getReviewCount() != null ? c.getReviewCount() : 0;
            if (rc >= FLASHCARD_MASTER_THRESHOLD
                    && c.getNextReviewTime() != null
                    && c.getNextReviewTime().isAfter(now)) {
                arr[1]++;
            }
        }

        // 错题按 category 字符串聚合
        List<LearningMistake> mistakes = mistakeMapper.selectList(
                new LambdaQueryWrapper<LearningMistake>().eq(LearningMistake::getUserId, userId));
        for (LearningMistake m : mistakes) {
            String name = m.getCategory();
            if (name == null || name.isBlank()) {
                continue;
            }
            int[] arr = bucket.computeIfAbsent(name, k -> new int[2]);
            arr[0]++;
            if (m.getMastered() != null && m.getMastered() == 1) {
                arr[1]++;
            }
        }

        return bucket.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]))
                .limit(MASTERY_TOP_N)
                .map(e -> {
                    CategoryItem item = new CategoryItem();
                    item.setCategoryName(e.getKey());
                    item.setTotal(e.getValue()[0]);
                    item.setMastered(e.getValue()[1]);
                    item.setPercent(e.getValue()[0] > 0
                            ? e.getValue()[1] * 100 / e.getValue()[0] : 0);
                    return item;
                })
                .collect(Collectors.toList());
    }

    /** 构建最近 8 周趋势：按自然周（周一为起点）聚合学习时长与签到天数。 */
    private List<WeeklyItem> buildWeeklyTrend(Long userId, LocalDate end) {
        // 以 end 所在周的周一为最后一周起点，向前回溯 7 周
        LocalDate lastWeekMonday = end.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate firstWeekMonday = lastWeekMonday.minusWeeks(WEEKLY_WEEKS - 1L);

        // 阅读时长按周聚合
        Map<LocalDate, Integer> minutesByWeek = new HashMap<>();
        List<DocReadProgress> reads = readProgressMapper.selectList(
                new LambdaQueryWrapper<DocReadProgress>().eq(DocReadProgress::getUserId, userId));
        for (DocReadProgress r : reads) {
            if (r.getLastReadTime() == null || r.getReadSeconds() == null) {
                continue;
            }
            LocalDate d = r.getLastReadTime().toLocalDate();
            if (d.isBefore(firstWeekMonday)) {
                continue;
            }
            LocalDate monday = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            minutesByWeek.merge(monday, r.getReadSeconds() / 60, Integer::sum);
        }

        // 签到天数按周聚合
        Map<LocalDate, Integer> checkinByWeek = new HashMap<>();
        List<UserCheckIn> checkIns = checkInMapper.selectList(new LambdaQueryWrapper<UserCheckIn>()
                .eq(UserCheckIn::getUserId, userId)
                .ge(UserCheckIn::getCheckDate, firstWeekMonday));
        for (UserCheckIn c : checkIns) {
            if (c.getCheckDate() == null) {
                continue;
            }
            LocalDate monday = c.getCheckDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            checkinByWeek.merge(monday, 1, Integer::sum);
        }

        List<WeeklyItem> result = new ArrayList<>(WEEKLY_WEEKS);
        for (int i = 0; i < WEEKLY_WEEKS; i++) {
            LocalDate monday = firstWeekMonday.plusWeeks(i);
            WeeklyItem item = new WeeklyItem();
            item.setWeekStart(monday.toString());
            item.setStudyMinutes(minutesByWeek.getOrDefault(monday, 0));
            item.setCheckinDays(checkinByWeek.getOrDefault(monday, 0));
            result.add(item);
        }
        return result;
    }
}
