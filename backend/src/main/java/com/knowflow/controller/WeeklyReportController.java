package com.knowflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.Result;
import com.knowflow.entity.DocReadProgress;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.entity.UserCheckIn;
import com.knowflow.entity.WeeklyReport;
import com.knowflow.mapper.DocReadProgressMapper;
import com.knowflow.mapper.LearningFlashcardMapper;
import com.knowflow.mapper.UserCheckInMapper;
import com.knowflow.mapper.WeeklyReportMapper;
import com.knowflow.service.AiService;
import com.knowflow.vo.WeeklyReportVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 学习周报接口。
 * 按自然周汇总学习统计，由 AI 生成周报摘要、本周成就与下周建议，支持历史列表与手动重新生成。
 */
@Slf4j
@Tag(name = "学习周报接口")
@RestController
@RequestMapping("/api/learning/weekly-report")
@RequiredArgsConstructor
public class WeeklyReportController {

    private final WeeklyReportMapper reportMapper;
    private final AiService aiService;
    private final UserCheckInMapper checkInMapper;
    private final LearningFlashcardMapper flashcardMapper;
    private final DocReadProgressMapper readProgressMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 历史周报列表回看周数 */
    private static final int HISTORY_WEEKS = 8;

    /** 获取当前周周报（weekStart=本周一），不存在则自动生成 */
    @Operation(summary = "获取当前周周报（不存在则自动生成）")
    @GetMapping
    public Result<WeeklyReportVO> current(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        LocalDate weekStart = currentMonday();
        WeeklyReport report = findReport(userId, weekStart);
        if (report == null) {
            report = generateWeeklyReport(userId);
        }
        return Result.success(toVO(report));
    }

    /** 获取历史周报列表（最近 8 周） */
    @Operation(summary = "历史周报列表（最近 8 周）")
    @GetMapping("/list")
    public Result<List<WeeklyReportVO>> list(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        LocalDate from = currentMonday().minusWeeks(HISTORY_WEEKS - 1L);
        List<WeeklyReport> reports = reportMapper.selectList(new LambdaQueryWrapper<WeeklyReport>()
                .eq(WeeklyReport::getUserId, userId)
                .ge(WeeklyReport::getWeekStart, from)
                .orderByDesc(WeeklyReport::getWeekStart));
        List<WeeklyReportVO> voList = reports.stream().map(this::toVO).collect(java.util.stream.Collectors.toList());
        return Result.success(voList);
    }

    /** 手动生成当前周周报（删除旧的重新生成） */
    @Operation(summary = "手动生成当前周周报")
    @PostMapping("/generate")
    public Result<WeeklyReportVO> generate(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        WeeklyReport report = generateWeeklyReport(userId);
        return Result.success(toVO(report));
    }

    /** 生成周报：聚合本周统计 + 调用 AI 生成摘要/成就/建议，并持久化 */
    private WeeklyReport generateWeeklyReport(Long userId) {
        LocalDate weekStart = currentMonday();
        LocalDate weekEnd = weekStart.plusDays(6);
        // 重新生成场景物理删除旧记录，避免唯一索引（user_id, week_start, deleted）冲突
        reportMapper.physicalDeleteByUserWeek(userId, weekStart);

        int studyMinutes = calcStudyMinutes(userId, weekStart, weekEnd);
        int checkinDays = calcCheckinDays(userId, weekStart, weekEnd);
        int flashcardReviewed = calcFlashcardReviewed(userId, weekStart, weekEnd);

        WeeklyAiContent content = callAiForReport(userId, studyMinutes, checkinDays, flashcardReviewed, weekStart, weekEnd);

        WeeklyReport report = new WeeklyReport();
        report.setUserId(userId);
        report.setWeekStart(weekStart);
        report.setWeekEnd(weekEnd);
        report.setSummary(content.summary);
        report.setStudyMinutes(studyMinutes);
        report.setCheckinDays(checkinDays);
        report.setFlashcardReviewed(flashcardReviewed);
        try {
            report.setAchievements(objectMapper.writeValueAsString(content.achievements));
            report.setSuggestions(objectMapper.writeValueAsString(content.suggestions));
        } catch (Exception e) {
            log.warn("周报成就/建议序列化失败: {}", e.getMessage());
            report.setAchievements("[]");
            report.setSuggestions("[]");
        }
        reportMapper.insert(report);
        return report;
    }

