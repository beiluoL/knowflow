package com.knowflow.exam.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 考试 AI 通道：五维主观题评卷 + AI 智能出卷。
 *
 * <p>安全原则：
 * <ul>
 *   <li>模型未配置/调用失败/JSON 非法 → 返回失败结果，由上层把题目保留为 PENDING，绝不默认 0 分；</li>
 *   <li>总分由服务端按固定权重重算，模型给的 total 仅作审计字段；</li>
 *   <li>AI 出卷逐题 Schema 校验、答案合法性校验并与题库语义去重；</li>
 *   <li>AI 卷不写共享题库，仅作为个人试卷快照保存（PENDING_REVIEW）。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExamAiService {

    public static final String PROMPT_RUBRIC_VERSION = "exam-rubric-v1";
    public static final String PROMPT_PAPER_VERSION = "exam-paper-v1";

    /** 五维默认权重（合计 100）。 */
    public static final Map<String, Integer> RUBRIC_WEIGHTS = Map.of(
            "technicalAccuracy", 35,
            "principle", 25,
            "completeness", 20,
            "engineering", 10,
            "expression", 10);

    private final AiService aiService;
    private final ObjectMapper objectMapper;

    /** AI 评卷输入题（仅必要字段，避免提示词膨胀）。 */
    public record GradeInput(long paperQuestionId, String title, String content,
                             String questionType, String referenceAnswer, String userAnswer) {
    }

    /** 单题 AI 评分结果（total100 为服务端重算值）。 */
    public record AiGrade(long paperQuestionId, int total100, int confidence,
                          Map<String, Integer> dimensions, List<String> hitPoints,
                          List<String> missingPoints, List<String> errors,
                          List<String> suggestions, List<String> evidence,
                          int modelTotal, String rawError) {
        public boolean valid() {
            return rawError == null;
        }
    }

    public boolean isConfigured() {
        try {
            return aiService.isConfigured();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 批量 AI 评卷。失败/非法的题目在结果中以 rawError 标记，上层据此保持 PENDING。
     */
    public List<AiGrade> gradeSubjective(List<GradeInput> inputs, Long userId, String model) {
        List<AiGrade> results = new ArrayList<>();
        if (inputs == null || inputs.isEmpty()) {
            return results;
        }
        if (!isConfigured()) {
            return inputs.stream().map(i -> new AiGrade(i.paperQuestionId(), 0, 0, Map.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of(), -1,
                    "AI_NOT_CONFIGURED")).toList();
        }
        String systemPrompt = """
                你是严格的 Java / AI 应用开发方向技术面试评卷专家。只依据题目与参考答案评分，不得编造考生没写的内容。
                必须只输出一个 JSON 对象，不要输出 markdown 代码块或任何解释文字，结构为：
                {"grades":[{"paperQuestionId":数字,"total":0到100整数,"confidence":0到100整数,
                "dimensions":{"technicalAccuracy":0-100,"principle":0-100,"completeness":0-100,"engineering":0-100,"expression":0-100},
                "hitPoints":["命中的评分点"],"missingPoints":["缺失的评分点"],"errors":["明显技术错误，没有则空数组"],
                "suggestions":["改进建议"],"evidence":["考生原话中支持评分的关键片段"]}]}
                维度含义：technicalAccuracy 技术准确性；principle 原理理解；completeness 完整性；engineering 工程实践；expression 表达结构。
                评分要可区分：完全空白或方向性错误给 0-30；部分正确 31-70；全面准确 71-100。
                """;
        String userPrompt;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("rubricWeights", RUBRIC_WEIGHTS);
            List<Map<String, Object>> qs = new ArrayList<>();
            for (GradeInput in : inputs) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("paperQuestionId", in.paperQuestionId());
                m.put("title", in.title());
                m.put("content", in.content());
                m.put("questionType", in.questionType());
                m.put("referenceAnswer", in.referenceAnswer());
                m.put("userAnswer", in.userAnswer());
                qs.add(m);
            }
            payload.put("questions", qs);
            userPrompt = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return inputs.stream().map(i -> new AiGrade(i.paperQuestionId(), 0, 0, Map.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of(), -1,
                    "PROMPT_BUILD_FAILED: " + e.getMessage())).toList();
        }

        String raw;
        try {
            raw = aiService.complete(systemPrompt, userPrompt, model, userId);
        } catch (com.knowflow.ai.capability.AiCapabilityException e) {
            log.warn("AI 评卷调用失败 userId={} kind={}: {}", userId, e.getKind(), e.getMessage());
            String code = "AI_" + e.getKind().name();
            return inputs.stream().map(i -> new AiGrade(i.paperQuestionId(), 0, 0, Map.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of(), -1, code)).toList();
        } catch (Exception e) {
            log.warn("AI 评卷调用失败 userId={}: {}", userId, e.getMessage());
            return inputs.stream().map(i -> new AiGrade(i.paperQuestionId(), 0, 0, Map.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of(), -1,
                    "AI_CALL_FAILED: " + e.getMessage())).toList();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(extractJson(raw));
        } catch (Exception e) {
            log.warn("AI 评卷返回非法 JSON userId={}: {}", userId, e.getMessage());
            return inputs.stream().map(i -> new AiGrade(i.paperQuestionId(), 0, 0, Map.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of(), -1,
                    "INVALID_JSON")).toList();
        }

        Map<Long, JsonNode> byId = new LinkedHashMap<>();
        JsonNode grades = root.path("grades");
        if (grades.isArray()) {
            for (JsonNode g : grades) {
                long id = g.path("paperQuestionId").asLong(-1);
                if (id > 0) {
                    byId.put(id, g);
                }
            }
        }
        for (GradeInput in : inputs) {
            JsonNode g = byId.get(in.paperQuestionId());
            if (g == null) {
                results.add(new AiGrade(in.paperQuestionId(), 0, 0, Map.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), -1, "MISSING_IN_RESPONSE"));
                continue;
            }
            try {
                Map<String, Integer> dims = new LinkedHashMap<>();
                for (String key : RUBRIC_WEIGHTS.keySet()) {
                    int v = clamp(g.path("dimensions").path(key).asInt(-1), 0, 100);
                    if (v < 0) {
                        v = 0;
                    }
                    dims.put(key, v);
                }
                int serverTotal = 0;
                for (Map.Entry<String, Integer> e : dims.entrySet()) {
                    serverTotal += e.getValue() * RUBRIC_WEIGHTS.get(e.getKey());
                }
                serverTotal = Math.round(serverTotal / 100f);
                int modelTotal = clamp(g.path("total").asInt(-1), 0, 100);
                int confidence = clamp(g.path("confidence").asInt(50), 0, 100);
                results.add(new AiGrade(
                        in.paperQuestionId(), serverTotal, confidence, dims,
                        textList(g.path("hitPoints")), textList(g.path("missingPoints")),
                        textList(g.path("errors")), textList(g.path("suggestions")),
                        textList(g.path("evidence")), modelTotal, null));
            } catch (Exception e) {
                results.add(new AiGrade(in.paperQuestionId(), 0, 0, Map.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), -1,
                        "GRADE_PARSE_FAILED: " + e.getMessage()));
            }
        }
        return results;
    }

    /** AI 出卷原始候选题。 */
    public record AiQuestion(String title, String content, String questionType, List<String> options,
                             String answer, String explanation, Integer difficulty, String topic,
                             String invalidReason) {
        public boolean valid() {
            return invalidReason == null;
        }
    }

    /**
     * AI 智能出卷：返回逐题校验后的候选题；调用失败或整体 JSON 非法时返回空列表并设置 errorMessage。
     *
     * @param errorOut 长度 1 的数组，errorOut[0] 承载错误信息（null 表示成功）
     */
    public List<AiQuestion> generateQuestions(String topic, int count, Map<String, Integer> typeDistribution,
                                              List<Integer> difficulties, Long userId, String model,
                                              String[] errorOut) {
        if (!isConfigured()) {
            errorOut[0] = "AI_NOT_CONFIGURED";
            return List.of();
        }
        String system = """
                你是 Java 高级工程师与 AI 应用开发方向的命题专家。根据要求出高质量中文面试题，只输出 JSON，不要 markdown 代码块：
                {"questions":[{"title":"短标题","content":"完整题干","questionType":"SINGLE_CHOICE|MULTIPLE_CHOICE|TRUE_FALSE|FILL_BLANK|SHORT_ANSWER",
                "options":["选项A","选项B","选项C","选项D"],"answer":"正确答案","explanation":"解析","difficulty":1到3整数,"topic":"知识点英文标识"}]}
                规则：SINGLE_CHOICE 必须 4 个选项，answer 是正确选项索引字符串 "0".."3"；
                MULTIPLE_CHOICE 必须 4 个选项，answer 形如 "0,2" 且至少两个；
                TRUE_FALSE 不给 options，answer 只能是 "true" 或 "false"；
                FILL_BLANK 不给 options，answer 为精确答案文本；
                SHORT_ANSWER 不给 options，answer 为参考答案要点。
                题目必须技术准确、表述无歧义、解析解释为什么对为什么错，且题与题之间不得重复或互为变体。
                """;
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("topic", topic);
        req.put("count", count);
        if (typeDistribution != null && !typeDistribution.isEmpty()) {
            req.put("typeDistribution", typeDistribution);
        }
        if (difficulties != null && !difficulties.isEmpty()) {
            req.put("difficulties", difficulties);
        }
        String userPrompt;
        try {
            userPrompt = objectMapper.writeValueAsString(req);
        } catch (Exception e) {
            errorOut[0] = "PROMPT_BUILD_FAILED: " + e.getMessage();
            return List.of();
        }
        String raw;
        try {
            raw = aiService.complete(system, userPrompt, model, userId);
        } catch (com.knowflow.ai.capability.AiCapabilityException e) {
            // 结构化失败分类（未配置/限流/超时/熔断等），上层可据此决定是否降级到规则组卷
            errorOut[0] = "AI_" + e.getKind().name();
            return List.of();
        } catch (Exception e) {
            errorOut[0] = "AI_CALL_FAILED: " + e.getMessage();
            return List.of();
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(extractJson(raw));
        } catch (Exception e) {
            errorOut[0] = "INVALID_JSON";
            return List.of();
        }
        List<AiQuestion> out = new ArrayList<>();
        JsonNode arr = root.path("questions");
        if (!arr.isArray()) {
            errorOut[0] = "INVALID_SCHEMA: questions missing";
            return List.of();
        }
        Set<String> validTypes = Set.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "FILL_BLANK", "SHORT_ANSWER");
        for (JsonNode q : arr) {
            String title = q.path("title").asText("").trim();
            String content = q.path("content").asText("").trim();
            String type = q.path("questionType").asText("").trim();
            String answer = q.path("answer").asText("").trim();
            String explanation = q.path("explanation").asText("").trim();
            int difficulty = q.path("difficulty").asInt(2);
            String tpc = q.path("topic").asText(topic == null ? "" : topic).trim();
            List<String> options = new ArrayList<>();
            JsonNode op = q.path("options");
            if (op.isArray()) {
                for (JsonNode o : op) {
                    options.add(o.asText());
                }
            }
            String invalid = validateQuestion(title, content, type, answer, explanation, difficulty, options, validTypes);
            out.add(new AiQuestion(title, content, type, options, answer, explanation,
                    Math.max(1, Math.min(3, difficulty)), tpc, invalid));
        }
        errorOut[0] = null;
        return out;
    }

    private String validateQuestion(String title, String content, String type, String answer,
                                    String explanation, int difficulty, List<String> options,
                                    Set<String> validTypes) {
        if (title.isEmpty() || content.isEmpty()) {
            return "标题或题干为空";
        }
        if (!validTypes.contains(type)) {
            return "非法题型: " + type;
        }
        if (answer.isEmpty()) {
            return "缺少答案";
        }
        if (explanation.isEmpty()) {
            return "缺少解析";
        }
        if (difficulty < 1 || difficulty > 3) {
            return "难度越界";
        }
        if (("SINGLE_CHOICE".equals(type) || "MULTIPLE_CHOICE".equals(type))) {
            if (options.size() != 4) {
                return "选择题必须 4 个选项";
            }
            String[] parts = answer.split(",");
            for (String p : parts) {
                try {
                    int idx = Integer.parseInt(p.trim());
                    if (idx < 0 || idx >= options.size()) {
                        return "答案索引越界: " + p;
                    }
                } catch (NumberFormatException e) {
                    return "答案不是索引: " + p;
                }
            }
            if ("MULTIPLE_CHOICE".equals(type) && parts.length < 2) {
                return "多选题至少两个正确项";
            }
            if ("SINGLE_CHOICE".equals(type) && parts.length != 1) {
                return "单选题只能一个正确项";
            }
        } else if (options != null && !options.isEmpty()) {
            return "非选择题不应带选项";
        }
        if ("TRUE_FALSE".equals(type) && !"true".equalsIgnoreCase(answer) && !"false".equalsIgnoreCase(answer)) {
            return "判断题答案必须为 true/false";
        }
        return null;
    }

    private List<String> textList(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode n : node) {
                String s = n.asText("").trim();
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** 容错提取 JSON：剥离模型偶尔输出的 ```json 围栏与前后文本。 */
    static String extractJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstBrace = s.indexOf('{');
            int lastBrace = s.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                return s.substring(firstBrace, lastBrace + 1);
            }
        }
        int firstBrace = s.indexOf('{');
        int lastBrace = s.lastIndexOf('}');
        if (firstBrace > 0 && lastBrace > firstBrace) {
            return s.substring(firstBrace, lastBrace + 1);
        }
        return s;
    }
}
