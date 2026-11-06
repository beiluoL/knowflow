package com.knowflow.interview.service.career.matching;

import com.knowflow.exception.BusinessException;
import com.knowflow.interview.service.career.CareerWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多简历 / 多岗位匹配。
 *
 * <p>不实现第二套匹配算法：每一条「简历 × 岗位」都调用现有
 * {@link CareerWorkflowService#match}，因此分数、证据、快照口径与单条匹配完全一致。
 * 本类只负责编排、排序与推荐理由。</p>
 */
@Service
@RequiredArgsConstructor
public class MultiResumeMatchService {

    /** 单次批量上限：避免一次请求生成过多快照。 */
    private static final int MAX_BATCH = 10;
    /** 推荐阈值：前两名分差小于该值时视为并列，不硬推一份。 */
    private static final double TIE_THRESHOLD = 3.0;

    private final JdbcTemplate jdbc;
    private final CareerWorkflowService workflow;

    /** 岗位 → 全部已确认简历。 */
    public Map<String, Object> matchJobWithResumes(Long userId, long jobId, Integer jobVersion) {
        requireUser(userId);
        int version = resolveJobVersion(userId, jobId, jobVersion);
        List<Map<String, Object>> resumes = confirmedResumes(userId);
        if (resumes.isEmpty()) throw new BusinessException(409, "没有已确认的简历，请先确认至少一份简历");

        List<Map<String, Object>> rows = new ArrayList<>();
        for (var resume : resumes) {
            long resumeId = ((Number) resume.get("id")).longValue();
            int resumeVersion = ((Number) resume.get("version")).intValue();
            Map<String, Object> row = matchRow(userId, resumeId, resumeVersion, jobId, version, resume);
            if (row != null) rows.add(row);
        }
        if (rows.isEmpty()) throw new BusinessException(409, "所有简历都未能完成匹配，请检查版本状态");

        sortByScore(rows);
        markRecommendation(rows);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId", jobId);
        result.put("jobVersion", version);
        result.put("candidates", rows);
        result.put("recommendedResumeId", rows.stream()
                .filter(row -> Boolean.TRUE.equals(row.get("recommended")))
                .map(row -> row.get("resumeId")).findFirst().orElse(null));
        result.put("scoreMeaning", "已确认必备技能的简历证据覆盖率，不代表熟练度或录用概率");
        result.put("policyVersion", "confirmed-skill-coverage-v1");
        return result;
    }

    /** 简历 → 多个岗位（批量）。 */
    public Map<String, Object> matchResumeWithJobs(Long userId, long resumeId, Integer resumeVersion,
                                                    List<Long> jobIds) {
        requireUser(userId);
        if (jobIds == null || jobIds.isEmpty()) throw new BusinessException(400, "请选择至少一个岗位");
        if (jobIds.size() > MAX_BATCH) throw new BusinessException(400, "单次最多匹配 " + MAX_BATCH + " 个岗位");
        var resume = resolveResume(userId, resumeId, resumeVersion);

        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        for (Long jobId : jobIds.stream().distinct().toList()) {
            try {
                int version = resolveJobVersion(userId, jobId, null);
                Map<String, Object> row = matchRow(userId, resumeId,
                        ((Number) resume.get("version")).intValue(), jobId, version, resume);
                if (row != null) rows.add(row);
            } catch (BusinessException failure) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("jobId", jobId);
                item.put("message", failure.getMessage());
                failures.add(item);
            }
        }
        sortByScore(rows);
        markRecommendation(rows);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resumeId", resumeId);
        result.put("resumeVersion", resume.get("version"));
        result.put("jobs", rows);
        result.put("failures", failures);
        result.put("partial", !failures.isEmpty());
        result.put("scoreMeaning", "已确认必备技能的简历证据覆盖率，不代表熟练度或录用概率");
        return result;
    }

    /** 匹配缺口：复用快照里已冻结的 dimensions，不重新计算。 */
    public Map<String, Object> gaps(Long userId, long matchId) {
        requireUser(userId);
        Map<String, Object> snapshot = workflow.get(userId, matchId, "MATCH");
        Object requiredRaw = snapshot.get("required");
        List<Map<String, Object>> missing = new ArrayList<>();
        List<Map<String, Object>> covered = new ArrayList<>();
        if (requiredRaw instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> raw)) continue;
                String requirement = String.valueOf(raw.get("requirement"));
                String status = String.valueOf(raw.get("matchStatus"));
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("skill", requirement);
                row.put("status", status);
                row.put("evidence", raw.get("resumeEvidence"));
                row.put("note", raw.get("gapDescription"));
                if ("MATCHED".equals(status)) covered.add(row);
                else missing.add(row);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matchId", matchId);
        result.put("missing", missing);
        result.put("covered", covered);
        result.put("note", "「简历未体现」只代表已确认简历中没有该技能证据，不代表本人不会。");
        return result;
    }

    /* ------------------------------ 内部 ------------------------------ */

    private Map<String, Object> matchRow(Long userId, long resumeId, int resumeVersion,
                                         long jobId, int jobVersion, Map<String, Object> resume) {
        String requestId = "multi-" + resumeId + "-" + jobId + "-" + resumeVersion + "-" + jobVersion;
        Map<String, Object> snapshot;
        try {
            snapshot = workflow.match(userId,
                    new CareerWorkflowService.MatchRequest(resumeId, resumeVersion, jobId, jobVersion, requestId));
        } catch (BusinessException failure) {
            // 单份简历失败不拖垮整体（例如岗位版本未确认）
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("resumeId", resumeId);
        row.put("resumeTitle", resume.get("title"));
        row.put("resumeVersion", resumeVersion);
        row.put("matchId", snapshot.get("id"));
        row.put("score", snapshot.get("score"));
        row.put("overallScore", snapshot.get("overallScore"));
        row.put("policyVersion", snapshot.get("policyVersion"));
        int[] counts = counts(snapshot.get("required"));
        row.put("requiredCount", counts[0]);
        row.put("matchedCount", counts[1]);
        row.put("coverage", counts[0] == 0 ? null
                : Math.round(counts[1] * 10000d / counts[0]) / 100d);
        Object jobSnapshot = snapshot.get("job");
        if (jobSnapshot instanceof Map<?, ?> jobMap) {
            row.put("jobId", jobMap.get("id"));
            row.put("jobTitle", jobMap.get("title"));
        }
        row.put("recommended", false);
        row.put("reason", reason(row));
        return row;
    }

    private int[] counts(Object requiredRaw) {
        int total = 0;
        int matched = 0;
        if (requiredRaw instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> raw)) continue;
                total++;
                if ("MATCHED".equals(String.valueOf(raw.get("matchStatus")))) matched++;
            }
        }
        return new int[]{total, matched};
    }

    /** 推荐理由只描述计算结果，不做主观评价。 */
    private String reason(Map<String, Object> row) {
        Object coverage = row.get("coverage");
        if (coverage == null) return "该岗位未提取到必备技能，无法计算覆盖率";
        double value = ((Number) coverage).doubleValue();
        if (value >= 80) return "必备技能覆盖 " + Math.round(value) + "%，简历证据较完整";
        if (value >= 50) return "必备技能覆盖 " + Math.round(value) + "%，仍有缺口需人工判断";
        return "必备技能覆盖 " + Math.round(value) + "%，缺口较多";
    }

    private void sortByScore(List<Map<String, Object>> rows) {
        rows.sort(Comparator.comparing(
                (Map<String, Object> row) -> row.get("coverage") == null ? -1d
                        : ((Number) row.get("coverage")).doubleValue()).reversed());
    }

    private void markRecommendation(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return;
        Object top = rows.get(0).get("coverage");
        if (top == null) return;
        double topValue = ((Number) top).doubleValue();
        // 覆盖率为 0 时不给推荐：推荐一份完全不匹配的简历会误导用户
        if (topValue <= 0) {
            rows.forEach(row -> row.put("recommended", false));
            return;
        }
        if (rows.size() > 1) {
            Object second = rows.get(1).get("coverage");
            if (second != null && topValue - ((Number) second).doubleValue() < TIE_THRESHOLD) {
                rows.forEach(row -> {
                    row.put("recommended", false);
                    row.put("tie", true);
                    row.put("reason", row.get("reason") + "；与其它简历覆盖接近，建议人工比较");
                });
                return;
            }
        }
        rows.get(0).put("recommended", true);
    }

    private List<Map<String, Object>> confirmedResumes(Long userId) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (var row : jdbc.queryForList("""
                SELECT r.id, r.title, r.current_version
                  FROM interview_career_record r
                  JOIN interview_career_version v ON v.record_id=r.id AND v.version_no=r.current_version
                 WHERE r.user_id=? AND r.kind='RESUME' AND r.deleted=0 AND v.confirmed=1
                 ORDER BY r.is_default DESC, r.update_time DESC, r.id DESC
                """, userId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("title", row.get("title"));
            item.put("version", row.get("current_version"));
            list.add(item);
        }
        return list;
    }

    private Map<String, Object> resolveResume(Long userId, long resumeId, Integer version) {
        var rows = jdbc.queryForList("""
                SELECT r.id, r.title, r.current_version
                  FROM interview_career_record r
                  JOIN interview_career_version v ON v.record_id=r.id AND v.version_no=COALESCE(?, r.current_version)
                 WHERE r.id=? AND r.user_id=? AND r.kind='RESUME' AND r.deleted=0 AND v.confirmed=1
                """, version, resumeId, userId);
        if (rows.isEmpty()) throw new BusinessException(409, "简历版本未确认或不存在");
        var row = rows.get(0);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.get("id"));
        item.put("title", row.get("title"));
        item.put("version", version == null ? row.get("current_version") : version);
        return item;
    }

    private int resolveJobVersion(Long userId, long jobId, Integer version) {
        var rows = jdbc.queryForList("""
                SELECT v.version_no
                  FROM interview_career_record r
                  JOIN interview_career_version v ON v.record_id=r.id AND v.version_no=COALESCE(?, r.current_version)
                 WHERE r.id=? AND r.user_id=? AND r.kind='JOB' AND r.deleted=0 AND v.confirmed=1
                """, version, jobId, userId);
        if (rows.isEmpty()) throw new BusinessException(409, "岗位不存在或版本未确认");
        return ((Number) rows.get(0).get("version_no")).intValue();
    }

    private void requireUser(Long userId) {
        if (userId == null) throw new BusinessException(401, "请先登录");
    }
}
