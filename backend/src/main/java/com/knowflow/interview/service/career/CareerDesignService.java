package com.knowflow.interview.service.career;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 简历排版设计（模板 + 主题）。
 *
 * <p><b>核心约束</b>：设计配置与简历内容版本完全独立——保存/切换模板只写本表，
 * 不会产生 {@code interview_career_version} 新版本，也不会改写原文；历史匹配与面试
 * 继续引用创建时的简历版本。</p>
 */
@Service
@RequiredArgsConstructor
public class CareerDesignService {

    /** 模板标识白名单：与前端 registry 的 6 套模板逐一对应。 */
    public static final List<String> TEMPLATE_IDS = List.of(
            "ats-classic", "cn-minimal", "java-backend", "java-ai", "modern-two-col", "graduate");

    public static final List<String> TEMPLATE_NAMES = List.of(
            "ATS 经典单栏", "中文极简", "Java 后端工程师", "Java + AI 工程师", "现代商务双栏", "应届生简历");

    private static final Set<String> SECTIONS = Set.of(
            "basics", "intention", "education", "experience", "projects", "skills", "certificates", "selfIntro", "custom");
    private static final Set<String> FONTS = Set.of("sans", "serif", "mono");
    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public List<Map<String, Object>> list(Long userId, long resumeId) {
        requireOwnResume(userId, resumeId);
        List<Map<String, Object>> designs = new ArrayList<>();
        for (var row : jdbc.queryForList("""
                SELECT id,name,template_id,template_version,theme_json,is_default,create_time,update_time
                  FROM interview_career_design
                 WHERE user_id=? AND resume_id=? AND deleted=0
                 ORDER BY is_default DESC, update_time DESC, id DESC
                """, userId, resumeId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("name", row.get("name"));
            item.put("templateId", row.get("template_id"));
            item.put("templateVersion", row.get("template_version"));
            item.put("isDefault", ((Number) row.get("is_default")).intValue() == 1);
            item.put("theme", parse(String.valueOf(row.get("theme_json"))));
            item.put("createdAt", String.valueOf(row.get("create_time")));
            item.put("updatedAt", String.valueOf(row.get("update_time")));
            designs.add(item);
        }
        return designs;
    }

    @Transactional
    public Map<String, Object> create(Long userId, long resumeId, DesignRequest body) {
        requireOwnResume(userId, resumeId);
        Map<String, Object> theme = normalize(body);
        int version = TEMPLATE_IDS.indexOf(String.valueOf(theme.get("templateId")));
        String name = body.name() == null || body.name().isBlank()
                ? TEMPLATE_NAMES.get(version) : body.name().trim();
        if (name.length() > 80) throw new BusinessException(400, "设计名称最多80字");
        boolean firstOne = countDesigns(userId, resumeId) == 0;
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO interview_career_design
                        (user_id,resume_id,name,template_id,template_version,theme_json,is_default)
                    VALUES (?,?,?,?,?,?,?)
                    """, new String[]{"id"});
            statement.setLong(1, userId);
            statement.setLong(2, resumeId);
            statement.setString(3, name);
            statement.setString(4, String.valueOf(theme.get("templateId")));
            statement.setString(5, "1.0");
            statement.setString(6, write(theme));
            statement.setInt(7, firstOne ? 1 : 0);
            return statement;
        }, key);
        long id = Objects.requireNonNull(key.getKey()).longValue();
        return one(userId, resumeId, id);
    }

    @Transactional
    public Map<String, Object> update(Long userId, long resumeId, long designId, DesignRequest body) {
        requireOwnResume(userId, resumeId);
        ownDesign(userId, resumeId, designId);
        Map<String, Object> theme = normalize(body);
        String name = body.name() == null || body.name().isBlank() ? null : body.name().trim();
        if (name != null && name.length() > 80) throw new BusinessException(400, "设计名称最多80字");
        jdbc.update("""
                UPDATE interview_career_design
                   SET name=COALESCE(?,name), template_id=?, template_version=?, theme_json=?, update_time=CURRENT_TIMESTAMP
                 WHERE id=? AND user_id=? AND resume_id=? AND deleted=0
                """, name, String.valueOf(theme.get("templateId")), "1.0", write(theme), designId, userId, resumeId);
        return one(userId, resumeId, designId);
    }

    @Transactional
    public void setDefault(Long userId, long resumeId, long designId) {
        requireOwnResume(userId, resumeId);
        ownDesign(userId, resumeId, designId);
        jdbc.update("UPDATE interview_career_design SET is_default=0 WHERE user_id=? AND resume_id=? AND deleted=0", userId, resumeId);
        jdbc.update("UPDATE interview_career_design SET is_default=1, update_time=CURRENT_TIMESTAMP WHERE id=? AND user_id=?", designId, userId);
    }

    @Transactional
    public void remove(Long userId, long resumeId, long designId) {
        requireOwnResume(userId, resumeId);
        ownDesign(userId, resumeId, designId);
        jdbc.update("UPDATE interview_career_design SET deleted=1, is_default=0, update_time=CURRENT_TIMESTAMP WHERE id=? AND user_id=?", designId, userId);
    }

    /** 模板目录：只返回元数据，渲染由前端组件完成，不在后端拼 HTML。 */
    public List<Map<String, Object>> templates() {
        List<Map<String, Object>> catalog = new ArrayList<>();
        for (int i = 0; i < TEMPLATE_IDS.size(); i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", TEMPLATE_IDS.get(i));
            item.put("name", TEMPLATE_NAMES.get(i));
            item.put("version", "1.0");
            catalog.add(item);
        }
        return catalog;
    }

    /* ------------------------------ 内部 ------------------------------ */

    /** 请求体：name 可空（用模板名），theme 为完整排版参数。 */
    public record DesignRequest(String name, Map<String, Object> theme) { }

    /**
     * 严格归一化：未知模板、越界数值、非法颜色、未知章节一律 400。
     * 不接受前端传入任意 HTML/CSS，避免把设计配置变成注入面。
     */
    private Map<String, Object> normalize(DesignRequest body) {
        if (body == null || body.theme() == null) throw new BusinessException(400, "缺少模板设计参数");
        Map<String, Object> theme = body.theme();
        String templateId = String.valueOf(theme.getOrDefault("templateId", ""));
        if (!TEMPLATE_IDS.contains(templateId)) throw new BusinessException(400, "模板不存在");

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("templateId", templateId);
        normalized.put("fontFamily", font(theme.get("fontFamily")));
        normalized.put("fontSize", number(theme.get("fontSize"), 9, 18, "字号"));
        normalized.put("lineHeight", decimal(theme.get("lineHeight"), 1.2, 2.4, "行距"));
        normalized.put("accentColor", color(theme.get("accentColor")));
        String pageSize = String.valueOf(theme.getOrDefault("pageSize", "A4"));
        if (!Set.of("A4", "Letter").contains(pageSize)) {
            throw new BusinessException(400, "不支持的纸张尺寸");
        }
        normalized.put("pageSize", pageSize);
        normalized.put("sectionGap", number(theme.get("sectionGap"), 4, 40, "章节间距"));

        Map<String, Object> margin = theme.get("margin") instanceof Map<?, ?> map
                ? cast(map) : Map.of();
        Map<String, Object> normalizedMargin = new LinkedHashMap<>();
        for (String side : List.of("top", "right", "bottom", "left")) {
            normalizedMargin.put(side, number(margin.get(side), 6, 40, "页边距"));
        }
        normalized.put("margin", normalizedMargin);

        List<String> order = sections(theme.get("sectionOrder"), "章节顺序");
        List<String> visible = sections(theme.get("visibleSections"), "显示章节");
        normalized.put("sectionOrder", order);
        normalized.put("visibleSections", visible);
        return normalized;
    }

    private String font(Object raw) {
        String value = raw == null ? "sans" : String.valueOf(raw);
        if (!FONTS.contains(value)) throw new BusinessException(400, "字体选项无效");
        return value;
    }

    private String color(Object raw) {
        String value = raw == null ? "#3B6FE0" : String.valueOf(raw).trim();
        if (!COLOR.matcher(value).matches()) throw new BusinessException(400, "主题颜色必须是 #RRGGBB");
        return value.toUpperCase();
    }

    private int number(Object raw, int min, int max, String label) {
        if (raw == null) throw new BusinessException(400, "缺少" + label);
        int value;
        try {
            value = (int) Math.round(Double.parseDouble(String.valueOf(raw)));
        } catch (NumberFormatException invalid) {
            throw new BusinessException(400, label + "必须是数字");
        }
        if (value < min || value > max) throw new BusinessException(400, label + "需在 " + min + "-" + max + " 之间");
        return value;
    }

    private double decimal(Object raw, double min, double max, String label) {
        if (raw == null) throw new BusinessException(400, "缺少" + label);
        double value;
        try {
            value = Double.parseDouble(String.valueOf(raw));
        } catch (NumberFormatException invalid) {
            throw new BusinessException(400, label + "必须是数字");
        }
        if (value < min || value > max) throw new BusinessException(400, label + "需在 " + min + "-" + max + " 之间");
        return Math.round(value * 10d) / 10d;
    }

    private List<String> sections(Object raw, String label) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) throw new BusinessException(400, "缺少" + label);
        if (list.size() > SECTIONS.size()) throw new BusinessException(400, label + "包含未知章节");
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (Object item : list) {
            String key = String.valueOf(item);
            if (!SECTIONS.contains(key)) throw new BusinessException(400, label + "包含未知章节");
            values.add(key);
        }
        return List.copyOf(values);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private int countDesigns(Long userId, long resumeId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM interview_career_design WHERE user_id=? AND resume_id=? AND deleted=0",
                Integer.class, userId, resumeId);
        return count == null ? 0 : count;
    }

    private void requireOwnResume(Long userId, long resumeId) {
        if (userId == null) throw new BusinessException(401, "请先登录");
        var rows = jdbc.queryForList(
                "SELECT id FROM interview_career_record WHERE id=? AND user_id=? AND kind='RESUME' AND deleted=0",
                resumeId, userId);
        if (rows.isEmpty()) throw new BusinessException(404, "简历不存在");
    }

    private void ownDesign(Long userId, long resumeId, long designId) {
        var rows = jdbc.queryForList("""
                SELECT id FROM interview_career_design
                 WHERE id=? AND user_id=? AND resume_id=? AND deleted=0
                """, designId, userId, resumeId);
        if (rows.isEmpty()) throw new BusinessException(404, "模板设计不存在");
    }

    private Map<String, Object> one(Long userId, long resumeId, long designId) {
        return list(userId, resumeId).stream()
                .filter(item -> ((Number) item.get("id")).longValue() == designId)
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "模板设计不存在"));
    }

    private String write(Map<String, Object> theme) {
        try {
            return json.writeValueAsString(theme);
        } catch (Exception invalid) {
            throw new BusinessException(400, "模板设计无法保存");
        }
    }

    private Map<String, Object> parse(String raw) {
        try {
            return json.readValue(raw, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception invalid) {
            return Map.of();
        }
    }
}
