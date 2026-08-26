package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.*;
import com.knowflow.mapper.*;
import com.knowflow.service.AiService;
import com.knowflow.service.LearningPlanService;
import com.knowflow.vo.LearningPlanVO;
import com.knowflow.vo.PlanBlockVO;
import com.knowflow.vo.PlanItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * F3 学习计划智能编排：服务实现。
 * <p>
 * 两条主线：
 * 1) 「今天」入口（GET /today）：懒生成（单天），实时回填完成状态；
 * 2) 「批量生成」入口（POST /generate）：范围 days（默认 7，≤30），force=true 先物理删除。
 * <p>
 * 编排：
 * - 候选项：学习章节（已采用未完成前 3 条路径） / todo / 习惯。
 * - AI 调用：AiService.complete(systemPrompt, userPrompt, model, userId) 超时/异常/非 JSON → 走确定性兜底。
 * - 兜底算法：章节顺序平铺 → 上午，todo → 下午，习惯 → 下午 8 点前；每项默认 30min。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningPlanServiceImpl implements LearningPlanService {

    // ========================= Mapper / Services =========================
    private final LearningPlanMapper planMapper;
    private final LearningUserPathMapper userPathMapper;
    private final LearningChapterMapper chapterMapper;
    private final LearningUserChapterMapper userChapterMapper;
    private final LearningTaskMapper learningTaskMapper;
    private final TaskMapper taskMapper;
    private final HabitMapper habitMapper;
    private final HabitCheckInMapper habitCheckInMapper;
    private final UserLearningPrefMapper userLearningPrefMapper;

    private final AiService aiService;

    // 可安全共享；findAndRegisterModules 用于 JSR310
    private final ObjectMapper objectMapper;

    // ========================= 常量 =========================

    /** 默认每日可用分钟（UserLearningPref 为空时使用）。 */
    private static final int DEFAULT_DAILY_MINUTES = 120;

    /** 生成范围上限，对应 spec NFR3.3 与 calendar range 上限。 */
    private static final int MAX_GENERATE_DAYS = 30;

    /** 并发学习路径数上限（候选池），对应假设 A1。 */
    private static final int MAX_ENROLLED_PATHS = 3;

    /** 三时段元数据：timeSlot / label / start / end。 */
    private static final SlotMeta[] SLOTS = new SlotMeta[] {
            new SlotMeta("morning",   "上午 \uD83C\uDF05", "07:00", "12:00"),
            new SlotMeta("afternoon", "下午 ☀️", "13:00", "18:00"),
            new SlotMeta("evening",   "晚间 \uD83C\uDF19", "19:00", "23:00"),
    };

    // ========================= 接口实现 =========================

    @Override
    public LearningPlanVO getTodayPlan(Long userId) {
        Objects.requireNonNull(userId, "userId");
        LocalDate today = LocalDate.now();
        LearningPlan record = findPlanByDate(userId, today);
        if (record == null) {
            log.info("[LearningPlan] 今日无计划，同步生成单日计划 userId={} date={}", userId, today);
            int n = generateForRange(userId, today, 1, Boolean.TRUE);
            if (n <= 0) {
                log.warn("[LearningPlan] 同步生成失败（侯选池为空或写入异常），返回空 VO userId={}", userId);
                return buildEmptyVO(today);
            }
            record = findPlanByDate(userId, today);
        }
        if (record == null) {
            return buildEmptyVO(today);
        }
        LearningPlanVO vo = parsePlanToVO(record);
        fillCompletionStatus(vo, userId);
        recomputeCompletedRatio(vo);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int generateForRange(Long userId, LocalDate startDate, Integer days, Boolean force) {
        Objects.requireNonNull(userId, "userId");
        LocalDate s = startDate == null ? LearningPlanService.defaultStartDate() : startDate;
        int d = (days == null) ? 7 : Math.min(Math.max(1, days), MAX_GENERATE_DAYS);
        boolean f = force != null && force;
        LocalDate end = s.plusDays(d - 1);

        log.info("[LearningPlan] generateForRange userId={} [{}~{}] days={} force={}", userId, s, end, d, f);

        CandidatePool pool = fetchCandidatePool(userId);
        UserLearningPref pref = fetchPref(userId);
        int dailyMinutes = Optional.ofNullable(pref.getFocusMinutes()).filter(v -> v > 0)
                .orElse(DEFAULT_DAILY_MINUTES);

        // 1) AI 编排：期望返回 7 天/1 天 × 3 blocks × items
        List<PlanDayDTO> planDays;
        try {
            planDays = callAiOrchestrate(userId, pool, dailyMinutes, s, d);
        } catch (Exception e) {
            log.warn("[LearningPlan] AI 编排失败，启用兜底算法 userId={} cause={}: {}",
                    userId, e.getClass().getSimpleName(), e.getMessage());
            planDays = fallbackOrchestrate(pool, dailyMinutes, s, d);
        }
        if (planDays == null || planDays.isEmpty()) {
            planDays = fallbackOrchestrate(pool, dailyMinutes, s, d);
        }

        // 2) 入库：按 (userId, date) 维度 upsert / force 删除
        int generated = 0;
        if (f) {
            int del = planMapper.physicalDeleteByDateRange(userId, s, end);
            log.info("[LearningPlan] force=true 物理删除 {} 行 userId={} [{}~{}]", del, userId, s, end);
        }
        for (PlanDayDTO pd : planDays) {
            if (pd.getDate().isBefore(s) || pd.getDate().isAfter(end)) continue;
            if (!f) {
                LearningPlan exist = findPlanByDate(userId, pd.getDate());
                if (exist != null) continue; // 幂等跳过
            }
            LearningPlan entity = buildEntity(userId, pd);
            int ok = planMapper.insert(entity);
            if (ok > 0) generated++;
        }
        log.info("[LearningPlan] generateForRange 实际生成 {} 天 userId={}", generated, userId);
        // WeeklyReport hook（开放问题 Q1，本期只打日志）
        log.info("[LearningPlan][Hook] weekly report refresh hook: userId={}", userId);
        return generated;
    }

    @Override
    public String exportCalendarIcs(Long userId, LocalDate baseDate, Integer rangeDays) {
        Objects.requireNonNull(userId, "userId");
        LocalDate base = baseDate == null ? LocalDate.now() : baseDate;
        int range = (rangeDays == null) ? 1 : Math.min(Math.max(1, rangeDays), MAX_GENERATE_DAYS);
        if (range < rangeDays) {
            log.warn("[LearningPlan] ICS 导出范围被裁剪 {}→{} (上限30) userId={}", rangeDays, range, userId);
        }
        LocalDate end = base.plusDays(range - 1);
        List<LearningPlan> records = planMapper.selectList(new LambdaQueryWrapper<LearningPlan>()
                .eq(LearningPlan::getUserId, userId)
                .ge(LearningPlan::getPlanDate, base)
                .le(LearningPlan::getPlanDate, end)
                .orderByAsc(LearningPlan::getPlanDate));

        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//KnowFlow//ZH//Learning Plan//1.0\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:PUBLISH\r\n");
        sb.append("X-WR-CALNAME:KnowFlow 学习计划\r\n");
        sb.append("X-WR-TIMEZONE:Asia/Shanghai\r\n");

        int seq = 0;
        DateTimeFormatter icsFmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
        for (LearningPlan p : records) {
            LearningPlanVO vo;
            try {
                vo = parsePlanToVO(p);
            } catch (Exception ex) {
                log.warn("[LearningPlan] ICS 解析计划失败 planId={} {}", p.getId(), ex.getMessage());
                continue;
            }
            for (PlanBlockVO b : vo.getBlocks()) {
                LocalTime blockStart = parseLocalTime(b.getStartTime(), LocalTime.parse(SLOTS[0].start));
                for (PlanItemVO it : b.getItems()) {
                    seq++;
                    LocalTime istart = parseLocalTime(it.getStartTime(), blockStart);
                    int dur = (it.getDuration() == null || it.getDuration() <= 0) ? 30 : it.getDuration();
                    LocalTime iend = parseLocalTime(it.getEndTime(), istart.plusMinutes(dur));

                    ZonedDateTime startZoned = ZonedDateTime.of(p.getPlanDate(), istart, ZoneId.of("Asia/Shanghai"))
                            .withZoneSameInstant(ZoneOffset.UTC);
                    ZonedDateTime endZoned = ZonedDateTime.of(p.getPlanDate(), iend, ZoneId.of("Asia/Shanghai"))
                            .withZoneSameInstant(ZoneOffset.UTC);
                    // 跨日修正
                    if (!endZoned.toLocalDate().equals(startZoned.toLocalDate())) {
                        endZoned = ZonedDateTime.of(p.getPlanDate(), LocalTime.of(23, 0), ZoneId.of("Asia/Shanghai"))
                                .withZoneSameInstant(ZoneOffset.UTC);
                    }

                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:knowflow-plan-").append(p.getId()).append('-').append(seq).append("@knowflow.local\r\n");
                    sb.append("DTSTAMP:").append(Instant.now().atOffset(ZoneOffset.UTC).format(icsFmt)).append("\r\n");
                    sb.append("DTSTART:").append(startZoned.format(icsFmt)).append("\r\n");
                    sb.append("DTEND:").append(endZoned.format(icsFmt)).append("\r\n");
                    String type = Optional.ofNullable(it.getType()).orElse("item");
                    String summaryPrefix = switch (type) {
                        case "learningTask" -> "[学习]";
                        case "todo" -> "[任务]";
                        case "habit" -> "[习惯]";
                        default -> "[计划]";
                    };
                    sb.append("SUMMARY:").append(icsEscape(summaryPrefix + " " + Optional.ofNullable(it.getTitle()).orElse("未命名"))).append("\r\n");
                    sb.append("DESCRIPTION:")
                            .append(icsEscape("时段：" + Optional.ofNullable(b.getLabel()).orElse(b.getTimeSlot())))
                            .append("\r\n");
                    sb.append("END:VEVENT\r\n");
                }
            }
        }
        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    // ========================= 内部：候选项池 =========================

    /** 候选项池：章节/任务/习惯 + 偏好。 */
    @lombok.Data
    public static class CandidatePool {
        List<ChapterCandidate> chapters = new ArrayList<>();
        List<TaskCandidate> todos = new ArrayList<>();
        List<HabitCandidate> habits = new ArrayList<>();
    }
    public record ChapterCandidate(Long chapterId, Long pathId, String title, Integer duration, int sortOrder) {}
    public record TaskCandidate(Long taskId, String title, Integer duration,
                                LocalDate scheduledDate, Integer urgent, Integer important) {}
    public record HabitCandidate(Long habitId, String name, String reminderTime) {}

    CandidatePool fetchCandidatePool(Long userId) {
        CandidatePool pool = new CandidatePool();
        // 1) 已采用学习路径（enrolled）：按 enrollTime 升序取前 3
        List<LearningUserPath> enrolled = userPathMapper.selectList(new LambdaQueryWrapper<LearningUserPath>()
                .eq(LearningUserPath::getUserId, userId)
                .lt(LearningUserPath::getProgress, new BigDecimal("1.00"))
                .orderByAsc(LearningUserPath::getEnrollTime))
                .stream()
                .limit(MAX_ENROLLED_PATHS)
                .toList();
        Set<Long> completedChapterIds = new HashSet<>();
        userChapterMapper.selectList(new LambdaQueryWrapper<LearningUserChapter>()
                        .eq(LearningUserChapter::getUserId, userId)
                        .isNotNull(LearningUserChapter::getCompleteTime))
                .forEach(c -> completedChapterIds.add(c.getChapterId()));
        for (LearningUserPath up : enrolled) {
            List<LearningChapter> chapters = chapterMapper.selectList(new LambdaQueryWrapper<LearningChapter>()
                    .eq(LearningChapter::getPathId, up.getPathId())
                    .orderByAsc(LearningChapter::getSortOrder));
            // 取前 N 个未完成章节：最多 14 条（够一周 2/天）
            int taken = 0;
            for (LearningChapter c : chapters) {
                if (completedChapterIds.contains(c.getId())) continue;
                pool.chapters.add(new ChapterCandidate(c.getId(), c.getPathId(), c.getTitle(),
                        c.getDuration() == null ? 30 : c.getDuration(), c.getSortOrder() == null ? taken : c.getSortOrder()));
                if (++taken >= 14) break;
            }
        }
        // 2) Todo：scheduled_date <= today || null，status != 1，sort by due/scheduled/urgent/important
        LocalDate today = LocalDate.now();
        List<Task> tasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getUserId, userId)
                .ne(Task::getStatus, 1)
                .and(w -> w.isNull(Task::getSomeday).or().ne(Task::getSomeday, 1)));
        for (Task t : tasks) {
            if (t.getScheduledDate() != null && t.getScheduledDate().isAfter(today)) continue;
            int dur = t.getStartTime() != null && t.getEndTime() != null
                    ? (int) Duration.between(t.getStartTime(), t.getEndTime()).toMinutes()
                    : 30;
            pool.todos.add(new TaskCandidate(t.getId(), t.getTitle(), dur,
                    t.getScheduledDate(),
                    t.getUrgent() == null ? 0 : t.getUrgent(),
                    t.getImportant() == null ? 0 : t.getImportant()));
        }
        pool.todos.sort(Comparator
                .comparing((TaskCandidate t) -> t.scheduledDate() == null ? LocalDate.MAX : t.scheduledDate())
                .thenComparing((TaskCandidate t) -> -(t.urgent() + t.important())));
        // 3) Habit：active=1 && (frequency=daily || null)
        List<Habit> habits = habitMapper.selectList(new LambdaQueryWrapper<Habit>()
                .eq(Habit::getUserId, userId)
                .eq(Habit::getActive, 1)
                .and(w -> w.eq(Habit::getFrequency, "daily").or().isNull(Habit::getFrequency))
                .orderByAsc(Habit::getSortOrder));
        for (Habit h : habits) {
            pool.habits.add(new HabitCandidate(h.getId(), h.getName(), h.getReminderTime()));
        }
        log.info("[LearningPlan] 候选池 userId={} chapters={} todos={} habits={}",
                userId, pool.chapters.size(), pool.todos.size(), pool.habits.size());
        return pool;
    }

    private UserLearningPref fetchPref(Long userId) {
        UserLearningPref p = userLearningPrefMapper.selectOne(new LambdaQueryWrapper<UserLearningPref>()
                .eq(UserLearningPref::getUserId, userId));
        return p == null ? new UserLearningPref() : p;
    }

    // ========================= 内部：AI 编排 + 兜底 =========================

    @lombok.Data
    public static class PlanDayDTO {
        LocalDate date;
        List<PlanBlockDTO> blocks = new ArrayList<>();
    }
    @lombok.Data
    public static class PlanBlockDTO {
        String timeSlot;
        String startTime;
        String endTime;
        List<PlanItemDTO> items = new ArrayList<>();
    }
    @lombok.Data
    public static class PlanItemDTO {
        String type; // learningTask | todo | habit
        Long chapterId;
        Long learningTaskId;
        Long taskId;
        Long habitId;
        String title;
        Integer duration;
        String startTime;
        String endTime;
    }

    /**
     * AI 编排：任何异常 → fallback。
     */
    List<PlanDayDTO> callAiOrchestrate(Long userId, CandidatePool pool, int dailyMinutes,
                                        LocalDate startDate, int days) {
        if (aiService == null || !Optional.ofNullable(aiService.isConfigured()).orElse(false)) {
            log.warn("[LearningPlan] AI 未配置，走兜底 userId={}", userId);
            return fallbackOrchestrate(pool, dailyMinutes, startDate, days);
        }
        String sysPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(pool, dailyMinutes, startDate, days);
        String raw;
        try {
            raw = aiService.complete(sysPrompt, userPrompt, null, userId);
        } catch (Exception e) {
            log.warn("[LearningPlan] AiService.complete 失败 userId={}: {} {}",
                    userId, e.getClass().getSimpleName(), e.getMessage());
            return fallbackOrchestrate(pool, dailyMinutes, startDate, days);
        }
        if (raw == null || raw.isBlank()) {
            log.warn("[LearningPlan] AI 返回空，走兜底 userId={}", userId);
            return fallbackOrchestrate(pool, dailyMinutes, startDate, days);
        }
        // 解析 JSON：优先取 ```json ... ``` 代码块内容
        String json = extractJson(raw);
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode daysNode = root.has("days") ? root.get("days") : root;
            List<PlanDayDTO> result = new ArrayList<>();
            Iterator<JsonNode> it = daysNode.elements();
            LocalDate cursor = startDate;
            int i = 0;
            while (it.hasNext() && i < days) {
                JsonNode dn = it.next();
                LocalDate d = dn.has("date") ? LocalDate.parse(dn.get("date").asText()) : cursor;
                PlanDayDTO pd = new PlanDayDTO();
                pd.setDate(d);
                JsonNode blocksNode = dn.get("blocks");
                if (blocksNode != null && blocksNode.isArray()) {
                    for (JsonNode bn : blocksNode) {
                        PlanBlockDTO pb = new PlanBlockDTO();
                        pb.setTimeSlot(textOf(bn, "timeSlot", slotByIndex(pd.getBlocks().size()).timeSlot));
                        pb.setStartTime(textOf(bn, "startTime", slotByName(pb.getTimeSlot()).start));
                        pb.setEndTime(textOf(bn, "endTime", slotByName(pb.getTimeSlot()).end));
                        JsonNode itemsNode = bn.get("items");
                        if (itemsNode != null && itemsNode.isArray()) {
                            for (JsonNode in_ : itemsNode) {
                                PlanItemDTO pi = new PlanItemDTO();
                                pi.setType(normalizeType(textOf(in_, "type", "learningTask")));
                                pi.setTitle(textOf(in_, "title", "计划项"));
                                pi.setDuration(intOf(in_, "duration", 30));
                                pi.setStartTime(textOf(in_, "startTime", null));
                                pi.setEndTime(textOf(in_, "endTime", null));
                                if (in_.has("chapterId")) pi.setChapterId(in_.get("chapterId").asLong());
                                if (in_.has("learningTaskId")) pi.setLearningTaskId(in_.get("learningTaskId").asLong());
                                if (in_.has("taskId")) pi.setTaskId(in_.get("taskId").asLong());
                                if (in_.has("habitId")) pi.setHabitId(in_.get("habitId").asLong());
                                pb.getItems().add(pi);
                            }
                        }
                        pd.getBlocks().add(pb);
                    }
                }
                // 补齐三块（兜底：若 AI 少给 blocks 则补空）
                while (pd.getBlocks().size() < 3) {
                    SlotMeta sm = slotByIndex(pd.getBlocks().size());
                    PlanBlockDTO pb = new PlanBlockDTO();
                    pb.setTimeSlot(sm.timeSlot);
                    pb.setStartTime(sm.start);
                    pb.setEndTime(sm.end);
                    pd.getBlocks().add(pb);
                }
                result.add(pd);
                cursor = d.plusDays(1);
                i++;
            }
            // 如果返回天数不够（<days），用空块补齐到 days
            while (result.size() < days) {
                PlanDayDTO pd = new PlanDayDTO();
                pd.setDate(cursor);
                for (int s = 0; s < 3; s++) {
                    SlotMeta sm = slotByIndex(s);
                    PlanBlockDTO pb = new PlanBlockDTO();
                    pb.setTimeSlot(sm.timeSlot);
                    pb.setStartTime(sm.start);
                    pb.setEndTime(sm.end);
                    pd.getBlocks().add(pb);
                }
                result.add(pd);
                cursor = cursor.plusDays(1);
            }
            log.info("[LearningPlan] AI 编排解析成功 userId={} days={}", userId, result.size());
            return result;
        } catch (JsonProcessingException e) {
            String truncated = json.length() > 300 ? json.substring(0, 300) : json;
            log.warn("[LearningPlan] AI JSON 解析失败，走兜底 userId={} 截断响应：{}",
                    userId, truncated, e);
            return fallbackOrchestrate(pool, dailyMinutes, startDate, days);
        }
    }

    private static String buildSystemPrompt() {
        return """
                你是一个学习计划编排助手。目标：把用户的候选章节、待办任务、打卡习惯，
                按早/午/晚三时段，拆成 N 天（通常 7 或 1）的日级执行清单，
                控制单天总时长不超过 dailyMinutes。
                严格返回 JSON，不要解释文字，不要 markdown 代码块包裹以外的文字。
                字段契约：
                {"days": [{
                  "date":"YYYY-MM-DD",
                  "blocks":[
                    {"timeSlot":"morning","startTime":"07:30","endTime":"09:30",
                     "items":[
                       {"type":"learningTask","chapterId":12,"title":"xxx","duration":60,"startTime":"07:30","endTime":"08:30"}
                     ]},
                    {"timeSlot":"afternoon","items":[
                       {"type":"todo","taskId":45,"title":"xxx","duration":40}
                    ]},
                    {"timeSlot":"evening","items":[
                       {"type":"habit","habitId":3,"title":"xxx","duration":30}
                    ]}
                  ]
                }]}
                规则：
                1) 高耗时学习任务放 morning；下午放 todo + 复习型章节；晚间放 habit + 轻量章节。
                2) 类型必须严格是 learningTask / todo / habit，且对应 ID 字段必须来自候选项池。
                3) 若某时段无任务，返回空数组，不要省略整个 block。
                """;
    }

    private String buildUserPrompt(CandidatePool pool, int dailyMinutes, LocalDate startDate, int days) {
        StringBuilder sb = new StringBuilder();
        sb.append("【基础】\nstartDate=").append(startDate).append(" days=").append(days)
                .append(" dailyMinutes=").append(dailyMinutes).append('\n');
        sb.append("【章节候选】\n");
        for (ChapterCandidate c : pool.chapters) {
            sb.append("- chapterId=").append(c.chapterId())
                    .append(" title=").append(c.title())
                    .append(" duration=").append(c.duration()).append('\n');
        }
        if (pool.chapters.isEmpty()) sb.append("（无）\n");
        sb.append("【任务候选】\n");
        for (TaskCandidate t : pool.todos) {
            sb.append("- taskId=").append(t.taskId())
                    .append(" title=").append(t.title())
                    .append(" duration=").append(t.duration())
                    .append(" scheduled=").append(t.scheduledDate())
                    .append(" urgent=").append(t.urgent()).append(" important=").append(t.important()).append('\n');
        }
        if (pool.todos.isEmpty()) sb.append("（无）\n");
        sb.append("【习惯候选】\n");
        for (HabitCandidate h : pool.habits) {
            sb.append("- habitId=").append(h.habitId())
                    .append(" name=").append(h.name())
                    .append(" reminderTime=").append(h.reminderTime()).append('\n');
        }
        if (pool.habits.isEmpty()) sb.append("（无）\n");
        sb.append("请返回 days 数组，date 从 ").append(startDate).append(" 起连续 ")
                .append(days).append(" 天。总时长严格≤ dailyMinutes。");
        return sb.toString();
    }

    /** 确定性兜底算法：chapters → morning；todos → afternoon；habits → evening 或下午末段。 */
    List<PlanDayDTO> fallbackOrchestrate(CandidatePool pool, int dailyMinutes, LocalDate startDate, int days) {
        // 候选队列拷贝
        List<ChapterCandidate> chs = new ArrayList<>(pool.chapters);
        List<TaskCandidate> todos = new ArrayList<>(pool.todos);
        List<HabitCandidate> habits = new ArrayList<>(pool.habits);
        List<PlanDayDTO> result = new ArrayList<>(days);

        for (int d = 0; d < days; d++) {
            LocalDate date = startDate.plusDays(d);
            PlanDayDTO pd = new PlanDayDTO();
            pd.setDate(date);
            int remaining = dailyMinutes;

            // morning: 学习章节（占 60% 可用时间）
            PlanBlockDTO m = newBlock("morning");
            int morningBudget = (int) (dailyMinutes * 0.6);
            while (remaining > 0 && !chs.isEmpty()) {
                int used = usedMinutesOfBlock(m);
                if (used >= morningBudget) break;
                ChapterCandidate c = chs.remove(0);
                int dur = (c.duration() == null || c.duration() <= 0) ? 30 : c.duration();
                if (used + dur > morningBudget && used > 0) break;
                PlanItemDTO pi = new PlanItemDTO();
                pi.setType("learningTask");
                pi.setChapterId(c.chapterId());
                pi.setTitle(c.title());
                pi.setDuration(dur);
                assignTimes(pi, m, dur);
                m.getItems().add(pi);
                remaining -= dur;
            }
            pd.getBlocks().add(m);

            // afternoon: todo + 溢出的章节
            PlanBlockDTO a = newBlock("afternoon");
            int afternoonBudget = (int) (dailyMinutes * 0.3);
            while (usedMinutesOfBlock(a) < afternoonBudget && !todos.isEmpty()) {
                TaskCandidate t = todos.remove(0);
                int dur = (t.duration() == null || t.duration() <= 0) ? 30 : t.duration();
                PlanItemDTO pi = new PlanItemDTO();
                pi.setType("todo");
                pi.setTaskId(t.taskId());
                pi.setTitle(t.title());
                pi.setDuration(dur);
                assignTimes(pi, a, dur);
                a.getItems().add(pi);
                remaining -= dur;
            }
            // 溢出章节
            while (usedMinutesOfBlock(a) < afternoonBudget && !chs.isEmpty()) {
                ChapterCandidate c = chs.remove(0);
                int dur = (c.duration() == null || c.duration() <= 0) ? 30 : c.duration();
                PlanItemDTO pi = new PlanItemDTO();
                pi.setType("learningTask");
                pi.setChapterId(c.chapterId());
                pi.setTitle(c.title());
                pi.setDuration(dur);
                assignTimes(pi, a, dur);
                a.getItems().add(pi);
                remaining -= dur;
            }
            pd.getBlocks().add(a);

            // evening: habits + 轻微溢出
            PlanBlockDTO e = newBlock("evening");
            for (HabitCandidate h : habits) {
                PlanItemDTO pi = new PlanItemDTO();
                pi.setType("habit");
                pi.setHabitId(h.habitId());
                pi.setTitle(h.name());
                pi.setDuration(30);
                if (h.reminderTime() != null && !h.reminderTime().isBlank()) {
                    // 使用提醒时间作 start
                    pi.setStartTime(h.reminderTime());
                    try {
                        LocalTime st = LocalTime.parse(h.reminderTime());
                        pi.setEndTime(st.plusMinutes(30).format(DateTimeFormatter.ofPattern("HH:mm")));
                    } catch (Exception ignored) {
                        pi.setEndTime("20:00");
                    }
                } else {
                    assignTimes(pi, e, 30);
                }
                e.getItems().add(pi);
                remaining -= 30;
            }
            pd.getBlocks().add(e);
            result.add(pd);
        }
        log.info("[LearningPlan] 兜底算法生成 {} 天计划 startDate={}", result.size(), startDate);
        return result;
    }

    // ========================= 内部：入库/出库 =========================

    private LearningPlan findPlanByDate(Long userId, LocalDate d) {
        return planMapper.selectOne(new LambdaQueryWrapper<LearningPlan>()
                .eq(LearningPlan::getUserId, userId)
                .eq(LearningPlan::getPlanDate, d));
    }

    private LearningPlan buildEntity(Long userId, PlanDayDTO pd) {
        LearningPlan p = new LearningPlan();
        p.setUserId(userId);
        p.setPlanDate(pd.getDate());
        p.setStatus(1);
        p.setCompletedRatio(BigDecimal.ZERO);

        // 序列化 blocks → timeBlocks；同时收集 ids 列表
        Set<Long> ltaskIds = new LinkedHashSet<>();
        Set<Long> todoIds = new LinkedHashSet<>();
        Set<Long> habitIds = new LinkedHashSet<>();

        for (PlanBlockDTO b : pd.getBlocks()) {
            for (PlanItemDTO i : b.getItems()) {
                if (i == null) continue;
                String type = i.getType();
                if ("learningTask".equals(type)) {
                    // type=learningTask：如果只有 chapterId，需要创建/复用 LearningTask
                    Long ltId = resolveLearningTaskId(userId, i);
                    if (ltId != null) {
                        i.setLearningTaskId(ltId);
                        ltaskIds.add(ltId);
                    }
                    if (i.getChapterId() != null) {
                        // chapterId 保留供前端跳转
                    }
                } else if ("todo".equals(type) && i.getTaskId() != null) {
                    todoIds.add(i.getTaskId());
                } else if ("habit".equals(type) && i.getHabitId() != null) {
                    habitIds.add(i.getHabitId());
                }
            }
        }

        try {
            List<PlanBlockVO> blockVOs = pd.getBlocks().stream()
                    .map(this::blockDtoToVo)
                    .collect(Collectors.toList());
            p.setTimeBlocks(objectMapper.writeValueAsString(blockVOs));
        } catch (JsonProcessingException ex) {
            log.error("[LearningPlan] 序列化 blocks 失败 userId={}", userId, ex);
            p.setTimeBlocks("[]");
        }
        try {
            p.setLearningTaskIds(objectMapper.writeValueAsString(new ArrayList<>(ltaskIds)));
            p.setTodoIds(objectMapper.writeValueAsString(new ArrayList<>(todoIds)));
            p.setHabitIds(objectMapper.writeValueAsString(new ArrayList<>(habitIds)));
        } catch (JsonProcessingException ex) {
            p.setLearningTaskIds("[]");
            p.setTodoIds("[]");
            p.setHabitIds("[]");
        }
        return p;
    }

    /**
     * 如果 AI 返回 chapterId 但没有 learningTaskId：
     * 按 (userId, targetType=chapter, targetId=chapterId, status!=1) 复用；
     * 不存在则创建一条 LearningTask 并返回 id，方便 UI 勾选状态回填。
     */
    private Long resolveLearningTaskId(Long userId, PlanItemDTO i) {
        if (i.getLearningTaskId() != null) return i.getLearningTaskId();
        if (i.getChapterId() == null) return null;
        LearningTask exist = learningTaskMapper.selectOne(new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getUserId, userId)
                .eq(LearningTask::getTargetId, i.getChapterId())
                .like(LearningTask::getType, "chapter")
                .ne(LearningTask::getStatus, 1)
                .last("LIMIT 1"));
        if (exist != null) return exist.getId();
        LearningTask nt = new LearningTask();
        nt.setUserId(userId);
        nt.setTitle(i.getTitle() == null ? ("章节学习：" + i.getChapterId()) : i.getTitle());
        nt.setTargetId(i.getChapterId());
        nt.setType("chapter");
        nt.setStatus(0);
        nt.setDescription("由学习计划编排自动创建");
        nt.setExpReward(10);
        nt.setEnergyCost(20);
        learningTaskMapper.insert(nt);
        return nt.getId();
    }

    private PlanBlockVO blockDtoToVo(PlanBlockDTO b) {
        PlanBlockVO vo = new PlanBlockVO();
        SlotMeta sm = slotByName(b.getTimeSlot());
        vo.setTimeSlot(sm.timeSlot);
        vo.setLabel(sm.label);
        vo.setStartTime(b.getStartTime() != null ? b.getStartTime() : sm.start);
        vo.setEndTime(b.getEndTime() != null ? b.getEndTime() : sm.end);
        List<PlanItemVO> items = b.getItems().stream()
                .filter(Objects::nonNull)
                .map(dto -> {
                    PlanItemVO iv = new PlanItemVO();
                    iv.setType(dto.getType());
                    iv.setLearningTaskId(dto.getLearningTaskId());
                    iv.setChapterId(dto.getChapterId());
                    iv.setTaskId(dto.getTaskId());
                    iv.setHabitId(dto.getHabitId());
                    iv.setTitle(dto.getTitle());
                    iv.setDuration(dto.getDuration());
                    iv.setStartTime(dto.getStartTime());
                    iv.setEndTime(dto.getEndTime());
                    iv.setCompleted(Boolean.FALSE); // 实时回填由 fillCompletionStatus 完成
                    return iv;
                })
                .collect(Collectors.toList());
        vo.setItems(items);
        return vo;
    }

    private LearningPlanVO parsePlanToVO(LearningPlan p) {
        LearningPlanVO vo = new LearningPlanVO();
        vo.setPlanId(p.getId());
        vo.setDate(p.getPlanDate());
        vo.setCompletedRatio(p.getCompletedRatio() == null ? BigDecimal.ZERO : p.getCompletedRatio());
        vo.setStatus(p.getStatus() == null ? 0 : p.getStatus());
        List<PlanBlockVO> blocks;
        try {
            blocks = (p.getTimeBlocks() == null || p.getTimeBlocks().isBlank())
                    ? new ArrayList<>()
                    : objectMapper.readValue(p.getTimeBlocks(), new TypeReference<List<PlanBlockVO>>() {});
        } catch (JsonProcessingException e) {
            log.warn("[LearningPlan] timeBlocks JSON 解析失败 planId={}：{}", p.getId(), e.getMessage());
            blocks = new ArrayList<>();
        }
        if (blocks.size() < 3) {
            for (int s = blocks.size(); s < 3; s++) {
                SlotMeta sm = slotByIndex(s);
                PlanBlockVO pb = new PlanBlockVO();
                pb.setTimeSlot(sm.timeSlot);
                pb.setLabel(sm.label);
                pb.setStartTime(sm.start);
                pb.setEndTime(sm.end);
                blocks.add(pb);
            }
        }
        vo.setBlocks(blocks);
        int total = blocks.stream().mapToInt(b -> b.getItems() == null ? 0 : b.getItems().size()).sum();
        vo.setTotalItems(total);
        vo.setCompletedItems(0);
        return vo;
    }

    /** 实时计算 item.completed：按类型查三张表。 */
    private void fillCompletionStatus(LearningPlanVO vo, Long userId) {
        Set<Long> ltIds = new HashSet<>();
        Set<Long> taskIds = new HashSet<>();
        Set<Long> habitIds = new HashSet<>();
        for (PlanBlockVO b : vo.getBlocks()) {
            if (b.getItems() == null) continue;
            for (PlanItemVO i : b.getItems()) {
                if (i == null) continue;
                String t = i.getType();
                if ("learningTask".equals(t) && i.getLearningTaskId() != null) ltIds.add(i.getLearningTaskId());
                else if ("todo".equals(t) && i.getTaskId() != null) taskIds.add(i.getTaskId());
                else if ("habit".equals(t) && i.getHabitId() != null) habitIds.add(i.getHabitId());
            }
        }
        // 批量查状态
        Map<Long, Boolean> ltDone = new HashMap<>();
        if (!ltIds.isEmpty()) {
            learningTaskMapper.selectBatchIds(ltIds).forEach(x ->
                    ltDone.put(x.getId(), x.getStatus() != null && x.getStatus() == 1));
        }
        Map<Long, Boolean> taskDone = new HashMap<>();
        if (!taskIds.isEmpty()) {
            taskMapper.selectBatchIds(taskIds).forEach(x ->
                    taskDone.put(x.getId(), x.getStatus() != null && x.getStatus() == 1));
        }
        Map<Long, Boolean> habitDone = new HashMap<>();
        if (!habitIds.isEmpty()) {
            habitCheckInMapper.selectList(new LambdaQueryWrapper<HabitCheckIn>()
                            .eq(HabitCheckIn::getUserId, userId)
                            .eq(HabitCheckIn::getCheckDate, vo.getDate())
                            .in(HabitCheckIn::getHabitId, habitIds)
                            .gt(HabitCheckIn::getCount, 0))
                    .forEach(c -> habitDone.put(c.getHabitId(), Boolean.TRUE));
        }
        int completed = 0, total = 0;
        for (PlanBlockVO b : vo.getBlocks()) {
            if (b.getItems() == null) continue;
            for (PlanItemVO i : b.getItems()) {
                if (i == null) continue;
                total++;
                boolean done = Boolean.FALSE;
                String t = i.getType();
                if ("learningTask".equals(t)) done = ltDone.getOrDefault(i.getLearningTaskId(), Boolean.FALSE);
                else if ("todo".equals(t)) done = taskDone.getOrDefault(i.getTaskId(), Boolean.FALSE);
                else if ("habit".equals(t)) done = habitDone.getOrDefault(i.getHabitId(), Boolean.FALSE);
                i.setCompleted(done);
                if (done) completed++;
            }
        }
        vo.setTotalItems(total);
        vo.setCompletedItems(completed);
    }

    /** 重新计算完成率：completedItems/totalItems ×100，两位小数。 */
    private void recomputeCompletedRatio(LearningPlanVO vo) {
        int total = vo.getTotalItems() == null ? 0 : vo.getTotalItems();
        int done = vo.getCompletedItems() == null ? 0 : vo.getCompletedItems();
        BigDecimal ratio;
        if (total == 0) ratio = BigDecimal.ZERO;
        else ratio = BigDecimal.valueOf(done * 100.0 / total).setScale(2, RoundingMode.HALF_UP);
        vo.setCompletedRatio(ratio);
    }

    private LearningPlanVO buildEmptyVO(LocalDate date) {
        LearningPlanVO vo = new LearningPlanVO();
        vo.setDate(date);
        vo.setCompletedRatio(BigDecimal.ZERO);
        vo.setStatus(0);
        vo.setTotalItems(0);
        vo.setCompletedItems(0);
        for (int s = 0; s < 3; s++) {
            SlotMeta sm = slotByIndex(s);
            PlanBlockVO pb = new PlanBlockVO();
            pb.setTimeSlot(sm.timeSlot);
            pb.setLabel(sm.label);
            pb.setStartTime(sm.start);
            pb.setEndTime(sm.end);
            vo.getBlocks().add(pb);
        }
        return vo;
    }

    // ========================= 工具 =========================

    private LearningPlanServiceImpl.SlotMeta slotByIndex(int idx) {
        return SLOTS[Math.max(0, Math.min(SLOTS.length - 1, idx))];
    }
    private LearningPlanServiceImpl.SlotMeta slotByName(String name) {
        if (name == null) return SLOTS[0];
        return switch (name) {
            case "morning" -> SLOTS[0];
            case "afternoon" -> SLOTS[1];
            case "evening" -> SLOTS[2];
            default -> SLOTS[0];
        };
    }
    private record SlotMeta(String timeSlot, String label, String start, String end){}

    private PlanBlockDTO newBlock(String timeSlot) {
        SlotMeta s = slotByName(timeSlot);
        PlanBlockDTO b = new PlanBlockDTO();
        b.setTimeSlot(s.timeSlot);
        b.setStartTime(s.start);
        b.setEndTime(s.end);
        return b;
    }

    private static int usedMinutesOfBlock(PlanBlockDTO b) {
        if (b.getItems() == null) return 0;
        int s = 0;
        for (PlanItemDTO i : b.getItems()) {
            if (i == null || i.getDuration() == null) s += 30;
            else s += i.getDuration();
        }
        return s;
    }

    /** 为 item 在 block 内分配 startTime/endTime。 */
    private static void assignTimes(PlanItemDTO pi, PlanBlockDTO b, int durationMinutes) {
        LocalTime blockStart = parseLocalTime(b.getStartTime(), LocalTime.parse(SLOTS[0].start));
        LocalTime nextStart;
        if (b.getItems() == null || b.getItems().isEmpty()) {
            nextStart = blockStart;
        } else {
            PlanItemDTO last = b.getItems().get(b.getItems().size() - 1);
            nextStart = parseLocalTime(last.getEndTime(), blockStart);
        }
        LocalTime end = nextStart.plusMinutes(durationMinutes);
        // 晚于 23:00 就不切到次日，仅截断到 23:00（由 ICS 再校验）
        if (end.isAfter(LocalTime.of(23, 0))) end = LocalTime.of(23, 0);
        pi.setStartTime(nextStart.format(DateTimeFormatter.ofPattern("HH:mm")));
        pi.setEndTime(end.format(DateTimeFormatter.ofPattern("HH:mm")));
    }

    private static LocalTime parseLocalTime(String s, LocalTime fallback) {
        if (s == null || s.isBlank()) return fallback;
        try { return LocalTime.parse(s); }
        catch (Exception e) { return fallback; }
    }

    private static String normalizeType(String t) {
        if (t == null) return "learningTask";
        return switch (t) {
            case "learningTask","chapter","learning" -> "learningTask";
            case "todo","task" -> "todo";
            case "habit","checkin" -> "habit";
            default -> "learningTask";
        };
    }

    private static String extractJson(String raw) {
        if (raw == null) return "{}";
        String body = raw.trim();
        // 去 ```json ... ``` / ``` ... ```
        int s1 = body.indexOf("```json");
        if (s1 >= 0) {
            int end = body.indexOf("```", s1 + 7);
            if (end > s1) return body.substring(s1 + 7, end).trim();
        }
        int s2 = body.indexOf("```");
        if (s2 >= 0) {
            int end = body.indexOf("```", s2 + 3);
            if (end > s2) return body.substring(s2 + 3, end).trim();
        }
        // 找最外层 { 与最外层 }
        int l = body.indexOf('{');
        int r = body.lastIndexOf('}');
        if (l >= 0 && r > l) return body.substring(l, r + 1);
        return body;
    }

    private static String textOf(JsonNode n, String field, String fallback) {
        if (n == null || !n.has(field) || n.get(field).isNull()) return fallback;
        return n.get(field).asText(fallback);
    }
    private static int intOf(JsonNode n, String field, int fallback) {
        if (n == null || !n.has(field) || n.get(field).isNull()) return fallback;
        return n.get(field).asInt(fallback);
    }

    /** RFC 5545 ICS 文本转义：换行→\\n，逗号/分号转义。 */
    private static String icsEscape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\n' -> sb.append("\\n");
                case '\r' -> {}
                case ';', ',', '\\' -> sb.append('\\').append(c);
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
