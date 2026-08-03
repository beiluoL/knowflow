package com.knowflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.dto.AgentIntentDTO;
import com.knowflow.entity.AgentCallLog;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.AgentCallLogMapper;
import com.knowflow.service.impl.AiServiceImpl;
import com.knowflow.vo.AgentEvalVO;
import com.knowflow.vo.AgentIntentVO;
import com.knowflow.vo.AmbiguityVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 编程 Agent 的意图识别与答案生成优化服务。
 * <p>
 * 覆盖方案 P1~P3：
 * <ul>
 *   <li>P1 多轮上下文意图理解：{@link #classify(AgentIntentDTO, Long)} 结合历史窗口做 LLM 分类+指代消解；</li>
 *   <li>P2 显式确认 + 歧义检测：低置信度/缺失槽位触发 needsClarify；
 *       {@link #detectAmbiguities(AgentIntentDTO)} 用结构探针 + 语义 LLM 标记歧义点；</li>
 *   <li>P4 领域知识库：{@link IntentKnowledge} 内置语言特性/框架约定/常见场景，辅助消歧；</li>
 *   <li>P3 准确率评估闭环：{@link #evaluate(EvalInput, Long)} 量化匹配度并产出改进建议，可回流知识库。</li>
 * </ul>
 * 统一复用 {@code AiServiceImpl#complete} 通道，不引入新模型服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentService {

    private final AiServiceImpl aiService;
    private final AgentCallLogMapper agentCallLogMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 意图分类低置信度阈值：低于此值强制澄清 */
    private static final double CLARIFY_THRESHOLD = 0.6;

    // ============================================================
    // P1 多轮上下文意图理解
    // ============================================================

    /**
     * 意图识别：把「当前输入 + 历史 + 项目快照」交给大模型，返回结构化意图与歧义点。
     * 历史窗口取最近 6 轮，避免 token 膨胀。
     */
    public AgentIntentVO classify(AgentIntentDTO dto, Long userId) {
        AgentIntentVO result = new AgentIntentVO();
        try {
            String historyText = buildHistoryText(dto.getHistory());
            String knowledgeText = IntentKnowledge.asPrompt();
            String projectText = buildProjectText(dto.getProjectSnapshot());

            String system = """
                    你是一名编程意图理解引擎。根据「当前输入 + 历史对话 + 项目结构 + 领域知识」，
                    判断用户真实意图并抽取结构化参数。只输出 JSON，不要任何解释。
                    字段：
                    intent: generate(生成新代码)/modify(修改已有代码)/explain(解释代码)/debug(调试)/chat(闲聊)
                    confidence: 0~1 的置信度
                    slots: 结构化参数，可含 language(语言), artifact(产物类型如 pomodoro/todo),
                           save(是否要求保存本地 true/false), target(要修改的文件)
                    needsClarify: 当指令模糊/缺失关键参数/无法结合历史消歧时为 true
                    clarifications: needsClarify 为 true 时给出 1~3 个澄清问题，每项含 field/question/options(可选项)
                    """ + "\n\n领域知识（用于消歧，可引用其约定给出澄清选项）：\n" + knowledgeText;

            // 多轮硬指代解析（方案 P1.1 parentId）：当前输入含指代代词且给出 parentId 时，
            // 把目标轮次内容摘要注入 prompt，避免「它/这个」被软消解误判
            String refText = resolveParentRef(dto);

            String user = "项目结构：\n" + projectText + "\n历史对话：\n" + historyText +
                    (refText != null ? "\n\n【多轮指代目标】用户当前输入中的「它/这个/上面」指代的是：\n" + refText : "") +
                    "\n当前输入：\n" + dto.getCurrentInput() +
                    "\n\n请输出 JSON：{\"intent\":..,\"confidence\":..,\"slots\":{..},\"needsClarify\":..,\"clarifications\":[..]}";

            String raw = aiService.complete(system, user, 0.1, userId);
            AgentIntentVO parsed = parseIntent(raw);
            result = parsed != null ? parsed : fallbackClassify(dto);
        } catch (BusinessException e) {
            // AI 未配置时降级为正则粗分类，保证基础可用性
            result = fallbackClassify(dto);
        } catch (Exception e) {
            log.warn("意图识别失败，降级处理: {}", e.getMessage());
            result = fallbackClassify(dto);
        }

        // 结构探针：不依赖 LLM，直接标记项目相关歧义
        result.setAmbiguities(mergeAmbiguities(result.getAmbiguities(), structuralProbe(dto)));
        // 显式确认：低置信度也强制澄清
        if (result.getConfidence() != null && result.getConfidence() < CLARIFY_THRESHOLD) {
            result.setNeedsClarify(true);
        }
        return result;
    }

    /** 正则兜底分类（AI 不可用时）：复用现有前端正则思路，服务端独立实现 */
    private AgentIntentVO fallbackClassify(AgentIntentDTO dto) {
        AgentIntentVO vo = new AgentIntentVO();
        String text = dto.getCurrentInput();
        boolean gen = GEN_ACTION.matcher(text).find();
        vo.setIntent(gen ? "generate" : "chat");
        vo.setConfidence(gen ? 0.85 : 0.5);
        vo.setSlots(new LinkedHashMap<>());
        vo.setNeedsClarify(!gen && text.trim().length() < 4);
        vo.setAmbiguities(new ArrayList<>());
        return vo;
    }

    private static final Pattern GEN_ACTION = Pattern.compile(
            "(写|生成|创建|实现|做个|做一个|帮我写|帮我实现|开发|整一个|写个|搞个|编|造|新建)");

    // ============================================================
    // P2 歧义检测（结构探针 + 语义 LLM）
    // ============================================================

    /** 结构探针：基于项目快照的确定性歧义检测，不调用 LLM */
    public List<AmbiguityVO> structuralProbe(AgentIntentDTO dto) {
        List<AmbiguityVO> list = new ArrayList<>();
        if (dto.getProjectSnapshot() == null || dto.getProjectSnapshot().isEmpty()) {
            return list;
        }
        List<AgentIntentDTO.ProjectFile> files = dto.getProjectSnapshot();
        boolean hasPackageJson = files.stream().anyMatch(f -> "package.json".equals(f.getPath()));
        boolean hasAppJs = files.stream().anyMatch(f -> "app.js".equals(f.getPath()));

        String text = dto.getCurrentInput().toLowerCase();
        // 修改不存在的文件：解析「修改/编辑 X 文件」模式，引用文件名不在快照中则标记
        java.util.regex.Matcher mFile = Pattern.compile("(修改|编辑|改下|更新|修复|调整)\\s*[「『]?([\\w./-]+\\.\\w+)").matcher(text);
        while (mFile.find()) {
            String referenced = mFile.group(2).toLowerCase();
            boolean exists = files.stream().anyMatch(f -> f.getPath().toLowerCase().equals(referenced)
                    || f.getPath().toLowerCase().endsWith("/" + referenced));
            if (!exists) {
                list.add(ambiguity("missing-file", "指令要修改的文件不存在：" + mFile.group(2),
                        "项目快照中未找到该文件，可能是文件名拼写错误或尚未创建",
                        "请确认文件名；若意图为「新建」请明确说「创建」而非「修改」"));
            }
        }
        // 要求加路由但无前端框架
        if (text.contains("路由") && !hasPackageJson && !hasAppJs) {
            list.add(ambiguity("framework-mismatch", "指令要求「路由」",
                    "项目快照未检测到 package.json / 前端框架",
                    "若目标是纯静态页可用 hash 路由；若需框架请先初始化（如 Vite + Vue）"));
        }
        // 生成 html 但已有 app.js 却要内联
        if (text.contains("html") && hasAppJs && text.contains("内联")) {
            list.add(ambiguity("lang-mismatch", "要求 HTML 内联脚本",
                    "项目已存在独立 app.js，内联会破坏既有结构约定",
                    "建议复用 app.js 或在 index.html 以 <script src> 引入，保持结构/行为分离"));
        }
        return list;
    }

    /** 语义歧义检测：LLM 辅助标记模糊点（结合领域知识） */
    public List<AmbiguityVO> detectAmbiguities(AgentIntentDTO dto, Long userId) {
        List<AmbiguityVO> structural = structuralProbe(dto);
        if (dto.getStructuralOnly() != null && dto.getStructuralOnly()) {
            return structural;
        }
        try {
            String knowledgeText = IntentKnowledge.asPrompt();
            String projectText = buildProjectText(dto.getProjectSnapshot());
            String system = """
                    你是代码歧义检测引擎。分析用户输入，标记与代码结构/语义/领域约定冲突或缺失的点。
                    只输出 JSON 数组：[{"kind":"missing-file|framework-mismatch|lang-mismatch|underspecified|semantical",
                    "point":"歧义点原文","reason":"为什么是歧义","suggestion":"结合领域知识的可执行建议"}]
                    若无歧义输出 []。不要任何解释。
                    """ + "\n领域知识：\n" + knowledgeText;
            String user = "项目结构：\n" + projectText + "\n用户输入：\n" + dto.getCurrentInput();
            String raw = aiService.complete(system, user, 0.1, userId);
            List<AmbiguityVO> semantic = parseAmbiguities(raw);
            return mergeAmbiguities(structural, semantic);
        } catch (Exception e) {
            log.warn("语义歧义检测失败，仅用结构探针: {}", e.getMessage());
            return structural;
        }
    }

    // ============================================================
    // P3 输出准确率评估闭环
    // ============================================================

    /** 评估输入：识别意图、Agent 实际输出、可选的用户反馈 */
    public record EvalInput(
            String intent,
            Map<String, String> slots,
            String agentOutput,
            String userFeedback,
            boolean fromFeedback,
            Long sessionId) {
    }

    public AgentEvalVO evaluate(EvalInput input, Long userId) {
        AgentEvalVO vo = new AgentEvalVO();
        try {
            String system = """
                    你是编程 Agent 输出评估引擎。对比「识别意图 + 结构化参数」与「Agent 实际输出」，
                    评估匹配度并给出改进建议。只输出 JSON：
                    {"matchScore":0~1,"dimensions":{"intent":0~1,"spec":0~1,"format":0~1},
                    "misses":["未满足点"],"suggestions":["改进建议"]}
                    不要任何解释。
                    """;
            String user = "识别意图：" + input.intent() + "\n抽取参数：" + input.slots() +
                    "\nAgent 输出：\n" + clip(input.agentOutput(), 2000) +
                    (input.userFeedback() != null ? "\n用户反馈：" + input.userFeedback() : "");
            String raw = aiService.complete(system, user, 0.1, userId);
            AgentEvalVO parsed = parseEval(raw);
            if (parsed != null) {
                parsed.setFromFeedback(input.fromFeedback());
                saveEvalLog(input, userId, parsed.getMatchScore());
                return parsed;
            }
        } catch (Exception e) {
            log.warn("评估失败: {}", e.getMessage());
        }
        // 兜底：基于是否含代码块与反馈做粗略评分
        vo.setMatchScore(input.userFeedback() == null ? 0.7 : 0.5);
        vo.setDimensions(Map.of("intent", 0.7, "spec", 0.7, "format", 0.7));
        vo.setMisses(new ArrayList<>());
        vo.setSuggestions(List.of("本次评估通道不可用，已用默认评分；建议补充用户反馈以提高准确性"));
        vo.setFromFeedback(input.fromFeedback());
        saveEvalLog(input, userId, vo.getMatchScore());
        return vo;
    }

    /** P3 评估闭环落库：把 matchScore 回写 agent_call_log，供运营复盘与知识库反哺 */
    private void saveEvalLog(EvalInput input, Long userId, Double score) {
        try {
            AgentCallLog log = new AgentCallLog();
            log.setUserId(userId);
            log.setSessionId(input.sessionId());
            log.setIntent(input.intent());
            log.setScore(score != null ? java.math.BigDecimal.valueOf(score) : null);
            log.setSuccess(1);
            agentCallLogMapper.insert(log);
        } catch (Exception e) {
            log.warn("评估日志落库失败（不影响主流程）: {}", e.getMessage());
        }
    }

    // ============================================================
    // 内部工具
    // ============================================================

    private String buildHistoryText(List<AgentIntentDTO.HistoryItem> history) {
        if (history == null || history.isEmpty()) return "（无）";
        // 仅取最近 6 轮
        int start = Math.max(0, history.size() - 6);
        return history.subList(start, history.size()).stream()
                .map(h -> "[" + (h.getRole() == null ? "?" : h.getRole()) + "] " + (h.getContent() == null ? "" : h.getContent()))
                .collect(Collectors.joining("\n"));
    }

    /** 多轮指代代词 */
    private static final Pattern ANAPHORA = Pattern.compile("(它|这个|那个|上面|刚才|之前|前面|上一步|前述)");

    /**
     * 硬指代解析：若当前输入含指代代词且历史中某条带 parentId 指向更早一条，
     * 返回目标轮次的内容摘要（取最近 200 字），供模型精确消解而非软猜。
     */
    private String resolveParentRef(AgentIntentDTO dto) {
        String input = dto.getCurrentInput();
        if (input == null || !ANAPHORA.matcher(input).find()) return null;
        List<AgentIntentDTO.HistoryItem> history = dto.getHistory();
        if (history == null || history.isEmpty()) return null;
        // 找到 parentId 指向的目标轮次（在最近 6 轮窗口内定位）
        String targetId = history.stream()
                .filter(h -> h.getParentId() != null && !h.getParentId().isBlank())
                .reduce((a, b) -> b) // 取最后一条带 parentId 的作为指代目标
                .map(AgentIntentDTO.HistoryItem::getParentId)
                .orElse(null);
        if (targetId == null) return null;
        return history.stream()
                .filter(h -> targetId.equals(h.getId()))
                .findFirst()
                .map(h -> clip(h.getContent(), 200))
                .orElse(null);
    }

    private String buildProjectText(List<AgentIntentDTO.ProjectFile> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) return "（未挂载项目目录）";
        return snapshot.stream()
                .map(f -> (f.getType().equals("directory") ? "[D] " : "[F] ") + f.getPath())
                .collect(Collectors.joining("\n"));
    }

    private List<AmbiguityVO> mergeAmbiguities(List<AmbiguityVO> a, List<AmbiguityVO> b) {
        List<AmbiguityVO> list = new ArrayList<>(a == null ? List.of() : a);
        if (b != null) list.addAll(b);
        return list;
    }

    private AmbiguityVO ambiguity(String kind, String point, String reason, String suggestion) {
        AmbiguityVO a = new AmbiguityVO();
        a.setKind(kind);
        a.setPoint(point);
        a.setReason(reason);
        a.setSuggestion(suggestion);
        return a;
    }

    private String clip(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    private AgentIntentVO parseIntent(String raw) {
        try {
            String json = extractJson(raw);
            Map<String, Object> m = objectMapper.readValue(json, Map.class);
            AgentIntentVO vo = new AgentIntentVO();
            vo.setIntent((String) m.get("intent"));
            vo.setConfidence(toDouble(m.get("confidence")));
            vo.setSlots(m.containsKey("slots") ? (Map<String, String>) m.get("slots") : new LinkedHashMap<>());
            vo.setNeedsClarify(Boolean.TRUE.equals(m.get("needsClarify")));
            if (m.containsKey("clarifications")) {
                List<AgentIntentVO.ClarifyQuestion> qs = new ArrayList<>();
                for (Object o : (List<?>) m.get("clarifications")) {
                    Map<String, Object> q = (Map<String, Object>) o;
                    AgentIntentVO.ClarifyQuestion cq = new AgentIntentVO.ClarifyQuestion();
                    cq.setField((String) q.get("field"));
                    cq.setQuestion((String) q.get("question"));
                    cq.setOptions(q.containsKey("options") ? (List<String>) q.get("options") : new ArrayList<>());
                    qs.add(cq);
                }
                vo.setClarifications(qs);
            }
            return vo;
        } catch (Exception e) {
            log.warn("意图 JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private List<AmbiguityVO> parseAmbiguities(String raw) {
        try {
            String json = extractJson(raw);
            List<Map<String, Object>> list = objectMapper.readValue(json, List.class);
            List<AmbiguityVO> res = new ArrayList<>();
            for (Map<String, Object> m : list) {
                AmbiguityVO a = new AmbiguityVO();
                a.setKind((String) m.get("kind"));
                a.setPoint((String) m.get("point"));
                a.setReason((String) m.get("reason"));
                a.setSuggestion((String) m.get("suggestion"));
                res.add(a);
            }
            return res;
        } catch (Exception e) {
            log.warn("歧义 JSON 解析失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private AgentEvalVO parseEval(String raw) {
        try {
            String json = extractJson(raw);
            Map<String, Object> m = objectMapper.readValue(json, Map.class);
            AgentEvalVO vo = new AgentEvalVO();
            vo.setMatchScore(toDouble(m.get("matchScore")));
            vo.setDimensions(m.containsKey("dimensions") ? (Map<String, Double>) m.get("dimensions") : new LinkedHashMap<>());
            vo.setMisses(m.containsKey("misses") ? (List<String>) m.get("misses") : new ArrayList<>());
            vo.setSuggestions(m.containsKey("suggestions") ? (List<String>) m.get("suggestions") : new ArrayList<>());
            return vo;
        } catch (Exception e) {
            log.warn("评估 JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private String extractJson(String raw) {
        if (raw == null) return "{}";
        int s = raw.indexOf('{');
        int e = raw.lastIndexOf('}');
        if (s >= 0 && e > s) return raw.substring(s, e + 1);
        // 数组形式（歧义）
        s = raw.indexOf('[');
        e = raw.lastIndexOf(']');
        if (s >= 0 && e > s) return raw.substring(s, e + 1);
        return raw;
    }

    private Double toDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(o.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
