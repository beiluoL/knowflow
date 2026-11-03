package com.knowflow.interview.career.insights;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 求职数据分析（只用本人已导入的数据）。
 *
 * <p>三条硬约束：
 * <ul>
 *   <li>每个统计都返回 sampleSize 与 scope，样本 &lt; 5 时由前端提示「仅供个人参考」；</li>
 *   <li>薪资只解析明确写出的区间，面议/缺失不计入，不填 0；</li>
 *   <li>不做行业基准、不调模型生成「市场趋势」。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CareerInsightsService {

    private static final Pattern RANGE = Pattern.compile(
            "(\\d{1,3}(?:\\.\\d)?)\\s*[kK千]?\\s*[-~—至]\\s*(\\d{1,3}(?:\\.\\d)?)\\s*[kK千]");
    private static final Pattern MONTHS = Pattern.compile("(\\d{1,2})\\s*薪");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public Map<String, Object> insights(Long userId) {
        if (userId == null) throw new BusinessException(401, "请先登录");

        List<Map<String, Object>> jobs = jobRows(userId);
        Map<String, Integer> required = new LinkedHashMap<>();
        Map<String, Integer> preferred = new LinkedHashMap<>();
        Map<String, Integer> cities = new LinkedHashMap<>();
        Map<String, Integer> companies = new LinkedHashMap<>();
        Map<String, Integer> experience = new LinkedHashMap<>();
        Map<String, Integer> education = new LinkedHashMap<>();
        List<Integer> salaryMids = new ArrayList<>();
        int salaryParsed = 0;
        int salaryUnknown = 0;

        for (var row : jobs) {
            Map<String, List<Map<String, Object>>> sections = sections(row.get("sections_json"));
            for (var claim : sections.getOrDefault("requiredSkills", List.of())) {
                required.merge(String.valueOf(claim.get("value")), 1, Integer::sum);
            }
            for (var claim : sections.getOrDefault("preferredSkills", List.of())) {
                preferred.merge(String.valueOf(claim.get("value")), 1, Integer::sum);
            }
            String title = String.valueOf(row.get("title"));
            String source = String.valueOf(row.get("source_text"));
            String city = firstCity(source);
            if (city != null) cities.merge(city, 1, Integer::sum);
            String company = extractCompany(title);
            if (company != null) companies.merge(company, 1, Integer::sum);
            String exp = matchFirst(source, "(\\d+\\s*-\\s*\\d+\\s*年|\\d+\\s*年以上|应届|不限)");
            if (exp != null) experience.merge(exp.replaceAll("\\s+", ""), 1, Integer::sum);
            String edu = matchFirst(source, "(本科|硕士|博士|大专|专科|学历不限)");
            if (edu != null) education.merge(edu, 1, Integer::sum);

            Integer[] salary = parseSalary(source);
            if (salary != null) {
                salaryParsed++;
                if (salary[0] != null || salary[1] != null) {
                    int min = salary[0] == null ? salary[1] : salary[0];
                    int max = salary[1] == null ? salary[0] : salary[1];
                    salaryMids.add((min + max) / 2);
                }
            } else {
                salaryUnknown++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sampleSize", jobs.size());
        result.put("scope", "仅统计你已导入并确认的岗位，不代表行业整体");
        result.put("generatedAt", java.time.LocalDateTime.now().toString());
        result.put("skills", Map.of(
                "required", topN(required, 20, jobs.size()),
                "preferred", topN(preferred, 20, jobs.size())));
        result.put("salary", salarySummary(salaryMids, salaryParsed, salaryUnknown));
        result.put("cities", topN(cities, 10, jobs.size()));
        result.put("companies", topN(companies, 10, jobs.size()));
        result.put("experience", topN(experience, 10, jobs.size()));
        result.put("education", topN(education, 8, jobs.size()));
        result.put("personal", personal(userId));
        return result;
    }

    /* ------------------------------ 个人求职 ------------------------------ */

    private Map<String, Object> personal(Long userId) {
        int resumes = count("SELECT COUNT(*) FROM interview_career_record WHERE user_id=? AND kind='RESUME' AND deleted=0", userId);
        int jobs = count("SELECT COUNT(*) FROM interview_career_record WHERE user_id=? AND kind='JOB' AND deleted=0", userId);
        int matches = count("SELECT COUNT(*) FROM interview_career_snapshot WHERE user_id=? AND kind='MATCH'", userId);
        Map<String, Integer> pipeline = new LinkedHashMap<>();
        for (var row : jdbc.queryForList("""
                SELECT COALESCE(p.status,'SAVED') AS status, COUNT(*) AS total
                  FROM interview_career_record r
                  LEFT JOIN interview_career_job_pipeline p ON p.record_id=r.id AND p.user_id=r.user_id
                 WHERE r.user_id=? AND r.kind='JOB' AND r.deleted=0
                 GROUP BY COALESCE(p.status,'SAVED')
                """, userId)) {
            pipeline.put(String.valueOf(row.get("status")), ((Number) row.get("total")).intValue());
        }
        int applied = 0;
        for (String status : List.of("APPLIED", "WRITTEN_TEST", "INTERVIEWING", "OFFER", "REJECTED", "CLOSED")) {
            applied += pipeline.getOrDefault(status, 0);
        }
        int interviewing = pipeline.getOrDefault("INTERVIEWING", 0) + pipeline.getOrDefault("OFFER", 0);
        int offers = pipeline.getOrDefault("OFFER", 0);

        Map<String, Object> personal = new LinkedHashMap<>();
        personal.put("resumes", resumes);
        personal.put("jobs", jobs);
        personal.put("matches", matches);
        personal.put("applied", applied);
        personal.put("interviewing", interviewing);
        personal.put("offers", offers);
        personal.put("pipeline", pipeline);
        Map<String, Object> rates = new LinkedHashMap<>();
        rates.put("applyToInterview", rate(interviewing, applied));
        rates.put("applyToOffer", rate(offers, applied));
        personal.put("conversion", rates);
        personal.put("rateNote", "转化率按当前状态快照计算，不是严格的漏斗归因；样本小于 5 时不具参考性");
        return personal;
    }

    private Double rate(int numerator, int denominator) {
        if (denominator <= 0) return null;
        return Math.round(numerator * 1000d / denominator) / 10d;
    }

    /* ------------------------------ 解析工具 ------------------------------ */

    private List<Map<String, Object>> jobRows(Long userId) {
        return jdbc.queryForList("""
                SELECT r.id, r.title, v.source_text, v.sections_json
                  FROM interview_career_record r
                  JOIN interview_career_version v ON v.record_id=r.id AND v.version_no=r.current_version
                 WHERE r.user_id=? AND r.kind='JOB' AND r.deleted=0
                """, userId);
    }

    private Map<String, List<Map<String, Object>>> sections(Object raw) {
        if (raw == null) return Map.of();
        try {
            return json.readValue(String.valueOf(raw),
                    new TypeReference<Map<String, List<Map<String, Object>>>>() { });
        } catch (Exception invalid) {
            return Map.of();
        }
    }

    private List<Map<String, Object>> topN(Map<String, Integer> counts, int limit, int sampleSize) {
        return counts.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getValue()).reversed())
                .limit(limit)
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", entry.getKey());
                    item.put("count", entry.getValue());
                    item.put("ratio", sampleSize == 0 ? 0
                            : Math.round(entry.getValue() * 1000d / sampleSize) / 10d);
                    return item;
                })
                .toList();
    }

    /**
     * 薪资解析：只认明确区间（15-25K / 20k-35k / 1.5万-2.5万），面议返回 null。
     * 返回值 [min, max]（单位 K/月），无法解析也返回 null——绝不按 0 处理。
     */
    Integer[] parseSalary(String source) {
        if (source == null) return null;
        if (source.contains("面议")) return null;
        Matcher matcher = RANGE.matcher(source);
        if (!matcher.find()) return null;
        try {
            int min = toK(matcher.group(1), matcher.group(0));
            int max = toK(matcher.group(2), matcher.group(0));
            return new Integer[]{min, max};
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private int toK(String value, String whole) {
        double number = Double.parseDouble(value);
        boolean wan = whole.contains("万");
        if (wan) number = number * 10;
        return (int) Math.round(number);
    }

    Map<String, Object> salarySummary(List<Integer> mids, int parsed, int unknown) {
        Map<String, Object> salary = new LinkedHashMap<>();
        salary.put("parsed", parsed);
        salary.put("unparsed", unknown);
        salary.put("note", "「面议」与未写区间的岗位不计入；薪资为月薪（K），不推断 13/14 薪");
        if (mids.isEmpty()) {
            salary.put("min", null);
            salary.put("median", null);
            salary.put("max", null);
            return salary;
        }
        List<Integer> sorted = mids.stream().sorted().toList();
        salary.put("min", sorted.get(0));
        salary.put("median", sorted.get(sorted.size() / 2));
        salary.put("max", sorted.get(sorted.size() - 1));
        return salary;
    }

    private String matchFirst(String source, String regex) {
        if (source == null) return null;
        Matcher matcher = Pattern.compile(regex).matcher(source);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String firstCity(String source) {
        if (source == null) return null;
        String[] cities = {"北京", "上海", "广州", "深圳", "杭州", "成都", "南京", "武汉", "西安",
                "苏州", "重庆", "天津", "长沙", "郑州", "合肥", "厦门", "青岛", "大连", "珠海", "远程"};
        for (String city : cities) if (source.contains(city)) return city;
        return null;
    }

    /** 从「公司 · 岗位」标题里取公司；没有分隔符则不猜。 */
    private String extractCompany(String title) {
        if (title == null) return null;
        for (String separator : List.of("·", " - ", "｜", "|")) {
            int index = title.indexOf(separator);
            if (index > 0 && index < title.length() - 1) {
                String company = title.substring(0, index).trim();
                if (company.length() <= 40) return company;
            }
        }
        return null;
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
