package com.knowflow.interview.service.career;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.knowflow.interview.career.CareerMatcher;
/**
 * 求职工作台聚合读取：总览、岗位列表、匹配历史、面试方案与会话历史。
 *
 * <p>所有查询以服务端认证 userId 为边界；不返回其他用户资料，不把缺失字段补造成
 * 匹配分数或面试成绩。</p>
 */
@Service
@RequiredArgsConstructor
public class CareerWorkspaceService {

    private static final Map<String,String> STATUS_LABELS = Map.of(
            "SAVED", "关注中",
            "TO_APPLY", "待投递",
            "APPLIED", "已投递",
            "WRITTEN_TEST", "笔试",
            "INTERVIEWING", "面试中",
            "OFFER", "已录用",
            "REJECTED", "已拒绝",
            "CLOSED", "已结束");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final CareerRecordService records;
    private final CareerJobPipelineService pipelines;

    public Map<String,Object> dashboard(Long userId) {
        requireUser(userId);
        int resumes = count("SELECT COUNT(*) FROM interview_career_record WHERE user_id=? AND kind='RESUME' AND deleted=0", userId);
        int jobs = count("SELECT COUNT(*) FROM interview_career_record WHERE user_id=? AND kind='JOB' AND deleted=0", userId);
        int confirmedResumes = count("""
                SELECT COUNT(*) FROM interview_career_record r
                 WHERE r.user_id=? AND r.kind='RESUME' AND r.deleted=0
                   AND EXISTS (SELECT 1 FROM interview_career_version v WHERE v.record_id=r.id AND v.confirmed=1)
                """, userId);
        int matches = count("SELECT COUNT(*) FROM interview_career_snapshot WHERE user_id=? AND kind='MATCH'", userId);
        int plans = count("SELECT COUNT(*) FROM interview_career_snapshot WHERE user_id=? AND kind='PLAN'", userId);
        int interviews = count("SELECT COUNT(*) FROM interview_career_session WHERE user_id=?", userId);
        int favorites = count("""
                SELECT COUNT(*) FROM interview_career_record r
                  LEFT JOIN interview_career_job_pipeline p ON p.record_id=r.id AND p.user_id=r.user_id
                 WHERE r.user_id=? AND r.kind='JOB' AND r.deleted=0 AND COALESCE(p.status,'SAVED')='SAVED'
                """, userId);

        Map<String,Object> counts = new LinkedHashMap<>();
        counts.put("resumes", resumes);
        counts.put("jobs", jobs);
        counts.put("favoriteJobs", favorites);
        counts.put("matches", matches);
        counts.put("interviews", interviews);

        Map<String,Object> response = new LinkedHashMap<>();
        response.put("counts", counts);
        response.put("pipeline", pipeline(userId));
        response.put("tasks", tasks(resumes, confirmedResumes, jobs, matches, plans, interviews));
        response.put("recentActivity", recentActivity(userId));
        return response;
    }

    private List<Map<String,Object>> pipeline(Long userId) {
        Map<String,Integer> observed = new LinkedHashMap<>();
        for (var row : jdbc.queryForList("""
                SELECT COALESCE(p.status,'SAVED') AS status, COUNT(*) AS total
                  FROM interview_career_record r
                  LEFT JOIN interview_career_job_pipeline p ON p.record_id=r.id AND p.user_id=r.user_id
                 WHERE r.user_id=? AND r.kind='JOB' AND r.deleted=0
                 GROUP BY COALESCE(p.status,'SAVED')
                """, userId)) {
            observed.put(String.valueOf(row.get("status")), ((Number) row.get("total")).intValue());
        }
        List<Map<String,Object>> result = new ArrayList<>();
        for (String status : List.of("SAVED", "TO_APPLY", "APPLIED", "WRITTEN_TEST", "INTERVIEWING", "OFFER", "REJECTED", "CLOSED")) {
            result.add(Map.of("status", status, "label", STATUS_LABELS.get(status),
                    "count", observed.getOrDefault(status, 0)));
        }
        return result;
    }

