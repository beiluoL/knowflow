package com.knowflow.interview.service.career.studio;

import com.knowflow.exception.BusinessException;
import com.knowflow.interview.career.CareerMatcher;
import com.knowflow.interview.service.career.CareerRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 内部版本化简历 Skill：确定性只读检查，不调用模型、不修改简历、不评价真实能力。 */
@Service
@RequiredArgsConstructor
public class ResumeStudioSkillService {
    private static final String VERSION = "1.0.0";
    private final CareerRecordService records;

    /** 可审计的 Skill 合同。 */
    public record Skill(String id, String version, String title, String targetRole,
                        List<String> inputs, String realEvidence, Map<String, Object> outputSchema,
                        String factValidation, String review, String promptVersion,
                        boolean modelRequired, List<String> highRiskProhibitions) { }
    /** 原文引用及其所在已确认章节。 */
    public record Evidence(String section, String originalQuote) { }
    /** 命中只表示发现文字证据，不等于能力验证。 */
    public record Finding(String id, String title, String status, List<Evidence> evidence,
                          String question, String suggestion) { }
    /** 一次只读检查绑定具体版本，规则版本随合同返回。 */
    public record CheckResult(long resumeId, int resumeVersion, Skill skill, String modelSource,
                              boolean writesResume, List<Finding> findings, String disclaimer) { }
    private record Rule(String id, String title, List<String> terms, String question) { }

    /** 当前内部注册表；无需安装外部 Skill 或创建新数据底座。 */
    public List<Skill> skills(Long userId) {
        requireUser(userId);
        return List.of(skill("java-ai", "Java + AI 应用工程师"),
                skill("senior-backend", "高级 Java 后端工程师"),
                skill("java-fullstack", "Java 全栈工程师"));
    }

    /** 归属与历史版本由现有记录服务校验；结果不自动进入审核或写回流程。 */
    public CheckResult check(Long userId, long resumeId, Integer version, String skillId) {
        Skill selected = skills(userId).stream().filter(s -> s.id().equals(skillId)).findFirst()
                .orElseThrow(() -> new BusinessException(400, "专项方向无效"));
        if (resumeId <= 0 || (version != null && version <= 0)) {
            throw new BusinessException(400, "简历或版本参数无效");
        }
        Map<String, Object> resume = records.detail(userId, resumeId, version);
        if (!"RESUME".equals(resume.get("kind"))) throw new BusinessException(400, "请选择简历记录");
        if (!Boolean.TRUE.equals(resume.get("confirmed"))) throw new BusinessException(409, "请先确认简历版本");
        String source = resume.get("source") instanceof String text ? text : "";
        List<Finding> findings = new ArrayList<>();
        Object sections = resume.get("sections");
        for (Rule rule : rules(skillId)) {
            List<Evidence> evidence = new ArrayList<>();
            if (sections instanceof Map<?, ?> groups) {
                for (var group : groups.entrySet()) {
                    if (!(group.getValue() instanceof List<?> claims)) continue;
                    for (Object value : claims) {
                        if (!(value instanceof CareerMatcher.Claim claim)) continue;
                        String quote = claim.quote();
                        if (quote == null || quote.isBlank() || !source.contains(quote)) continue;
                        String normalized = quote.toLowerCase(Locale.ROOT);
                        if (rule.terms().stream().anyMatch(term -> containsTerm(normalized, term))) {
                            Evidence item = new Evidence(String.valueOf(group.getKey()), quote);
                            if (!evidence.contains(item)) evidence.add(item);
                        }
                    }
                }
            }
            findings.add(new Finding(rule.id(), rule.title(), evidence.isEmpty() ? "NEEDS_CONFIRMATION" : "EVIDENCE_FOUND",
                    List.copyOf(evidence), rule.question(), evidence.isEmpty()
                    ? "当前已确认简历未提供可引用信息；请确认是否有真实经历，有则补充原文后重新检查。"
                    : "请人工核对个人职责、实现过程与可验证结果；仅保留确实做过的内容，不补写未经证实的指标。"));
        }
        return new CheckResult(resumeId, ((Number) resume.get("version")).intValue(), selected,
                "RULE", false, List.copyOf(findings),
                "规则仅检索已确认原文，不验证能力或工作成果；缺少证据不代表不会。不承诺 ATS 分数或录用概率。需要改写时使用现有定制流程逐项审核后另建版本。");
    }

    private boolean containsTerm(String quote, String term) {
        String normalized = term.toLowerCase(Locale.ROOT);
        if (normalized.matches("[a-z0-9 ]+")) {
            return java.util.regex.Pattern.compile("(?<![a-z0-9])" + java.util.regex.Pattern.quote(normalized)
                    + "(?![a-z0-9])").matcher(quote).find();
        }
        return quote.contains(normalized);
    }

    private Skill skill(String id, String role) {
        return new Skill(id, VERSION, role + "专项体检", role,
                List.of("resumeId", "resumeVersion（可选；省略时锁定当前版本）", "skillId"),
                "仅使用本人已确认版本中的 Claim.quote，必须逐字存在于该版本 source",
                Map.of("type", "object", "required", List.of("resumeId", "resumeVersion", "skill", "modelSource", "writesResume", "findings"),
                        "finding", Map.of("required", List.of("id", "title", "status", "evidence", "question", "suggestion"),
                                "status", List.of("EVIDENCE_FOUND", "NEEDS_CONFIRMATION"))),
                "原文精确引用；关键词只用于文字检索，不能证明熟练程度、项目成果或能力缺失",
                "本检查只读；改写复用 tailoring/tasks → review → create-version，由用户逐项审核",
                "resume-studio-rule-" + VERSION, false,
                List.of("禁止编造工作年限/学历/公司/技能/成果/数字", "禁止缺少证据即判定不会", "禁止自动采纳或覆盖原版本",
                        "禁止未经明确授权向模型发送简历", "禁止承诺 ATS 通过或录用"));
    }

    private List<Rule> rules(String skillId) {
        Rule java = new Rule("java", "Java 服务开发证据", List.of("Java", "Spring", "Spring Boot"),
                "你是否实际负责过 Java 服务？请说明职责、接口、技术选型与实现过程。");
        Rule backend = new Rule("backend", "数据与服务可靠性证据", List.of("MySQL", "Redis", "Kafka", "事务", "并发", "监控", "性能"),
                "是否有数据库、缓存、并发或故障治理的真实案例？请说明定位方法、取舍与可验证结果。");
        return switch (skillId) {
            case "java-ai" -> List.of(java, backend,
                    new Rule("ai", "AI 应用落地证据", List.of("RAG", "LLM", "大模型", "向量", "Spring AI", "LangChain4j"),
                            "是否做过真实 AI 应用？请说明检索/工具调用、评测、权限、隐私、成本和失败回退；没有经历请勿补写。"));
            case "senior-backend" -> List.of(java, backend,
                    new Rule("architecture", "架构与协作职责证据", List.of("架构", "设计", "治理", "重构", "评审"),
                            "是否负责过架构取舍、迁移或协作交付？请区分个人职责与团队成果，并提供真实约束。"));
            case "java-fullstack" -> List.of(java, backend,
                    new Rule("frontend", "前端与端到端交付证据", List.of("Vue", "React", "TypeScript", "前端", "全栈"),
                            "是否实际交付前端页面和接口联调？请说明状态处理、鉴权、测试与部署的个人职责。"));
            default -> throw new BusinessException(400, "专项方向无效");
        };
    }

    private void requireUser(Long userId) {
        if (userId == null) throw new BusinessException(401, "请先登录");
    }
}
