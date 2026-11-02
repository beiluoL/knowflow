package com.knowflow.interview.skill;

import com.knowflow.interview.core.InterviewQuestionBank;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

/**
 * Phase 1 面试 Skill Registry。
 *
 * <p>技能以 catalog.yml 为唯一登记源，等价承载 InterviewGuide 的 SKILL.md、skill.meta.yml、
 * 出题规则、评分锚点与追问规则。Skill 只映射到既有题库方向，不新建、不覆盖题目 ID。</p>
 */
@Component
public class InterviewSkillRegistry {

    /** 一条面试技能定义；所有字段均为服务端白名单内容，不接受用户自定义覆盖。 */
    public record Skill(
            String id,
            String name,
            String category,
            String level,
            List<String> roles,
            int minYears,
            int maxYears,
            String difficulty,
            String trackCode,
            List<String> tags,
            List<String> jdKeywords,
            String instructions,
            List<String> questionRules,
            List<String> followupRules,
            Map<String, String> rubric,
            List<String> references) {
    }

    /** 推荐结果：分数与命中的真实 JD 关键词，用于前端解释而不是“黑箱推荐”。 */
    public record Match(Skill skill, int score, List<String> matchedKeywords) {
    }

    private static final Set<String> DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD", "SENIOR");
    private final Map<String, Skill> skills;
    private final List<Skill> ordered;

    public InterviewSkillRegistry() {
        this.skills = load();
        this.ordered = List.copyOf(skills.values());
    }

    public List<Skill> list() {
        return ordered;
    }

    public Skill find(String id) {
        return id == null ? null : skills.get(id);
    }

    /** 使用真实题库方向列表，供前端组合岗位/方向/难度。 */
    public List<InterviewQuestionBank.Track> tracks() {
        return InterviewQuestionBank.TRACKS;
    }

    /** 按 JD、岗位、难度与经验年限做确定性推荐；无匹配时返回空列表，不编造技能。 */
    public List<Match> recommend(String jd, String role, String difficulty, Integer years) {
        String text = normal(jd);
        String roleText = normal(role);
        List<Match> matches = new ArrayList<>();
        for (Skill skill : ordered) {
            List<String> matched = new ArrayList<>();
            int score = 0;
            if (!text.isBlank()) {
                for (String keyword : skill.jdKeywords()) {
                    if (text.contains(normal(keyword))) {
                        matched.add(keyword);
                        score += 3;
                    }
                }
                for (String tag : skill.tags()) {
                    if (text.contains(normal(tag)) && !matched.contains(tag)) {
                        matched.add(tag);
                        score += 1;
                    }
                }
            }
            if (!roleText.isBlank()) {
                for (String item : skill.roles()) {
                    if (roleText.contains(normal(item)) || normal(item).contains(roleText)) {
                        score += 6;
                        break;
                    }
                }
            }
            if (difficulty != null && !difficulty.isBlank()) {
                if (skill.difficulty().equalsIgnoreCase(difficulty)) score += 3;
            }
            if (years != null) {
                if (years >= skill.minYears() && years <= skill.maxYears()) score += 2;
            }
            if (score > 0) matches.add(new Match(skill, score, List.copyOf(matched)));
        }
        matches.sort(Comparator.comparingInt(Match::score).reversed().thenComparing(m -> m.skill().name()));
        return matches.size() > 5 ? matches.subList(0, 5) : matches;
    }

    /** 从 JD 中识别 catalog 已登记的技术关键词；只返回真实命中的词，不推测。 */
    public List<String> detectKeywords(String jd) {
        String text = normal(jd);
        if (text.isBlank()) return List.of();
        LinkedHashSet<String> found = new LinkedHashSet<>();
        for (Skill skill : ordered) {
            for (String keyword : skill.jdKeywords()) {
                if (text.contains(normal(keyword))) found.add(keyword);
            }
        }
        return List.copyOf(found);
    }