    private List<Map<String,Object>> tasks(int resumes, int confirmedResumes, int jobs, int matches, int plans, int interviews) {
        List<Map<String,Object>> tasks = new ArrayList<>();
        if (resumes == 0) {
            tasks.add(task("resume-create", "创建第一份简历", "上传 PDF/DOCX 或粘贴简历原文，解析后再人工确认。", "/career/resumes/new"));
            return tasks;
        }
        if (confirmedResumes == 0) {
            tasks.add(task("resume-confirm", "确认简历解析结果", "检查 AI 提取的条目与原文证据，确认后才会参与匹配。", "/career/resumes"));
        }
        if (jobs == 0) {
            tasks.add(task("job-import", "导入一个目标岗位", "粘贴 JD、上传文件或从可访问链接导入。", "/career/jobs?import=1"));
        }
        if (jobs > 0 && matches == 0) {
            tasks.add(task("match-run", "运行一次智能匹配", "选择已确认的简历版本与岗位版本，生成可回溯的证据快照。", "/career/matches?create=1"));
        }
        if (matches > 0 && plans == 0) {
            tasks.add(task("plan-create", "创建岗位定制面试", "根据匹配差距选择题目方向，逐题审核后开始。", "/career/interviews/new"));
        }
        if (plans > 0 && interviews == 0) {
            tasks.add(task("interview-start", "完成一次岗位定制面试", "进入已审核方案，复用 Interview V2 完成作答、追问与报告。", "/career/interviews"));
        }
        if (tasks.isEmpty()) {
            tasks.add(task("pipeline-review", "更新岗位投递进度", "把已投递、笔试和面试中的岗位状态同步到岗位中心。", "/career/jobs"));
        }
        if (tasks.size() > 4) return tasks.subList(0, 4);
        return tasks;
    }

    private Map<String,Object> task(String id, String title, String description, String route) {
        Map<String,Object> task = new LinkedHashMap<>();
        task.put("id", id);
        task.put("title", title);
        task.put("description", description);
        task.put("route", route);
        return task;
    }