    /** 调用 AI 生成周报摘要 + 成就 + 下周建议，AI 不可用时降级返回模板内容 */
    @SuppressWarnings("unchecked")
    private WeeklyAiContent callAiForReport(Long userId, int studyMinutes, int checkinDays,
                                            int flashcardReviewed, LocalDate weekStart, LocalDate weekEnd) {
        String systemPrompt = "你是 KnowFlow 学习平台的 AI 学习周报助手，擅长总结学习数据并给出鼓励性、可执行的反馈。";
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("请根据以下本周学习数据生成一份学习周报。\n\n");
        userPrompt.append("周期：").append(weekStart).append(" 至 ").append(weekEnd).append("\n");
        userPrompt.append("本周学习数据：\n");
        userPrompt.append("- 学习时长（分钟）：").append(studyMinutes).append("\n");
        userPrompt.append("- 签到天数：").append(checkinDays).append("\n");
        userPrompt.append("- 闪卡复习数：").append(flashcardReviewed).append("\n\n");
        userPrompt.append("要求：\n");
        userPrompt.append("1. 返回严格 JSON 对象，格式：{\"summary\":\"周报摘要\",\"achievements\":[\"成就1\"],\"suggestions\":[\"建议1\"]}\n");
        userPrompt.append("2. summary：100-200 字的本周学习总结，鼓励为主\n");
        userPrompt.append("3. achievements：本周取得的成就列表（字符串数组，2-4 条）\n");
        userPrompt.append("4. suggestions：下周学习建议列表（字符串数组，2-4 条）\n");
        userPrompt.append("5. 不要输出 JSON 以外的任何文字\n");

        String raw;
        try {
            raw = aiService.complete(systemPrompt, userPrompt.toString(), null, userId);
        } catch (Exception e) {
            log.warn("AI 周报生成失败，降级返回模板内容: {}", e.getMessage());
            return fallbackContent(studyMinutes, checkinDays, flashcardReviewed);
        }
        if (raw == null || raw.isBlank()) {
            return fallbackContent(studyMinutes, checkinDays, flashcardReviewed);
        }
        try {
            String json = raw.trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return fallbackContent(studyMinutes, checkinDays, flashcardReviewed);
            }
            json = json.substring(start, end + 1);
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            WeeklyAiContent content = new WeeklyAiContent();
            content.summary = asString(map.get("summary"));
            content.achievements = toStringList(map.get("achievements"));
            content.suggestions = toStringList(map.get("suggestions"));
            if (content.summary == null || content.summary.isBlank()) {
                content.summary = defaultSummary(studyMinutes, checkinDays, flashcardReviewed);
            }
            return content;
        } catch (Exception e) {
            log.warn("周报 JSON 解析失败: {}", e.getMessage());
            return fallbackContent(studyMinutes, checkinDays, flashcardReviewed);
        }
    }

    /** AI 不可用时的降级内容：摘要用模板拼接统计数据，成就/建议返回空数组 */
    private WeeklyAiContent fallbackContent(int studyMinutes, int checkinDays, int flashcardReviewed) {
        WeeklyAiContent content = new WeeklyAiContent();
        content.summary = defaultSummary(studyMinutes, checkinDays, flashcardReviewed);
        content.achievements = Collections.emptyList();
        content.suggestions = Collections.emptyList();
        return content;
    }

    private String defaultSummary(int studyMinutes, int checkinDays, int flashcardReviewed) {
        return String.format("本周累计学习 %d 分钟，签到 %d 天，复习闪卡 %d 张。继续保持学习节奏，下周加油！",
                studyMinutes, checkinDays, flashcardReviewed);
    }

    /** 本周学习分钟：聚合 doc_read_progress 中 last_read_time 落在本周的 readSeconds/60 */
    private int calcStudyMinutes(Long userId, LocalDate weekStart, LocalDate weekEnd) {
        List<DocReadProgress> reads = readProgressMapper.selectList(new LambdaQueryWrapper<DocReadProgress>()
                .eq(DocReadProgress::getUserId, userId));
        int minutes = 0;
        for (DocReadProgress r : reads) {
            if (r.getLastReadTime() == null || r.getReadSeconds() == null) {
                continue;
            }
            LocalDate d = r.getLastReadTime().toLocalDate();
            if (d.isBefore(weekStart) || d.isAfter(weekEnd)) {
                continue;
            }
            minutes += r.getReadSeconds() / 60;
        }
        return minutes;
    }

    /** 本周签到天数：check_date 落在本周（周一至周日）的记录数 */
    private int calcCheckinDays(Long userId, LocalDate weekStart, LocalDate weekEnd) {
        long count = checkInMapper.selectCount(new LambdaQueryWrapper<UserCheckIn>()
                .eq(UserCheckIn::getUserId, userId)
                .ge(UserCheckIn::getCheckDate, weekStart)
                .le(UserCheckIn::getCheckDate, weekEnd));
        return (int) count;
    }

    /** 本周闪卡复习数：last_review_time 落在本周的闪卡数 */
    private int calcFlashcardReviewed(Long userId, LocalDate weekStart, LocalDate weekEnd) {
        LocalDateTime startDt = weekStart.atStartOfDay();
        LocalDateTime endDt = weekEnd.plusDays(1).atStartOfDay();
        List<LearningFlashcard> cards = flashcardMapper.selectList(new LambdaQueryWrapper<LearningFlashcard>()
                .eq(LearningFlashcard::getUserId, userId)
                .isNotNull(LearningFlashcard::getLastReviewTime)
                .ge(LearningFlashcard::getLastReviewTime, startDt)
                .lt(LearningFlashcard::getLastReviewTime, endDt));
        return cards.size();
    }

    /** 当前周一日期 */
    private LocalDate currentMonday() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** 查询某用户某周的周报 */
    private WeeklyReport findReport(Long userId, LocalDate weekStart) {
        return reportMapper.selectOne(new LambdaQueryWrapper<WeeklyReport>()
                .eq(WeeklyReport::getUserId, userId)
                .eq(WeeklyReport::getWeekStart, weekStart)
                .last("LIMIT 1"));
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object raw) {
        if (!(raw instanceof List)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (Object item : (List<Object>) raw) {
            if (item != null && !String.valueOf(item).isBlank()) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private WeeklyReportVO toVO(WeeklyReport report) {
        WeeklyReportVO vo = new WeeklyReportVO();
        vo.setId(report.getId());
        vo.setWeekStart(report.getWeekStart());
        vo.setWeekEnd(report.getWeekEnd());
        vo.setSummary(report.getSummary());
        vo.setStudyMinutes(report.getStudyMinutes());
        vo.setCheckinDays(report.getCheckinDays());
        vo.setFlashcardReviewed(report.getFlashcardReviewed());
        vo.setCreateTime(report.getCreateTime());
        vo.setUpdateTime(report.getUpdateTime());
        List<String> achievements = Collections.emptyList();
        List<String> suggestions = Collections.emptyList();
        try {
            if (report.getAchievements() != null && !report.getAchievements().isBlank()) {
                achievements = objectMapper.readValue(report.getAchievements(), new TypeReference<List<String>>() {});
            }
            if (report.getSuggestions() != null && !report.getSuggestions().isBlank()) {
                suggestions = objectMapper.readValue(report.getSuggestions(), new TypeReference<List<String>>() {});
            }
        } catch (Exception e) {
            log.warn("周报成就/建议反序列化失败: {}", e.getMessage());
        }
        vo.setAchievements(achievements);
        vo.setSuggestions(suggestions);
        return vo;
    }

    /** AI 生成的周报内容载体 */
    private static class WeeklyAiContent {
        String summary;
        List<String> achievements;
        List<String> suggestions;
    }
}
