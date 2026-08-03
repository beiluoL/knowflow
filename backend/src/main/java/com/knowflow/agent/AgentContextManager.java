package com.knowflow.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.ai.ModelAdapter;
import com.knowflow.entity.AgentMessage;
import com.knowflow.entity.AgentSession;
import com.knowflow.mapper.AgentMessageMapper;
import com.knowflow.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * P2 上下文管理器：负责为一次模型调用组装「可控长度」的多轮上下文。
 * <p>
 * 策略（滚动窗口 + 摘要压缩）：
 * <ol>
 *   <li>取会话内最近 {@code MAX_RECENT_MESSAGES} 条消息作为候选（避免全量扫库）；</li>
 *   <li>已有的 summary 消息（message_type=summary）视为"历史记忆"，恒定置于最前；</li>
 *   <li>从最新往旧累加预估 token，超过 {@code contextWindow} 后截断；</li>
 *   <li>被截断掉的旧消息若数量达到阈值，异步/同步生成新的摘要落库，下次直接复用。</li>
 * </ol>
 * token 采用启发式预估（中文按字符、英文按 4 字符 ≈ 1 token），无需引入分词依赖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentContextManager {

    /** 单次组装时最多回溯的消息条数，防止超长会话拖垮查询。 */
    private static final int MAX_RECENT_MESSAGES = 200;

    /** 默认上下文窗口（预估 token），会话未配置时使用。 */
    private static final int DEFAULT_CONTEXT_WINDOW = 6000;

    /** 触发摘要压缩的最小被截断消息数：太少则不值得调用模型。 */
    private static final int SUMMARY_TRIGGER_COUNT = 6;

    /** 摘要提示词允许喂给模型的最大字符数，避免摘要本身超长。 */
    private static final int SUMMARY_INPUT_LIMIT = 8000;

    /** 生成的摘要文本最大保留长度。 */
    private static final int SUMMARY_MAX_LENGTH = 1200;

    private final AgentMessageMapper messageMapper;
    private final AiService aiService;

    /**
     * 组装本轮对话上下文。
     *
     * @param session  会话（提供 contextWindow 配置）
     * @param userId   当前用户，用于摘要时选择模型配置
     * @param configId 模型配置 ID，可空
     * @return 已按时间正序排列、总长度受控的消息列表（不含本轮 user 输入）
     */
    public List<ModelAdapter.ChatMessage> buildContext(AgentSession session, Long userId, Long configId) {
        Long sessionId = session.getId();
        int window = session.getContextWindow() != null && session.getContextWindow() > 0
                ? session.getContextWindow()
                : DEFAULT_CONTEXT_WINDOW;

        List<AgentMessage> recent = loadRecent(sessionId);
        if (recent.isEmpty()) {
            return new ArrayList<>();
        }

        // 摘要消息单独抽出，始终保留在上下文最前部
        List<AgentMessage> summaries = new ArrayList<>();
        List<AgentMessage> dialogues = new ArrayList<>();
        for (AgentMessage m : recent) {
            if ("summary".equals(m.getMessageType())) {
                summaries.add(m);
            } else {
                dialogues.add(m);
            }
        }

        int summaryTokens = 0;
        for (AgentMessage s : summaries) {
            summaryTokens += estimateTokens(s.getContent());
        }

        // 从最新往旧回溯，累加至窗口上限
        int budget = Math.max(window - summaryTokens, window / 4);
        int used = 0;
        int keepFrom = dialogues.size();
        for (int i = dialogues.size() - 1; i >= 0; i--) {
            int t = estimateTokens(dialogues.get(i).getContent());
            if (used + t > budget && keepFrom < dialogues.size()) {
                break;
            }
            used += t;
            keepFrom = i;
            if (used >= budget) {
                break;
            }
        }

        List<AgentMessage> dropped = dialogues.subList(0, keepFrom);
        List<AgentMessage> kept = dialogues.subList(keepFrom, dialogues.size());

        // 被丢弃的旧消息达到阈值 → 压缩成摘要落库，供后续复用
        if (dropped.size() >= SUMMARY_TRIGGER_COUNT) {
            AgentMessage newSummary = compressAndPersist(sessionId, userId, configId, dropped);
            if (newSummary != null) {
                summaries.add(newSummary);
            }
        }

        List<ModelAdapter.ChatMessage> context = new ArrayList<>();
        for (AgentMessage s : summaries) {
            context.add(new ModelAdapter.ChatMessage("system", "【历史对话摘要】\n" + s.getContent()));
        }
        for (AgentMessage m : kept) {
            context.add(toChatMessage(m));
        }
        return context;
    }

    /** 载入会话最近的消息（正序返回）。 */
    private List<AgentMessage> loadRecent(Long sessionId) {
        List<AgentMessage> desc = messageMapper.selectList(new LambdaQueryWrapper<AgentMessage>()
                .eq(AgentMessage::getSessionId, sessionId)
                .eq(AgentMessage::getDeleted, 0)
                .orderByDesc(AgentMessage::getId)
                .last("LIMIT " + MAX_RECENT_MESSAGES));
        Collections.reverse(desc);
        return desc;
    }

    /** 实体消息 → 适配层消息，tool 角色需带回 toolCallId 才能被模型正确关联。 */
    private ModelAdapter.ChatMessage toChatMessage(AgentMessage m) {
        String role = m.getRole();
        if ("tool".equals(role) || "tool_result".equals(role)) {
            ModelAdapter.ChatMessage tm = new ModelAdapter.ChatMessage("tool", m.getContent());
            tm.toolCallId = m.getToolCallId();
            return tm;
        }
        return new ModelAdapter.ChatMessage(role, m.getContent());
    }

    /**
     * 将旧消息压缩为一条摘要并落库。
     * <p>模型不可用时降级为「截断拼接」，保证主流程永不因摘要失败而中断。
     */
    private AgentMessage compressAndPersist(Long sessionId, Long userId, Long configId,
                                            List<AgentMessage> dropped) {
        String raw = renderForSummary(dropped);
        String summaryText;
        try {
            List<ModelAdapter.ChatMessage> prompt = new ArrayList<>();
            prompt.add(new ModelAdapter.ChatMessage("system",
                    "你是对话摘要助手。请将以下编程助手的历史对话压缩为要点摘要，"
                            + "保留：用户目标、已确定的技术方案、关键代码/文件路径、待办事项。"
                            + "不要编造内容，不超过 400 字，直接输出摘要正文。"));
            prompt.add(new ModelAdapter.ChatMessage("user", raw));
            ModelAdapter.ChatResult res = aiService.chatMulti(prompt, null, userId, configId);
            summaryText = res != null && res.content != null ? res.content.trim() : "";
        } catch (Exception e) {
            log.warn("会话 {} 历史摘要生成失败，降级为截断拼接: {}", sessionId, e.getMessage());
            summaryText = "";
        }
        if (summaryText.isEmpty()) {
            summaryText = truncate(raw, SUMMARY_MAX_LENGTH);
        } else if (summaryText.length() > SUMMARY_MAX_LENGTH) {
            summaryText = truncate(summaryText, SUMMARY_MAX_LENGTH);
        }

        AgentMessage summary = new AgentMessage();
        summary.setSessionId(sessionId);
        summary.setUserId(userId);
        summary.setRole("system");
        summary.setMessageType("summary");
        summary.setContent(summaryText);
        summary.setTokenCount(estimateTokens(summaryText));
        messageMapper.insert(summary);

        // 已被摘要覆盖的旧消息做逻辑删除，避免下次重复压缩
        for (AgentMessage m : dropped) {
            AgentMessage patch = new AgentMessage();
            patch.setId(m.getId());
            patch.setDeleted(1);
            messageMapper.updateById(patch);
        }
        log.info("会话 {} 已压缩 {} 条历史消息为摘要（{} 字）", sessionId, dropped.size(), summaryText.length());
        return summary;
    }

    /** 将待压缩消息渲染为「角色: 内容」文本。 */
    private String renderForSummary(List<AgentMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (AgentMessage m : messages) {
            sb.append(m.getRole()).append(": ")
                    .append(truncate(m.getContent(), 1000)).append('\n');
            if (sb.length() > SUMMARY_INPUT_LIMIT) {
                break;
            }
        }
        return truncate(sb.toString(), SUMMARY_INPUT_LIMIT);
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    /**
     * 启发式 token 预估：CJK 字符按 1 token，其余按 4 字符 ≈ 1 token。
     * 用于窗口裁剪判断，不追求与厂商分词器完全一致。
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjk = 0;
        int other = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                cjk++;
            } else {
                other++;
            }
        }
        return cjk + (other + 3) / 4;
    }
}