    private List<Map<String,Object>> recentActivity(Long userId) {
        List<Activity> activities = new ArrayList<>();
        for (var row : jdbc.queryForList("""
                SELECT r.kind,r.title,v.version_no,v.create_time
                  FROM interview_career_version v
                  JOIN interview_career_record r ON r.id=v.record_id
                 WHERE r.user_id=? AND r.deleted=0
                 ORDER BY v.create_time DESC, v.id DESC LIMIT 8
                """, userId)) {
            String kind = String.valueOf(row.get("kind"));
            activities.add(new Activity(
                    "RESUME".equals(kind) ? "RESUME_UPDATED" : "JOB_UPDATED",
                    "RESUME".equals(kind) ? "简历版本更新" : "岗位版本更新",
                    String.valueOf(row.get("title")) + " · v" + row.get("version_no"),
                    (Timestamp) row.get("create_time")));
        }
        for (var row : snapshotRows(userId, "MATCH", 6)) {
            var payload = payload(row);
            String job = nestedTitle(payload, "job");
            activities.add(new Activity("MATCH_CREATED", "智能匹配完成",
                    job.isBlank() ? "已生成匹配快照" : "目标岗位：" + job,
                    (Timestamp) row.get("create_time")));
        }
        for (var row : snapshotRows(userId, "PLAN", 6)) {
            var payload = payload(row);
            String job = nestedTitle(payload, "job");
            activities.add(new Activity("PLAN_CREATED", "面试方案生成",
                    job.isBlank() ? "已生成岗位定制面试方案" : "目标岗位：" + job,
                    (Timestamp) row.get("create_time")));
        }
        for (var row : interviewRows(userId, 6)) {
            activities.add(new Activity("INTERVIEW_STARTED", "岗位定制面试",
                    planTitle(userId, longValue(row.get("plan_id"))) + " · " + stateLabel(row.get("state")),
                    (Timestamp) row.get("create_time")));
        }
        return activities.stream()
                .sorted(Comparator.comparing(Activity::at, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(8)
                .map(activity -> {
                    Map<String,Object> value = new LinkedHashMap<>();
                    value.put("type", activity.type());
                    value.put("title", activity.title());
                    value.put("description", activity.description());
                    value.put("at", activity.at());
                    return value;
                })
                .toList();
    }

    public List<Map<String,Object>> listJobs(Long userId, String status, String keyword, String sort) {
        requireUser(userId);
        if (status != null && !status.isBlank() && !CareerJobPipelineService.STATUSES.contains(status)) {
            throw new BusinessException(400, "岗位状态筛选无效");
        }
        StringBuilder sql = new StringBuilder("""
                SELECT r.id
                  FROM interview_career_record r
                  LEFT JOIN interview_career_job_pipeline p ON p.record_id=r.id AND p.user_id=r.user_id
                 WHERE r.user_id=? AND r.kind='JOB' AND r.deleted=0
                """);
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (status != null && !status.isBlank()) {
            sql.append(" AND COALESCE(p.status,'SAVED')=?");
            args.add(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            if (value.length() > 100) throw new BusinessException(400, "搜索关键词最多100字");
            sql.append(" AND LOWER(r.title) LIKE ?");
            args.add("%" + value.toLowerCase(java.util.Locale.ROOT) + "%");
        }
        sql.append(switch (sort == null ? "updated" : sort) {
            case "title" -> " ORDER BY r.title ASC";
            case "status" -> " ORDER BY COALESCE(p.status,'SAVED') ASC, r.title ASC";
            default -> " ORDER BY r.update_time DESC, r.id DESC";
        });
        sql.append(" LIMIT 100");
        List<Map<String,Object>> result = new ArrayList<>();
        for (var row : jdbc.queryForList(sql.toString(), args.toArray())) {
            long id = ((Number) row.get("id")).longValue();
            Map<String,Object> record = records.detail(userId, id, null);
            record.put("jobStatus", pipelines.view(userId, id));
            result.add(record);
        }
        return result;
    }

    public List<Map<String,Object>> matchHistory(Long userId) {
        requireUser(userId);
        List<Map<String,Object>> result = new ArrayList<>();
        for (var row : snapshotRows(userId, "MATCH", 30)) {
            var payload = payload(row);
            var resume = nestedMap(payload, "resume");
            var job = nestedMap(payload, "job");
            Map<String,Object> item = new LinkedHashMap<>();
            item.put("id", longValue(row.get("id")));
            item.put("resumeTitle", resume.get("title"));
            item.put("resumeVersion", resume.get("version"));
            item.put("resumeId", resume.get("id"));
            item.put("jobTitle", job.get("title"));
            item.put("jobVersion", job.get("version"));
            item.put("jobId", job.get("id"));
            item.put("score", payload.get("score"));
            item.put("overallScore", payload.get("overallScore"));
            item.put("policyVersion", payload.get("policyVersion"));
            item.put("scoreMeaning", payload.get("scoreMeaning"));
            item.put("evaluationCoverage", payload.get("evaluationCoverage"));
            item.put("createdAt", row.get("create_time"));
            result.add(item);
        }
        return result;
    }

    public List<Map<String,Object>> planHistory(Long userId) {
        requireUser(userId);
        List<Map<String,Object>> result = new ArrayList<>();
        for (var row : snapshotRows(userId, "PLAN", 30)) {
            var payload = payload(row);
            var job = nestedMap(payload, "job");
            var resume = nestedMap(payload, "resume");
            var questions = payload.get("questions");
            int questionCount = questions instanceof List<?> list ? list.size() : 0;
            Map<String,Object> item = new LinkedHashMap<>();
            item.put("id", longValue(row.get("id")));
            item.put("matchId", payload.get("matchId"));
            item.put("jobId", job.get("id"));
            item.put("jobTitle", job.get("title"));
            item.put("jobVersion", job.get("version"));
            item.put("resumeId", resume.get("id"));
            item.put("resumeVersion", resume.get("version"));
            item.put("questionCount", questionCount);
            item.put("durationMinutes", payload.get("durationMinutes"));
            item.put("difficulty", payload.get("difficulty"));
            item.put("generationSource", payload.get("generationSource"));
            item.put("previousPlanId", payload.get("previousPlanId"));
            item.put("createdAt", row.get("create_time"));
            result.add(item);
        }
        return result;
    }

    public List<Map<String,Object>> interviewHistory(Long userId) {
        requireUser(userId);
        List<Map<String,Object>> result = new ArrayList<>();
        for (var row : interviewRows(userId, 30)) {
            Map<String,Object> item = new LinkedHashMap<>();
            item.put("planId", longValue(row.get("plan_id")));
            item.put("sessionId", longValue(row.get("session_id")));
            item.put("jobTitle", planTitle(userId, longValue(row.get("plan_id"))));
            item.put("state", row.get("state"));
            item.put("stateLabel", stateLabel(row.get("state")));
            item.put("difficulty", row.get("difficulty"));
            item.put("role", row.get("role"));
            item.put("createdAt", row.get("create_time"));
            result.add(item);
        }
        return result;
    }

    private List<Map<String,Object>> interviewRows(Long userId, int limit) {
        return jdbc.queryForList("""
                SELECT c.session_id,c.plan_id,c.create_time,
                       s.state,s.difficulty,s.role
                  FROM interview_career_session c
                  LEFT JOIN mock_interview_v2_state s ON s.session_id=c.session_id AND s.user_id=c.user_id
                 WHERE c.user_id=?
                 ORDER BY c.id DESC LIMIT ?
                """, userId, limit);
    }

    private List<Map<String,Object>> snapshotRows(Long userId, String kind, int limit) {
        return jdbc.queryForList("""
                SELECT id,payload_json,create_time
                  FROM interview_career_snapshot
                 WHERE user_id=? AND kind=?
                 ORDER BY id DESC LIMIT ?
                """, userId, kind, limit);
    }

    /**
     * 求职总览（数据分析中心）。
     *
     * <p>只做聚合读取：所有数字来自本人真实记录；样本不足时返回 null 与样本量，
     * 不用 0 或行业基准填充。技能覆盖复用 {@link CareerMatcher} 的确定性比对，
     * 不引入第二套匹配算法，也不让模型参与打分。</p>
     */
    public Map<String,Object> overview(Long userId) {
        requireUser(userId);
        int resumes = count("SELECT COUNT(*) FROM interview_career_record WHERE user_id=? AND kind='RESUME' AND deleted=0", userId);
        int jobs = count("SELECT COUNT(*) FROM interview_career_record WHERE user_id=? AND kind='JOB' AND deleted=0", userId);
        int matches = count("SELECT COUNT(*) FROM interview_career_snapshot WHERE user_id=? AND kind='MATCH'", userId);
        int plans = count("SELECT COUNT(*) FROM interview_career_snapshot WHERE user_id=? AND kind='PLAN'", userId);
        int applied = count("""
                SELECT COUNT(*) FROM interview_career_record r
                  JOIN interview_career_job_pipeline p ON p.record_id=r.id AND p.user_id=r.user_id
                 WHERE r.user_id=? AND r.kind='JOB' AND r.deleted=0
                   AND p.status IN ('APPLIED','WRITTEN_TEST','INTERVIEWING','OFFER','REJECTED','CLOSED')
                """, userId);
        int offers = count("""
                SELECT COUNT(*) FROM interview_career_record r
                  JOIN interview_career_job_pipeline p ON p.record_id=r.id AND p.user_id=r.user_id
                 WHERE r.user_id=? AND r.kind='JOB' AND r.deleted=0 AND p.status='OFFER'
                """, userId);

        Map<String,Object> counts = new LinkedHashMap<>();
        counts.put("resumes", resumes);
        counts.put("jobs", jobs);
        counts.put("matches", matches);
        counts.put("applied", applied);
        counts.put("offers", offers);
        counts.put("plans", plans);

        Map<String,Object> response = new LinkedHashMap<>();
        response.put("counts", counts);
        response.put("pipeline", pipeline(userId));
        response.put("trends", weeklyTrends(userId));
        response.put("skills", skillCoverage(userId));
        response.put("interview", interviewSummary(userId));
        response.put("activity", recentActivity(userId));
        return response;
    }

    /** 近 8 周真实新增数量（按自然周，周一起算）；没有数据的周补 0 而不是跳过。 */
    private List<Map<String,Object>> weeklyTrends(Long userId) {
        LocalDate monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        List<Map<String,Object>> weeks = new ArrayList<>();
        for (int i = 7; i >= 0; i--) {
            LocalDate start = monday.minusWeeks(i);
            LocalDate end = start.plusWeeks(1);
            Timestamp from = Timestamp.valueOf(start.atStartOfDay());
            Timestamp to = Timestamp.valueOf(end.atStartOfDay());
            int resumeVersions = count("""
                    SELECT COUNT(*) FROM interview_career_version v
                      JOIN interview_career_record r ON r.id=v.record_id
                     WHERE r.user_id=? AND r.deleted=0 AND r.kind='RESUME'
                       AND v.create_time>=? AND v.create_time<?
                    """, userId, from, to);
            int jobVersions = count("""
                    SELECT COUNT(*) FROM interview_career_version v
                      JOIN interview_career_record r ON r.id=v.record_id
                     WHERE r.user_id=? AND r.deleted=0 AND r.kind='JOB'
                       AND v.create_time>=? AND v.create_time<?
                    """, userId, from, to);
            int matchRuns = count("""
                    SELECT COUNT(*) FROM interview_career_snapshot
                     WHERE user_id=? AND kind='MATCH' AND create_time>=? AND create_time<?
                    """, userId, from, to);
            Map<String,Object> week = new LinkedHashMap<>();
            week.put("label", start.getMonthValue() + "/" + start.getDayOfMonth());
            week.put("start", start.toString());
            week.put("resumeVersions", resumeVersions);
            week.put("jobVersions", jobVersions);
            week.put("matches", matchRuns);
            weeks.add(week);
        }
        return weeks;
    }

    /**
     * 技能覆盖：默认（或最近更新的）已确认简历 × 全部已确认岗位的必备技能。
     *
     * <p>未确认简历/无已确认岗位时 overall 为 null，由前端显示「样本不足」，
     * 不用 0% 冒充「完全不匹配」。</p>
     */
    private Map<String,Object> skillCoverage(Long userId) {
        Map<String,Object> result = new LinkedHashMap<>();
        var resumeRows = jdbc.queryForList("""
                SELECT r.title, v.sections_json
                  FROM interview_career_record r
                  JOIN interview_career_version v ON v.record_id=r.id AND v.version_no=r.current_version
                 WHERE r.user_id=? AND r.kind='RESUME' AND r.deleted=0 AND v.confirmed=1
                 ORDER BY r.is_default DESC, r.update_time DESC, r.id DESC LIMIT 1
                """, userId);
        if (resumeRows.isEmpty()) {
            result.put("resume", null);
            result.put("overall", null);
            result.put("jobSample", 0);
            result.put("top", List.of());
            return result;
        }
        List<CareerMatcher.Claim> resume = claimsOf(resumeRows.get(0).get("sections_json"), "skills");
        Map<String,Integer> required = new LinkedHashMap<>();
        Map<String,Integer> covered = new LinkedHashMap<>();
        int jobSample = 0;
        for (var row : jdbc.queryForList("""
                SELECT v.sections_json
                  FROM interview_career_record r
                  JOIN interview_career_version v ON v.record_id=r.id AND v.version_no=r.current_version
                 WHERE r.user_id=? AND r.kind='JOB' AND r.deleted=0 AND v.confirmed=1
                """, userId)) {
            List<CareerMatcher.Claim> requiredClaims = claimsOf(row.get("sections_json"), "requiredSkills");
            if (requiredClaims.isEmpty()) continue;
            jobSample++;
            for (var dimension : CareerMatcher.match(resume, requiredClaims)) {
                String skill = dimension.requirement();
                required.merge(skill, 1, Integer::sum);
                if ("MATCHED".equals(dimension.matchStatus())) covered.merge(skill, 1, Integer::sum);
            }
        }
        List<Map<String,Object>> top = required.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(12)
                .map(entry -> {
                    int total = entry.getValue();
                    int hit = covered.getOrDefault(entry.getKey(), 0);
                    Map<String,Object> item = new LinkedHashMap<>();
                    item.put("skill", entry.getKey());
                    item.put("required", total);
                    item.put("covered", hit);
                    item.put("coverage", total == 0 ? 0 : Math.round(hit * 100f / total));
                    return item;
                })
                .toList();
        int totalRequired = required.values().stream().mapToInt(Integer::intValue).sum();
        int totalCovered = covered.values().stream().mapToInt(Integer::intValue).sum();
        result.put("resume", String.valueOf(resumeRows.get(0).get("title")));
        result.put("jobSample", jobSample);
        result.put("overall", totalRequired == 0 ? null : Math.round(totalCovered * 100f / totalRequired));
        result.put("top", top);
        return result;
    }

    /** 面试：会话数 / 完成数 / 本人自评均分（1-5）。没有自评时返回 null，不当作 0 分。 */
    private Map<String,Object> interviewSummary(Long userId) {
        Map<String,Object> summary = new LinkedHashMap<>();
        int sessions = count("SELECT COUNT(*) FROM interview_career_session WHERE user_id=?", userId);
        int completed = count("""
                SELECT COUNT(*) FROM interview_career_session c
                  JOIN mock_interview_v2_state s ON s.session_id=c.session_id
                 WHERE c.user_id=? AND s.state='COMPLETED'
                """, userId);
        var rows = jdbc.queryForList("""
                SELECT AVG(m.avg_self_score) AS avg_score, COUNT(m.avg_self_score) AS scored
                  FROM interview_career_session c
                  JOIN mock_interview_session m ON m.id=c.session_id
                 WHERE c.user_id=?
                """, userId);
        Object avg = rows.isEmpty() ? null : rows.get(0).get("avg_score");
        Object scored = rows.isEmpty() ? null : rows.get(0).get("scored");
        summary.put("sessions", sessions);
        summary.put("completed", completed);
        summary.put("selfScoreAvg", avg == null ? null : Math.round(((Number) avg).doubleValue() * 10d) / 10d);
        summary.put("scored", scored == null ? 0 : ((Number) scored).intValue());
        return summary;
    }

    /** 只读取 sections_json 中指定分组的 value，缺失或格式异常时返回空列表（不抛错、不补造）。 */
    private List<CareerMatcher.Claim> claimsOf(Object sectionsJson, String key) {
        if (sectionsJson == null) return List.of();
        try {
            Map<String,List<Map<String,Object>>> sections = json.readValue(
                    String.valueOf(sectionsJson), new TypeReference<>() {});
            List<Map<String,Object>> items = sections.get(key);
            if (items == null) return List.of();
            List<CareerMatcher.Claim> claims = new ArrayList<>();
            for (var item : items) {
                Object value = item.get("value");
                Object quote = item.get("quote");
                if (value == null || quote == null) continue;
                claims.add(new CareerMatcher.Claim(String.valueOf(value), String.valueOf(quote)));
            }
            return claims;
        } catch (Exception invalid) {
            return List.of();
        }
    }

    private Map<String,Object> payload(Map<String,Object> row) {
        Object raw = row.get("payload_json");
        if (raw == null) return Map.of();
        try {
            return json.readValue(String.valueOf(raw), new TypeReference<LinkedHashMap<String,Object>>() {});
        } catch (Exception invalid) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> nestedMap(Map<String,Object> value, String key) {
        Object nested = value.get(key);
        return nested instanceof Map<?,?> map ? (Map<String,Object>) map : Map.of();
    }

    private String nestedTitle(Map<String,Object> value, String key) {
        var nested = nestedMap(value, key);
        Object title = nested.get("title");
        return title == null ? "" : String.valueOf(title);
    }

    private String planTitle(Long userId, long planId) {
        if (planId <= 0) return "岗位定制面试";
        var rows = jdbc.queryForList("""
                SELECT payload_json FROM interview_career_snapshot
                 WHERE id=? AND user_id=? AND kind='PLAN'
                """, planId, userId);
        if (rows.isEmpty()) return "岗位定制面试";
        String title = nestedTitle(payload(rows.get(0)), "job");
        return title.isBlank() ? "岗位定制面试" : title;
    }

    private String stateLabel(Object state) {
        if (state == null) return "会话状态未知";
        return switch (String.valueOf(state)) {
            case "ASKING" -> "作答中";
            case "EVALUATING" -> "评价中";
            case "PAUSED" -> "已暂停";
            case "COMPLETED" -> "已完成";
            default -> String.valueOf(state);
        };
    }

    private long longValue(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private void requireUser(Long userId) {
        if (userId == null) throw new BusinessException(401, "请先登录");
    }

    private record Activity(String type, String title, String description, Timestamp at) { }
}
