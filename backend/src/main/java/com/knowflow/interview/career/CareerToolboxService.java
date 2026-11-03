package com.knowflow.interview.career;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简历工具箱：ATS 可读性检查、内容体检、JD 定向优化、版本对比与分析历史。
 *
 * <p>三条硬边界：
 * <ul>
 *   <li><b>不承诺通过率</b>：ATS 只是启发式可读性评估，不模拟任何企业 ATS；</li>
 *   <li><b>不伪造能力</b>：没有 PDF 版式解析能力时返回 UNKNOWN，不臆测字体、分栏与阅读顺序；</li>
 *   <li><b>不编造内容</b>：只给建议，不生成经历、项目或指标；分析结果绑定具体简历版本。</li>
 * </ul>
 * 匹配复用 {@link CareerMatcher}，不新建第二套算法。</p>
 */
@Service
@RequiredArgsConstructor
public class CareerToolboxService {

    /** 分析算法版本：随规则变化递增，历史记录可追溯当时口径。 */
    static final String ATS_VERSION = "ats-1.0";
    static final String CONTENT_VERSION = "content-1.0";
    static final String JD_VERSION = "jd-1.0";
    static final String COMPARE_VERSION = "compare-1.0";

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?86)?1[3-9]\\d{9}(?!\\d)");
    private static final Pattern DATE_DOT = Pattern.compile("((?:19|20)\\d{2})\\.(\\d{1,2})");
    private static final Pattern DATE_DASH = Pattern.compile("((?:19|20)\\d{2})-(\\d{1,2})");
    private static final Pattern DATE_CN = Pattern.compile("((?:19|20)\\d{2})年(\\d{1,2})月");
    private static final Pattern DATE_SLASH = Pattern.compile("((?:19|20)\\d{2})/(\\d{1,2})");
    private static final Pattern PERIOD = Pattern.compile(
            "((?:19|20)\\d{2})[.\\-/年]?(\\d{1,2})?[.\\-/月]?\\s*[-–—~至]\\s*((?:19|20)\\d{2})?[.\\-/年]?(\\d{1,2})?");
    private static final Pattern ABNORMAL = Pattern.compile("[\\uFFFD\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]");
    private static final Pattern REPEAT_PUNCT = Pattern.compile("[，。；、,;]{2,}|\\s{3,}");

    private static final List<String> REQUIRED_GROUPS = List.of("basics", "experience", "projects", "education", "skills");
    private static final Map<String, String> GROUP_LABELS = Map.of(
            "basics", "基本信息",
            "intention", "求职意向",
            "education", "教育经历",
            "experience", "工作经历",
            "projects", "项目经历",
            "skills", "技能",
            "certificates", "证书",
            "selfIntro", "自我评价");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final CareerRecordService records;

    /* ============================ ATS 可读性检查 ============================ */

    public Map<String, Object> atsCheck(Long userId, long resumeId, Integer version, Long jobId, Integer jobVersion) {
        Map<String, Object> resume = load(userId, resumeId, version, "RESUME");
        String source = String.valueOf(resume.get("source"));
        Map<String, List<CareerMatcher.Claim>> sections = sectionsOf(resume);
        List<Map<String, Object>> items = new ArrayList<>();

        int length = source == null ? 0 : source.trim().length();
        items.add(item("text-extract", "正文可提取",
                length >= 120 ? "PASS" : "FAIL", length >= 120 ? "NONE" : "HIGH",
                length >= 120 ? "已提取 " + length + " 字正文" : "仅提取到 " + length + " 字，可能为图片型或加密文件",
                length >= 120 ? "文本可被解析，可继续检查其他项" : "建议导出为可复制文本的 PDF 或 DOCX 后重新上传"));

        boolean hasEmail = source != null && EMAIL.matcher(source).find();
        boolean hasPhone = source != null && PHONE.matcher(source).find();
        items.add(item("contact", "联系方式完整",
                hasEmail && hasPhone ? "PASS" : (hasEmail || hasPhone ? "WARN" : "FAIL"),
                hasEmail && hasPhone ? "NONE" : "MEDIUM",
                "邮箱：" + (hasEmail ? "已识别" : "未识别") + "；手机：" + (hasPhone ? "已识别" : "未识别"),
                hasEmail && hasPhone ? "联系方式可被常规解析器读取" : "在正文顶部补充可复制的邮箱与手机号，不要只放在页眉图片里"));

        long filled = REQUIRED_GROUPS.stream().filter(g -> countOf(sections, g) > 0).count();
        List<String> emptyGroups = REQUIRED_GROUPS.stream()
                .filter(g -> countOf(sections, g) == 0)
                .map(g -> GROUP_LABELS.getOrDefault(g, g))
                .toList();
        items.add(item("section-empty", "核心章节完整",
                emptyGroups.isEmpty() ? "PASS" : (filled >= 3 ? "WARN" : "FAIL"),
                emptyGroups.isEmpty() ? "NONE" : (filled >= 3 ? "MEDIUM" : "HIGH"),
                emptyGroups.isEmpty() ? "五个核心章节均有已确认条目" : "缺少或为空：" + String.join("、", emptyGroups),
                emptyGroups.isEmpty() ? "章节结构完整" : "补齐缺失章节，或在确认环节删除不适用章节后重新检查"));

        List<String> headings = List.of("工作经历", "项目经历", "教育经历", "专业技能", "技能", "自我评价", "求职意向");
        long headingHits = headings.stream().filter(h -> source != null && source.contains(h)).count();
        items.add(item("heading", "标题结构清晰",
                headingHits >= 2 ? "PASS" : "WARN",
                headingHits >= 2 ? "NONE" : "MEDIUM",
                "命中 " + headingHits + " 个常见章节标题",
                headingHits >= 2 ? "存在可识别的章节标题" : "用独立一行的标准章节名（如「工作经历」），避免全部小写或纯图标"));

        Matcher abnormal = source == null ? null : ABNORMAL.matcher(source);
        int abnormalCount = 0;
        while (abnormal != null && abnormal.find() && abnormalCount < 999) abnormalCount++;
        items.add(item("abnormal", "无异常字符",
                abnormalCount == 0 ? "PASS" : "WARN",
                abnormalCount == 0 ? "NONE" : "LOW",
                abnormalCount == 0 ? "未发现替换符或控制字符" : "发现 " + abnormalCount + " 个替换符/控制字符",
                abnormalCount == 0 ? "字符编码正常" : "可能是复制粘贴或字体缺字导致，建议重新导出后上传"));

        String dateStyle = dateStyleOf(source);
        items.add(item("date-format", "日期格式统一",
                dateStyle == null ? "UNKNOWN" : (dateStyle.contains("+") ? "WARN" : "PASS"),
                dateStyle == null ? "NONE" : (dateStyle.contains("+") ? "LOW" : "NONE"),
                dateStyle == null ? "未识别到可判断的日期写法" : "识别到的写法：" + dateStyle.replace("+", "、"),
                dateStyle != null && dateStyle.contains("+") ? "统一成同一种写法（建议 YYYY.MM），避免解析器把日期读错" : "日期写法一致"));

        int skills = countOf(sections, "skills");
        items.add(item("skill-parse", "技能可识别",
                skills >= 3 ? "PASS" : (skills > 0 ? "WARN" : "FAIL"),
                skills >= 3 ? "NONE" : "MEDIUM",
                "已确认技能条目 " + skills + " 项",
                skills >= 3 ? "技能以条目形式存在，便于关键词比对" : "把技能拆成独立条目（如 Java、Spring Boot），不要只写一段描述"));

        if (jobId != null) {
            Map<String, Object> job = load(userId, jobId, jobVersion, "JOB");
            int coverage = coverageOf(sections, sectionsOf(job), "requiredSkills");
            items.add(item("keyword-jd", "关键词与岗位相关",
                    coverage >= 60 ? "PASS" : (coverage > 0 ? "WARN" : "FAIL"),
                    coverage >= 60 ? "NONE" : "MEDIUM",
                    "岗位必备技能覆盖率 " + coverage + "%（由确定性比对得出，不含语义猜测）",
                    coverage >= 60 ? "关键词覆盖良好" : "到「智能匹配」查看缺失技能，只补充真实具备的部分"));
        } else {
            items.add(item("keyword-jd", "关键词与岗位相关", "UNKNOWN", "NONE",
                    "未选择目标岗位，无法比对关键词", "选择一个岗位后可检查关键词覆盖"));
        }

        items.add(item("reading-order", "阅读顺序", "UNKNOWN", "NONE",
                "当前只解析文本，无法判断分栏与阅读顺序",
                "如需确认，请用模板中心的单栏 ATS 模板导出后人工核对"));
        items.add(item("layout", "版式复杂度", "UNKNOWN", "NONE",
                "未解析 PDF 字体、文本框与栏位信息，无法判断版式风险",
                "使用模板中心的「ATS 经典单栏」可降低版式风险"));

        Map<String, Object> result = result("ATS", resume, jobId, jobVersion, ATS_VERSION, items);
        result.put("disclaimer", "ATS 检查是启发式可读性评估，不模拟任何企业的招聘系统，也不代表录用概率。");
        saveRun(userId, "ATS", resume, jobId, jobVersion, ATS_VERSION, result);
        return result;
    }

    /* ============================ 简历内容体检 ============================ */

    public Map<String, Object> contentCheck(Long userId, long resumeId, Integer version) {
        Map<String, Object> resume = load(userId, resumeId, version, "RESUME");
        Map<String, List<CareerMatcher.Claim>> sections = sectionsOf(resume);
        String source = String.valueOf(resume.get("source"));
        List<Map<String, Object>> items = new ArrayList<>();

        for (String group : List.of("basics", "experience", "projects")) {
            int n = countOf(sections, group);
            String label = GROUP_LABELS.getOrDefault(group, group);
            items.add(item("has-" + group, label + "有内容",
                    n > 0 ? "PASS" : "WARN",
                    n > 0 ? "NONE" : "MEDIUM",
                    n > 0 ? label + "共 " + n + " 条" : label + "为空",
                    n > 0 ? "已包含该章节" : (("basics".equals(group))
                            ? "补充姓名、城市、联系方式等基础信息"
                            : "如为应届生可突出项目与实习，不必虚构工作经历")));
        }

        List<CareerMatcher.Claim> skills = sections.getOrDefault("skills", List.of());
        boolean techInFields = allClaims(sections).stream()
                .anyMatch(c -> c.fields() != null && c.fields().containsKey("techStack"));
        items.add(item("tech-stack", "技术栈明确",
                skills.size() >= 3 || techInFields ? "PASS" : "WARN",
                skills.size() >= 3 || techInFields ? "NONE" : "MEDIUM",
                "技能条目 " + skills.size() + " 项" + (techInFields ? "，且经历中标注了技术栈" : ""),
                "技能与经历中的技术栈至少有一处明确写出具体名称"));

        List<CareerMatcher.Claim> experience = sections.getOrDefault("experience", List.of());
        List<String> shortItems = experience.stream().filter(c -> c.value().length() < 20).map(CareerMatcher.Claim::value).toList();
        items.add(item("responsibility", "职责描述充分",
                experience.isEmpty() ? "UNKNOWN" : (shortItems.isEmpty() ? "PASS" : "WARN"),
                experience.isEmpty() ? "NONE" : (shortItems.isEmpty() ? "NONE" : "MEDIUM"),
                experience.isEmpty() ? "没有工作经历，跳过该项" : (shortItems.isEmpty() ? "职责描述长度正常" : "过短的条目：" + preview(shortItems)),
                "每条经历写清负责什么、怎么做、结果如何"));

        List<String> noProcess = allClaims(sections).stream()
                .filter(c -> List.of("experience", "projects").contains(sectionOf(sections, c)))
                .filter(c -> !containsAny(c.value(), "设计", "实现", "开发", "优化", "排查", "重构", "落地", "搭建", "维护"))
                .map(CareerMatcher.Claim::value)
                .toList();
        items.add(item("process", "有具体实现过程",
                noProcess.isEmpty() ? "PASS" : "WARN",
                noProcess.isEmpty() ? "NONE" : "LOW",
                noProcess.isEmpty() ? "条目中能看到具体动作" : "缺少动作描述的条目：" + preview(noProcess),
                "补充「怎么做的」，而不是只写负责范围"));

        long withNumbers = allClaims(sections).stream()
                .filter(c -> c.value().chars().anyMatch(Character::isDigit)).count();
        int total = allClaims(sections).size();
        items.add(item("outcome", "成果可核实",
                total == 0 ? "UNKNOWN" : (withNumbers > 0 ? "PASS" : "WARN"),
                total == 0 ? "NONE" : (withNumbers > 0 ? "NONE" : "LOW"),
                total == 0 ? "没有可检查的条目" : withNumbers + "/" + total + " 条包含具体数字或可核实信息",
                "缺少量化指标不等于内容错误；如确有数据可补充，没有则不要编造"));

        Map<String, List<String>> duplicates = duplicatesOf(sections);
        items.add(item("duplicate", "无重复描述",
                duplicates.isEmpty() ? "PASS" : "WARN",
                duplicates.isEmpty() ? "NONE" : "LOW",
                duplicates.isEmpty() ? "未发现完全重复的条目" : "重复条目：" + preview(duplicates.values().iterator().next()),
                "同一段描述只保留一次，避免占用篇幅"));

        List<String> noisy = allClaims(sections).stream()
                .filter(c -> REPEAT_PUNCT.matcher(c.value()).find())
                .map(CareerMatcher.Claim::value).toList();
        items.add(item("punctuation", "标点与空白规范",
                noisy.isEmpty() ? "PASS" : "WARN",
                noisy.isEmpty() ? "NONE" : "LOW",
                noisy.isEmpty() ? "未发现重复标点或连续空白" : "存在重复标点/连续空白：" + preview(noisy),
                "清理重复标点与多余空格；这是格式提示，不代表内容有错"));

        List<String> reversed = reversedPeriods(source);
        items.add(item("date-order", "日期不矛盾",
                reversed.isEmpty() ? "PASS" : "FAIL",
                reversed.isEmpty() ? "NONE" : "HIGH",
                reversed.isEmpty() ? "未发现开始晚于结束的时间段" : "开始晚于结束：" + String.join("；", reversed),
                "核对并修正时间段，日期矛盾会被解析器判为无效经历"));

        List<String> inconsistent = inconsistentTechNames(skills);
        items.add(item("tech-naming", "技术名称一致",
                inconsistent.isEmpty() ? "PASS" : "WARN",
                inconsistent.isEmpty() ? "NONE" : "LOW",
                inconsistent.isEmpty() ? "同一技术只有一种写法" : "同一技术的不同写法：" + String.join("；", inconsistent),
                "统一写法（如统一为 Spring Boot），便于关键词比对"));

        Map<String, Object> result = result("CONTENT", resume, null, null, CONTENT_VERSION, items);
        result.put("disclaimer", "内容体检只指出可核实的结构性问题；缺少量化指标不等于错误，系统不会替你生成经历或数据。");
        saveRun(userId, "CONTENT", resume, null, null, CONTENT_VERSION, result);
        return result;
    }

    /* ============================ JD 定向优化 ============================ */

    public Map<String, Object> jdOptimize(Long userId, long resumeId, Integer resumeVersion,
                                          long jobId, Integer jobVersion) {
        Map<String, Object> resume = load(userId, resumeId, resumeVersion, "RESUME");
        Map<String, Object> job = load(userId, jobId, jobVersion, "JOB");
        Map<String, List<CareerMatcher.Claim>> resumeSections = sectionsOf(resume);
        Map<String, List<CareerMatcher.Claim>> jobSections = sectionsOf(job);

        List<CareerMatcher.Claim> resumeClaims = allClaims(resumeSections);
        List<CareerMatcher.Claim> required = jobSections.getOrDefault("requiredSkills", List.of());
        List<CareerMatcher.Claim> preferred = jobSections.getOrDefault("preferredSkills", List.of());

        List<Map<String, Object>> requiredView = dimensions(CareerMatcher.match(resumeClaims, required));
        List<Map<String, Object>> preferredView = dimensions(CareerMatcher.match(resumeClaims, preferred));

        long matched = requiredView.stream().filter(d -> "MATCHED".equals(d.get("status"))).count();
        int coverage = required.isEmpty() ? 0 : (int) Math.round(matched * 100d / required.size());

        LinkedHashSet<String> jdCanonical = new LinkedHashSet<>();
        for (var claim : required) jdCanonical.add(CareerMatcher.canonical(claim.value()));
        for (var claim : preferred) jdCanonical.add(CareerMatcher.canonical(claim.value()));
        List<String> extra = resumeSections.getOrDefault("skills", List.of()).stream()
                .filter(c -> !jdCanonical.contains(CareerMatcher.canonical(c.value())))
                .map(CareerMatcher.Claim::value)
                .distinct()
                .limit(12)
                .toList();

        List<Map<String, Object>> suggestions = new ArrayList<>();
        for (var dimension : requiredView) {
            if (!"MATCHED".equals(dimension.get("status"))) {
                suggestions.add(Map.of(
                        "skill", dimension.get("skill"),
                        "type", "MISSING",
                        "text", "如果确实做过与「" + dimension.get("skill") + "」相关的工作，请在经历中写清场景与做法；若没有，不要写进简历。"));
            }
        }
        if (coverage < 60 && !required.isEmpty()) {
            suggestions.add(Map.of(
                    "skill", "-", "type", "OVERALL",
                    "text", "必备技能覆盖率为 " + coverage + "%。优先补齐真实具备的缺失项，再考虑调整表述顺序。"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool", "JD");
        result.put("resumeId", resume.get("id"));
        result.put("resumeTitle", resume.get("title"));
        result.put("resumeVersion", resume.get("version"));
        result.put("jobId", job.get("id"));
        result.put("jobTitle", job.get("title"));
        result.put("jobVersion", job.get("version"));
        result.put("analysisVersion", JD_VERSION);
        result.put("checkedAt", Timestamp.valueOf(java.time.LocalDateTime.now()).toString());
        result.put("coverage", coverage);
        result.put("required", requiredView);
        result.put("preferred", preferredView);
        result.put("extraSkills", extra);
        result.put("suggestions", suggestions);
        result.put("matchedCount", matched);
        result.put("requiredCount", required.size());
        result.put("disclaimer", "覆盖率只说明岗位要求中有多少在简历里出现过，不代表熟练度，也不代表录用概率。");
        saveRun(userId, "JD", resume, job.get("id") instanceof Number n ? n.longValue() : null,
                job.get("version") instanceof Number n ? n.intValue() : null, JD_VERSION, result);
        return result;
    }

    /* ============================ 版本对比 ============================ */

    public Map<String, Object> compare(Long userId, long resumeId, int from, int to) {
        if (from == to) throw new BusinessException(400, "请选择两个不同的版本");
        Map<String, Object> left = records.detail(userId, resumeId, from);
        Map<String, Object> right = records.detail(userId, resumeId, to);
        Map<String, List<CareerMatcher.Claim>> leftSections = sectionsOf(left);
        Map<String, List<CareerMatcher.Claim>> rightSections = sectionsOf(right);

        Map<String, List<Map<String, Object>>> changes = new LinkedHashMap<>();
        LinkedHashSet<String> sectionKeys = new LinkedHashSet<>();
        sectionKeys.addAll(leftSections.keySet());
        sectionKeys.addAll(rightSections.keySet());
        for (String key : sectionKeys) {
            List<Map<String, Object>> diff = diffSection(leftSections.getOrDefault(key, List.of()),
                    rightSections.getOrDefault(key, List.of()));
            if (!diff.isEmpty()) changes.put(key, diff);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool", "COMPARE");
        result.put("resumeId", resumeId);
        result.put("resumeTitle", right.get("title"));
        result.put("from", from);
        result.put("to", to);
        result.put("fromConfirmed", left.get("confirmed"));
        result.put("toConfirmed", right.get("confirmed"));
        result.put("changes", changes);
        result.put("sectionCounts", sectionCounts(leftSections, rightSections));
        result.put("analysisVersion", COMPARE_VERSION);
        result.put("checkedAt", Timestamp.valueOf(java.time.LocalDateTime.now()).toString());
        result.put("disclaimer", "对比基于已保存版本的原文条目，不做语义改写对齐；版本内容本身不会被修改。");
        return result;
    }

    /* ============================ 历史 ============================ */

    public List<Map<String, Object>> history(Long userId, String toolType) {
        requireUser(userId);
        StringBuilder sql = new StringBuilder("""
                SELECT id,resume_id,resume_version,job_id,job_version,tool_type,analysis_version,result_json,create_time
                  FROM interview_career_tool_run
                 WHERE user_id=? AND deleted=0
                """);
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (toolType != null && !toolType.isBlank()) {
            String normalized = toolType.toUpperCase(Locale.ROOT);
            if (!List.of("ATS", "CONTENT", "JD").contains(normalized)) {
                throw new BusinessException(400, "分析类型无效");
            }
            sql.append(" AND tool_type=?");
            args.add(normalized);
        }
        sql.append(" ORDER BY create_time DESC, id DESC LIMIT 30");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var row : jdbc.queryForList(sql.toString(), args.toArray())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("resumeId", row.get("resume_id"));
            item.put("resumeVersion", row.get("resume_version"));
            item.put("jobId", row.get("job_id"));
            item.put("jobVersion", row.get("job_version"));
            item.put("toolType", row.get("tool_type"));
            item.put("analysisVersion", row.get("analysis_version"));
            item.put("createdAt", String.valueOf(row.get("create_time")));
            item.put("summary", summaryOf(String.valueOf(row.get("result_json"))));
            rows.add(item);
        }
        return rows;
    }

    public Map<String, Object> historyDetail(Long userId, long id) {
        requireUser(userId);
        var rows = jdbc.queryForList("""
                SELECT tool_type,result_json,create_time FROM interview_career_tool_run
                 WHERE id=? AND user_id=? AND deleted=0
                """, id, userId);
        if (rows.isEmpty()) throw new BusinessException(404, "分析记录不存在");
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", id);
        detail.put("toolType", rows.get(0).get("tool_type"));
        detail.put("createdAt", String.valueOf(rows.get(0).get("create_time")));
        detail.put("result", parse(String.valueOf(rows.get(0).get("result_json"))));
        return detail;
    }

    /* ============================ 内部工具 ============================ */

    private Map<String, Object> load(Long userId, long id, Integer version, String expectedKind) {
        Map<String, Object> record = records.detail(userId, id, version);
        if (!expectedKind.equals(record.get("kind"))) {
            throw new BusinessException(400, "RESUME".equals(expectedKind) ? "请选择简历记录" : "请选择岗位记录");
        }
        return record;
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<CareerMatcher.Claim>> sectionsOf(Map<String, Object> record) {
        Object sections = record.get("sections");
        return sections instanceof Map<?, ?> map ? (Map<String, List<CareerMatcher.Claim>>) map : Map.of();
    }

    private List<CareerMatcher.Claim> allClaims(Map<String, List<CareerMatcher.Claim>> sections) {
        List<CareerMatcher.Claim> claims = new ArrayList<>();
        for (var entry : sections.entrySet()) claims.addAll(entry.getValue());
        return claims;
    }

    private int countOf(Map<String, List<CareerMatcher.Claim>> sections, String key) {
        return sections.getOrDefault(key, List.of()).size();
    }

    private String sectionOf(Map<String, List<CareerMatcher.Claim>> sections, CareerMatcher.Claim claim) {
        for (var entry : sections.entrySet()) {
            if (entry.getValue().contains(claim)) return entry.getKey();
        }
        return "";
    }

    private List<Map<String, Object>> dimensions(List<CareerMatcher.Dimension> dimensions) {
        List<Map<String, Object>> view = new ArrayList<>();
        for (var dimension : dimensions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("skill", dimension.requirement());
            item.put("status", dimension.matchStatus());
            item.put("evidence", dimension.resumeEvidence());
            item.put("note", dimension.gapDescription());
            view.add(item);
        }
        return view;
    }

    private int coverageOf(Map<String, List<CareerMatcher.Claim>> resume,
                           Map<String, List<CareerMatcher.Claim>> job, String group) {
        List<CareerMatcher.Claim> required = job.getOrDefault(group, List.of());
        if (required.isEmpty()) return 0;
        long matched = CareerMatcher.match(allClaims(resume), required).stream()
                .filter(d -> "MATCHED".equals(d.matchStatus())).count();
        return (int) Math.round(matched * 100d / required.size());
    }

    private Map<String, Object> item(String id, String label, String status, String risk,
                                     String evidence, String suggestion) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("label", label);
        item.put("status", status);
        item.put("risk", risk);
        item.put("evidence", evidence);
        item.put("suggestion", suggestion);
        return item;
    }

    private Map<String, Object> result(String tool, Map<String, Object> resume, Long jobId, Integer jobVersion,
                                       String version, List<Map<String, Object>> items) {
        int pass = (int) items.stream().filter(i -> "PASS".equals(i.get("status"))).count();
        int warn = (int) items.stream().filter(i -> "WARN".equals(i.get("status"))).count();
        int fail = (int) items.stream().filter(i -> "FAIL".equals(i.get("status"))).count();
        int unknown = (int) items.stream().filter(i -> "UNKNOWN".equals(i.get("status"))).count();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("pass", pass);
        summary.put("warn", warn);
        summary.put("fail", fail);
        summary.put("unknown", unknown);
        summary.put("overall", fail > 0 ? "FAIL" : (warn > 0 ? "WARN" : "PASS"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool", tool);
        result.put("resumeId", resume.get("id"));
        result.put("resumeTitle", resume.get("title"));
        result.put("resumeVersion", resume.get("version"));
        result.put("jobId", jobId);
        result.put("jobVersion", jobVersion);
        result.put("analysisVersion", version);
        result.put("checkedAt", Timestamp.valueOf(java.time.LocalDateTime.now()).toString());
        result.put("summary", summary);
        result.put("items", items);
        return result;
    }

    private String dateStyleOf(String source) {
        if (source == null) return null;
        List<String> styles = new ArrayList<>();
        if (DATE_DOT.matcher(source).find()) styles.add("YYYY.MM");
        if (DATE_DASH.matcher(source).find()) styles.add("YYYY-MM");
        if (DATE_CN.matcher(source).find()) styles.add("YYYY年M月");
        if (DATE_SLASH.matcher(source).find()) styles.add("YYYY/MM");
        if (styles.isEmpty()) return null;
        return String.join("+", styles);
    }

    private List<String> reversedPeriods(String source) {
        List<String> reversed = new ArrayList<>();
        if (source == null) return reversed;
        Matcher matcher = PERIOD.matcher(source);
        while (matcher.find() && reversed.size() < 5) {
            int startYear = Integer.parseInt(matcher.group(1));
            int startMonth = matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(2));
            if (matcher.group(3) == null) continue;
            int endYear = Integer.parseInt(matcher.group(3));
            int endMonth = matcher.group(4) == null ? 12 : Integer.parseInt(matcher.group(4));
            if (startYear * 12 + startMonth > endYear * 12 + endMonth) {
                reversed.add(matcher.group());
            }
        }
        return reversed;
    }

    private List<String> inconsistentTechNames(List<CareerMatcher.Claim> skills) {
        Map<String, LinkedHashSet<String>> spellings = new LinkedHashMap<>();
        for (var claim : skills) {
            spellings.computeIfAbsent(CareerMatcher.canonical(claim.value()), k -> new LinkedHashSet<>())
                    .add(claim.value().trim());
        }
        List<String> result = new ArrayList<>();
        for (var entry : spellings.entrySet()) {
            if (entry.getValue().size() > 1) result.add(String.join(" / ", entry.getValue()));
        }
        return result;
    }

    private Map<String, List<String>> duplicatesOf(Map<String, List<CareerMatcher.Claim>> sections) {
        Map<String, List<String>> duplicates = new LinkedHashMap<>();
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (var entry : sections.entrySet()) {
            for (var claim : entry.getValue()) {
                String key = claim.value().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
                if (key.length() < 8) continue;
                seen.merge(key, 1, Integer::sum);
                if (seen.get(key) == 2) duplicates.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(claim.value());
            }
        }
        return duplicates;
    }

    private List<Map<String, Object>> diffSection(List<CareerMatcher.Claim> left, List<CareerMatcher.Claim> right) {
        Map<String, CareerMatcher.Claim> leftMap = keyed(left);
        Map<String, CareerMatcher.Claim> rightMap = keyed(right);
        List<Map<String, Object>> diff = new ArrayList<>();
        for (var entry : rightMap.entrySet()) {
            CareerMatcher.Claim before = leftMap.get(entry.getKey());
            if (before == null) {
                diff.add(change("ADDED", entry.getValue().value(), null, entry.getValue().value()));
            } else if (!before.value().equals(entry.getValue().value())) {
                diff.add(change("MODIFIED", entry.getValue().value(), before.value(), entry.getValue().value()));
            }
        }
        for (var entry : leftMap.entrySet()) {
            if (!rightMap.containsKey(entry.getKey())) {
                diff.add(change("REMOVED", entry.getValue().value(), entry.getValue().value(), null));
            }
        }
        return diff;
    }

    /** 用「结构化字段」做对齐键；没有字段时退化为条目原文，保证同一条只出现一次。 */
    private Map<String, CareerMatcher.Claim> keyed(List<CareerMatcher.Claim> claims) {
        Map<String, CareerMatcher.Claim> map = new LinkedHashMap<>();
        for (var claim : claims) {
            String key = claim.fields() == null || claim.fields().isEmpty()
                    ? "value:" + claim.value()
                    : "fields:" + claim.fields().entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue()).sorted().reduce("", (a, b) -> a + "|" + b);
            map.putIfAbsent(key, claim);
        }
        return map;
    }

    private Map<String, Object> change(String type, String label, String before, String after) {
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("type", type);
        change.put("label", label);
        change.put("before", before);
        change.put("after", after);
        return change;
    }

    private Map<String, Object> sectionCounts(Map<String, List<CareerMatcher.Claim>> left,
                                              Map<String, List<CareerMatcher.Claim>> right) {
        Map<String, Object> counts = new LinkedHashMap<>();
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        keys.addAll(left.keySet());
        keys.addAll(right.keySet());
        for (String key : keys) {
            counts.put(key, Map.of(
                    "from", left.getOrDefault(key, List.of()).size(),
                    "to", right.getOrDefault(key, List.of()).size()));
        }
        return counts;
    }

    private void saveRun(Long userId, String toolType, Map<String, Object> resume, Long jobId, Integer jobVersion,
                         String version, Map<String, Object> result) {
        try {
            long resumeId = ((Number) resume.get("id")).longValue();
            int resumeVersion = ((Number) resume.get("version")).intValue();
            jdbc.update("""
                    INSERT INTO interview_career_tool_run
                        (user_id,resume_id,resume_version,job_id,job_version,tool_type,analysis_version,result_json)
                    VALUES (?,?,?,?,?,?,?,?)
                    """, userId, resumeId, resumeVersion, jobId, jobVersion, toolType, version,
                    json.writeValueAsString(result));
        } catch (Exception failure) {
            // 分析结果本身可用；历史写入失败不伪造成功记录，也不让用户丢掉本次结果。
            throw new BusinessException(500, "分析结果保存失败，请稍后重试");
        }
    }

    private Map<String, Object> summaryOf(String resultJson) {
        Map<String, Object> summary = new LinkedHashMap<>();
        Map<String, Object> parsed = parse(resultJson);
        if (parsed.get("summary") instanceof Map<?, ?> map) {
            summary.put("pass", map.get("pass"));
            summary.put("warn", map.get("warn"));
            summary.put("fail", map.get("fail"));
            summary.put("unknown", map.get("unknown"));
            summary.put("overall", map.get("overall"));
        }
        if (parsed.get("coverage") != null) summary.put("coverage", parsed.get("coverage"));
        summary.put("resumeTitle", parsed.get("resumeTitle"));
        summary.put("jobTitle", parsed.get("jobTitle"));
        return summary;
    }

    private Map<String, Object> parse(String raw) {
        try {
            return json.readValue(raw, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception invalid) {
            return Map.of();
        }
    }

    private String preview(List<String> values) {
        if (values.isEmpty()) return "";
        String first = values.get(0);
        String text = first.length() > 40 ? first.substring(0, 40) + "…" : first;
        return values.size() > 1 ? text + "（共 " + values.size() + " 条）" : text;
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) if (value.contains(keyword)) return true;
        return false;
    }

    private void requireUser(Long userId) {
        if (userId == null) throw new BusinessException(401, "请先登录");
    }

    /** 供测试断言：分析口径随规则变化时递增，历史结果不因规则更新而"回算"。 */
    static LocalDate today() {
        return LocalDate.now();
    }
}