    private Map<String, Skill> load() {
        try (InputStream in = new ClassPathResource("interview/skills/catalog.yml").getInputStream()) {
            Object root = new Yaml().load(in);
            if (!(root instanceof Map<?, ?> map) || !(map.get("skills") instanceof List<?> rows)) {
                throw new IllegalStateException("面试 Skill 目录缺少 skills 列表");
            }
            LinkedHashMap<String, Skill> result = new LinkedHashMap<>();
            Set<String> allowedTracks = new HashSet<>();
            for (InterviewQuestionBank.Track track : InterviewQuestionBank.TRACKS) allowedTracks.add(track.code());
            allowedTracks.add("PROJECT");
            allowedTracks.add("RADAR_PRIVATE");
            for (Object row : rows) {
                if (!(row instanceof Map<?, ?> item)) throw new IllegalStateException("面试 Skill 条目格式无效");
                Skill skill = new Skill(
                        required(item, "id"),
                        required(item, "name"),
                        required(item, "category"),
                        required(item, "level"),
                        strings(item.get("roles")),
                        number(item.get("minYears")),
                        number(item.get("maxYears")),
                        required(item, "difficulty"),
                        required(item, "trackCode"),
                        strings(item.get("tags")),
                        strings(item.get("jdKeywords")),
                        required(item, "instructions"),
                        strings(item.get("questionRules")),
                        strings(item.get("followupRules")),
                        rubric(item.get("rubric")),
                        strings(item.get("references")));
                validate(skill, allowedTracks);
                if (result.putIfAbsent(skill.id(), skill) != null) {
                    throw new IllegalStateException("面试 Skill ID 重复：" + skill.id());
                }
            }
            if (result.isEmpty()) throw new IllegalStateException("面试 Skill 目录为空");
            return Collections.unmodifiableMap(result);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("面试 Skill 目录无法读取", e);
        }
    }

    private static void validate(Skill skill, Set<String> allowedTracks) {
        if (!skill.id().matches("[a-z0-9-]{2,40}")) throw new IllegalStateException("Skill ID 无效：" + skill.id());
        if (!DIFFICULTIES.contains(skill.difficulty())) throw new IllegalStateException("Skill 难度无效：" + skill.id());
        if (!allowedTracks.contains(skill.trackCode())) throw new IllegalStateException("Skill 映射到未登记题库方向：" + skill.id());
        if (skill.roles().isEmpty()) throw new IllegalStateException("Skill 缺少适用岗位：" + skill.id());
        if (skill.minYears() < 0 || skill.maxYears() < skill.minYears()) throw new IllegalStateException("Skill 年限范围无效：" + skill.id());
        if (skill.questionRules().isEmpty() || skill.followupRules().isEmpty()) throw new IllegalStateException("Skill 缺少出题或追问规则：" + skill.id());
        if (skill.rubric().isEmpty()) throw new IllegalStateException("Skill 缺少评分标准：" + skill.id());
        if (skill.instructions().isBlank()) throw new IllegalStateException("Skill 缺少考察说明：" + skill.id());
    }

    private static String required(Map<?, ?> item, String key) {
        Object value = item.get(key);
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isEmpty()) throw new IllegalStateException("面试 Skill 缺少字段：" + key);
        return text;
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item == null) continue;
            String text = String.valueOf(item).trim();
            if (!text.isEmpty()) out.add(text);
        }
        return List.copyOf(out);
    }

    private static int number(Object value) {
        if (value instanceof Number n) return n.intValue();
        return 0;
    }

    private static Map<String, String> rubric(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        map.forEach((k, v) -> {
            if (k != null && v != null) out.put(String.valueOf(k), String.valueOf(v));
        });
        return Collections.unmodifiableMap(out);
    }

    private static String normal(String value) {
        return value == null ? "" : value.replaceAll("[\\p{P}\\p{Z}\\s]", "").toLowerCase(Locale.ROOT);
    }
}
