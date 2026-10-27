package com.knowflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.trace.RagTrace;
import com.knowflow.ai.trace.RagTraceStep;
import com.knowflow.dto.ChatSendDTO;
import com.knowflow.entity.ChatConversation;
import com.knowflow.entity.ChatMessage;
import com.knowflow.entity.DocDocument;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.ChatConversationMapper;
import com.knowflow.mapper.ChatMessageMapper;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.ai.CoachPrompts;
import com.knowflow.service.AiService;
import com.knowflow.service.ChatService;
import com.knowflow.common.LearningEventType;
import com.knowflow.service.DocChunkService;
import com.knowflow.service.LearningEventService;
import com.knowflow.vo.ConversationVO;
import com.knowflow.vo.MessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 聊天业务服务实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl extends ServiceImpl<ChatConversationMapper, ChatConversation> implements ChatService {

    private final ChatMessageMapper messageMapper;
    private final AiService aiService;
    private final com.knowflow.ai.capability.AiCapabilityProperties capabilityProperties;
    private final DocDocumentMapper docMapper;
    private final DocChunkService docChunkService;
    private final LearningEventService learningEventService;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.knowflow.learning.tutor.TutorContextService tutorContext;


    /** 事件序列化独立于业务对象，避免为加字段改动既有测试构造签名。 */
    private static final ObjectMapper EVENT_MAPPER = new ObjectMapper();

    /**
     * SSE 生成线程池：模型读流是阻塞式调用，必须离开请求线程，
     * 否则 emitter 的 start/status/reasoning/answer 事件会等到方法返回后一次性刷出。
     * <p>复用 DelegatingSecurityContextExecutorService（与知识库导入一致），
     * 保证异步线程内仍能读到发起请求时的登录用户上下文。
     */
    private final java.util.concurrent.ExecutorService sseExecutor =
            new org.springframework.security.concurrent.DelegatingSecurityContextExecutorService(
                    java.util.concurrent.Executors.newCachedThreadPool(r -> {
                        Thread t = new Thread(r, "chat-sse");
                        t.setDaemon(true);
                        return t;
                    }));

    /** 已提交但未结束的 SSE 生成任务数，供测试断言与停机前确认收尾。 */
    private final java.util.concurrent.atomic.AtomicInteger sseInFlight = new java.util.concurrent.atomic.AtomicInteger();

    /** 提交生成任务并计数，保证 {@link #awaitSseTasks(long)} 能等到任务真正结束。 */
    private void submitSse(Runnable task) {
        sseInFlight.incrementAndGet();
        sseExecutor.execute(() -> {
            try {
                task.run();
            } finally {
                sseInFlight.decrementAndGet();
            }
        });
    }

    /** 等待已提交的 SSE 生成任务结束；超时即返回，不阻塞业务请求。 */
    public void awaitSseTasks(long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (sseInFlight.get() > 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** RAG 检索结果与真实执行轨迹；trace 为 null 表示本次未检索（如教练模式或用户关闭知识库）。 */
    private record RagContext(List<DocDocument> docs, RagTrace trace) {
        static RagContext empty() {
            return new RagContext(Collections.emptyList(), null);
        }
    }

    @Override
    public List<ConversationVO> getConversationList(Long userId) {
        return getConversationList(userId, false);
    }

    @Override
    public List<ConversationVO> getConversationList(Long userId, boolean archived) {
        List<ChatConversation> conversations = this.list(new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getUserId, userId)
                .eq(ChatConversation::getArchived, archived)
                .orderByDesc(ChatConversation::getPinned)
                .orderByDesc(ChatConversation::getUpdateTime).orderByDesc(ChatConversation::getId));
        return conversations.stream()
                .filter(c -> tutorContext==null || tutorContext.visible(c.getId(),userId))
                .map(c -> BeanUtil.copyProperties(c, ConversationVO.class))
                .collect(Collectors.toList());
    }

    @Override
    public List<MessageVO> getMessageList(Long conversationId, Long userId) {
        ChatConversation conversation = this.getById(conversationId);
        if (conversation == null || !conversation.getUserId().equals(userId)) {
            throw new BusinessException(404, "对话不存在");
        }
        if(tutorContext!=null && tutorContext.bound(conversationId))tutorContext.get(conversationId,userId);
        List<ChatMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByAsc(ChatMessage::getCreateTime).orderByAsc(ChatMessage::getId));
        return messages.stream()
                .map(m -> BeanUtil.copyProperties(m, MessageVO.class))
                .collect(Collectors.toList());
    }

    /** 在创建会话、保存消息及学习活动前拒绝关闭态；历史与规则计划不经过此门禁。 */
    private void requireChatAiEnabled(Long userId) {
        if (userId == null) throw new BusinessException(401, "未登录或登录已失效");
        if (!capabilityProperties.isEnabled()) {
            throw new com.knowflow.ai.capability.AiCapabilityException(
                    com.knowflow.ai.capability.AiFailureKind.SYSTEM_DISABLED, null);
        }
    }

    /** 发送消息主流程：落库用户消息 → 检索相关文档 → 调用 AI → 落库助手消息并更新会话。 */
    @Override
    @Transactional
    public MessageVO sendMessage(ChatSendDTO dto, Long userId) {
        requireChatAiEnabled(userId);
        ChatConversation conversation;
        if (dto.getConversationId() == null) {
            conversation = createConversationEntity(userId, dto.getContent());
        } else {
            conversation = this.getById(dto.getConversationId());
            if (conversation == null || !conversation.getUserId().equals(userId)) {
                throw new BusinessException(404, "对话不存在");
            }
        }
        // 先校验引用上下文，避免引用无效时仍落库一条孤立的用户消息
        if(tutorContext!=null)tutorContext.forbidBranch(conversation.getId());
        String quoted = buildQuoteContext(dto.getQuoteMessageId(), conversation.getId(), userId);
        ChatMessage userMessage = new ChatMessage();
        userMessage.setConversationId(conversation.getId());
        userMessage.setUserId(userId);
        userMessage.setRole("user");
        userMessage.setContent(dto.getContent());
        userMessage.setTokenCount(dto.getContent().length());
        messageMapper.insert(userMessage);
        if (StrUtil.isNotBlank(dto.getContent())) baseMapper.initializeTitle(conversation.getId(), userId, dto.getContent().trim().substring(0, Math.min(50, dto.getContent().trim().length())));
        learningEventService.record(userId, LearningEventType.AI_CHAT, "CHAT", conversation.getId(),
                Map.of("hasImages", dto.getImages() != null && !dto.getImages().isEmpty()));
        List<DocDocument> contextDocs = Boolean.FALSE.equals(dto.getUseKnowledgeBase())
                ? Collections.emptyList() : searchRelatedDocs(dto.getContent(), userId);
        // A-RAG + A-CHAT：有图片时使用视觉模型，否则使用文本模型
        List<String> images = dto.getImages();
        String replyContent;
        if (images != null && !images.isEmpty()) {
            replyContent = aiService.chatWithImages(
                    quoted + (dto.getContent() != null ? dto.getContent() : ""),
                    images, contextDocs, dto.getModel(), userId);
        } else {
            replyContent = aiService.chat(quoted.isBlank() ? dto.getContent() : quoted + dto.getContent(), contextDocs, dto.getModel(), userId);
        }
        if (StrUtil.isBlank(replyContent)) {
            replyContent = "（AI 暂时未返回内容，请稍后重试或检查 AI 配置。）";
        }
        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setConversationId(conversation.getId());
        assistantMessage.setUserId(userId);
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(replyContent);
        assistantMessage.setDocReferences(buildDocReferences(contextDocs));
        assistantMessage.setTokenCount(replyContent.length());
        messageMapper.insert(assistantMessage);
        conversation.setMessageCount(conversation.getMessageCount() + 2);
        // 截断 last_message 防止超出数据库列长度（列宽 4000，这里留余量）
        String lastMsg = replyContent.length() > 3900 ? replyContent.substring(0, 3900) : replyContent;
        conversation.setLastMessage(lastMsg);
        baseMapper.completeTurn(conversation.getId(), userId, lastMsg);
        return BeanUtil.copyProperties(assistantMessage, MessageVO.class);
    }

    @Override
    public ConversationVO createConversation(Long userId, String title) {
        ChatConversation conversation = createConversationEntity(userId, title);
        return BeanUtil.copyProperties(conversation, ConversationVO.class);
    }

    @Override
    public ConversationVO renameConversation(Long id, Long userId, String title) {
        ChatConversation row = getById(id);
        if (row == null || !java.util.Objects.equals(row.getUserId(), userId)) throw new BusinessException(404, "对话不存在");
        if(tutorContext!=null && tutorContext.bound(id))tutorContext.get(id,userId);
        if (title == null || title.isBlank() || title.length() > 200) throw new BusinessException(400, "标题须为1至200个字符");
        this.update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ChatConversation>()
                .eq(ChatConversation::getId, id).eq(ChatConversation::getUserId, userId)
                .set(ChatConversation::getTitle, title.trim()).set(ChatConversation::getUpdateTime, LocalDateTime.now()));
        row = getById(id);
        if (row == null) throw new BusinessException(404, "对话不存在");
        return BeanUtil.copyProperties(row, ConversationVO.class);
    }

    @Override
    public ConversationVO updateState(Long id, Long userId, Boolean pinned, Boolean archived) {
        ChatConversation row = getById(id);
        if (row == null || !java.util.Objects.equals(row.getUserId(), userId)) throw new BusinessException(404, "对话不存在");
        if(tutorContext!=null && tutorContext.bound(id))tutorContext.get(id,userId);
        if (pinned == null && archived == null) throw new BusinessException(400, "至少提供一个状态");
        var update = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ChatConversation>()
                .eq(ChatConversation::getId, id).eq(ChatConversation::getUserId, userId);
        if (pinned != null) update.set(ChatConversation::getPinned, pinned);
        if (archived != null) update.set(ChatConversation::getArchived, archived);
        if (!this.update(update)) throw new BusinessException(404, "对话不存在");
        return BeanUtil.copyProperties(getById(id), ConversationVO.class);
    }

    private ChatConversation createConversationEntity(Long userId, String title) {
        ChatConversation conversation = new ChatConversation();
        conversation.setUserId(userId);
        conversation.setTitle(StrUtil.isNotBlank(title) ? (title.length() > 50 ? title.substring(0, 50) : title) : "新对话");
        conversation.setMessageCount(0);
        conversation.setLastMessage("");
        this.save(conversation);
        return conversation;
    }

    @Override
    @Transactional
    public void deleteConversation(Long conversationId, Long userId) {
        ChatConversation conversation = this.getById(conversationId);
        if (conversation == null || !conversation.getUserId().equals(userId)) {
            throw new BusinessException(404, "对话不存在");
        }
        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId));
        this.removeById(conversationId);
    }

    /**
     * F1：流式发送消息。
     * 流程：保存 user 消息 → 检索相关文档 → 拼 system prompt（含摘要+RAG）→
     * 调 AiService.streamChat 异步推送 → onComplete 回调保存 assistant 消息并更新会话。
     */
    @Override
    public SseEmitter streamSend(ChatSendDTO dto, Long userId) {
        requireChatAiEnabled(userId);
        // 与 AiService 内部 timeout 180s 对齐
        SseEmitter emitter = new SseEmitter(180_000L);

        // 1. 获取/创建会话
        ChatConversation conversation;
        if (dto.getConversationId() == null) {
            conversation = createConversationEntity(userId, dto.getContent());
        } else {
            conversation = this.getById(dto.getConversationId());
            if (conversation == null || !conversation.getUserId().equals(userId)) {
                throw new BusinessException(404, "对话不存在");
            }
        }

        // 2. 先校验引用上下文，避免引用无效时仍落库孤立的用户消息
        final String quoteContext = buildQuoteContext(dto.getQuoteMessageId(), conversation.getId(), userId);
        final var tutor=tutorContext==null?null:tutorContext.prepare(conversation.getId(),userId,dto);
        if(tutor!=null && (dto.getContent()==null || dto.getContent().isBlank() || dto.getContent().length()>4000 || dto.getImages()!=null&&!dto.getImages().isEmpty()))throw new BusinessException(400,"导师首批仅支持1至4000字文字消息");

        if(tutor!=null && tutor.replay()!=null){submitSse(()->{tutorContext.verify(tutor);sendEvent(emitter,"delta",Map.of("content",tutor.replay()));sendEvent(emitter,"done",Map.of("content",tutor.replay()));completeQuietly(emitter);});return emitter;}
        // 3. 保存用户消息（同步）
        ChatMessage userMessage = new ChatMessage();
        userMessage.setConversationId(conversation.getId());
        userMessage.setUserId(userId);
        userMessage.setRole("user");
        userMessage.setContent(dto.getContent());
        userMessage.setTokenCount(dto.getContent().length());
        userMessage.setTruncated(0);
        if(tutor==null)messageMapper.insert(userMessage);else try{tutorContext.saveTurn(userMessage,tutor);}catch(RuntimeException e){tutorContext.failHint(tutor);throw e;}
        if (StrUtil.isNotBlank(dto.getContent())) baseMapper.initializeTitle(conversation.getId(), userId, dto.getContent().trim().substring(0, Math.min(50, dto.getContent().trim().length())));

        // 4. 后续步骤全部离开请求线程：模型读流是阻塞式调用，若留在请求线程，
        //    SseEmitter 的事件会等到方法返回后才一次性刷出，流式退化为整段返回。
        final ChatConversation conv = conversation;
        final Long uid = userId;
        final String content = dto.getContent();
        // 是否为学习教练模式（mode=LEARNING_COACH）。Coach 是「解释/建议」层，不记为学习事件（§435-436）。
        final boolean isCoach = tutor!=null || "LEARNING_COACH".equals(dto.getMode());
        final boolean deepThinking = Boolean.TRUE.equals(dto.getDeepThinking());
        final String model = dto.getModel();
        final boolean hasImages = dto.getImages() != null && !dto.getImages().isEmpty();
        submitSse(() -> {
            try {
                // 回复开始事件：携带真实 conversationId 与用户消息 ID，
                // 前端据此丢弃其它会话的迟到事件，避免切换会话后串流。
                // 用 LinkedHashMap 而非 Map.of：主键回填异常时 ID 可能为 null，不能让事件构造抛出并毁掉整次回答。
                Map<String, Object> startEvent = new LinkedHashMap<>();
                startEvent.put("conversationId", conv.getId());
                startEvent.put("userMessageId", userMessage.getId());
                startEvent.put("timestamp", System.currentTimeMillis());
                sendEvent(emitter, "start", startEvent);

                // Learning Event System（Phase 1）：通用 AI 对话记事件；Coach 模式不记（避免把辅导当学习行为奖励）
                if (!isCoach) {
                    learningEventService.record(uid, LearningEventType.AI_CHAT, "CHAT", conv.getId(),
                            Map.of("hasImages", hasImages, "stream", true));
                }

                // 5. 系统提示词 + RAG 真实检索轨迹：Coach 模式与通用模式分治
                final RagContext rag;
                final String systemPrompt;
                if(tutor!=null){
                    tutorContext.verify(tutor);systemPrompt=tutor.prompt();rag=RagContext.empty();
                } else if (isCoach) {
                    // 学习教练：后端固定提示词 + 前端基于真实数据组装的上下文（仅作数据，不可作为指令）
                    systemPrompt = quoteContext + CoachPrompts.buildCoachSystemPrompt(dto.getCoachContext());
                    rag = RagContext.empty();
                } else if (Boolean.FALSE.equals(dto.getUseKnowledgeBase())) {
                    // 用户主动关闭知识库：不检索，也不发送任何检索轨迹
                    systemPrompt = quoteContext + buildStreamSystemPrompt(conv, Collections.emptyList());
                    rag = RagContext.empty();
                } else {
                    sendEvent(emitter, "status", Map.of("phase", "retrieving"));
                    sendEvent(emitter, "rag_start", Map.of("startedAt", System.currentTimeMillis()));
                    rag = retrieveContext(content, uid, emitter);
                    systemPrompt = quoteContext + buildStreamSystemPrompt(conv, rag.docs());
                }
                sendEvent(emitter, "status", Map.of("phase", "generating"));

                // 6. 流式调用 AI；onComplete 回调持久化 assistant 消息（回答/推理/轨迹分开存）
                aiService.streamChat(systemPrompt, content, uid, null, emitter,
                        new PersistCallback(conv, uid, rag, emitter, true, tutor),
                        null, null, null, deepThinking, model);
            } catch (Exception e) {
                if(tutor!=null)tutorContext.failHint(tutor);
                log.error("流式回答失败 convId={}", conv.getId(), e);
                sendEvent(emitter, "error", Map.of(
                        "error", "AI 调用失败，请稍后重试",
                        "timestamp", System.currentTimeMillis()));
                completeQuietly(emitter);
            }
        });
        return emitter;
    }

    /** 关闭 SSE：客户端可能已断开，关闭失败不影响其它会话。 */
    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // 连接已结束
        }
    }

    /**
     * 流式完成回调：把最终回答、可展示推理内容与真实执行轨迹分别持久化，
     * 并在落库后推送 {@code message} 事件告知前端真实消息 ID。
     */
    private final class PersistCallback implements AiService.StreamCompletionCallback {
        private final ChatConversation conv;
        private final Long uid;
        private final RagContext rag;
        private final SseEmitter emitter;
        /** true=新的一轮对话（消息数 +2）；false=重新生成（用户消息已存在，只刷新会话摘要字段）。 */
        private final boolean countTurn;
        private final com.knowflow.learning.tutor.TutorContextService.Prepared tutor;

        private PersistCallback(ChatConversation conv, Long uid, RagContext rag, SseEmitter emitter, boolean countTurn) {
            this(conv,uid,rag,emitter,countTurn,null);
        }
        private PersistCallback(ChatConversation conv, Long uid, RagContext rag, SseEmitter emitter, boolean countTurn, com.knowflow.learning.tutor.TutorContextService.Prepared tutor) {
            this.tutor=tutor;
            this.conv = conv;
            this.uid = uid;
            this.rag = rag;
            this.emitter = emitter;
            this.countTurn = countTurn;
        }

        @Override
        public void onComplete(String content, boolean success) {
            persist(content, null, null, null, success);
        }

        @Override
        public void onComplete(String content, String reasoning, String reasoningKind,
                               Long reasoningDurationMs, boolean success) {
            persist(content, reasoning, reasoningKind, reasoningDurationMs, success);
        }

        private void persist(String content, String reasoning, String reasoningKind,
                             Long reasoningDurationMs, boolean success) {
            try {
                if(tutor!=null){
                    if(!success || StrUtil.isBlank(content)){tutorContext.failHint(tutor);sendEvent(emitter,"error",Map.of("error","导师生成失败或已停止，原始输入已保留"));completeQuietly(emitter);return;}
                    tutorContext.verify(tutor);
                }
                String finalContent;
                if (StrUtil.isBlank(content)) {
                    finalContent = success ? "（AI 暂未返回内容，请重试）" : "（生成被中断或失败，请重试）";
                } else {
                    finalContent = content;
                }
                ChatMessage assistantMessage = new ChatMessage();
                assistantMessage.setConversationId(conv.getId());
                assistantMessage.setUserId(uid);
                assistantMessage.setRole("assistant");
                assistantMessage.setContent(finalContent);
                assistantMessage.setDocReferences(tutor==null?buildDocReferences(rag.docs()):tutor.references());
                assistantMessage.setTokenCount(finalContent.length());
                // success=false 表示流被打断（用户点停止或网络中断），标记 truncated=1
                assistantMessage.setTruncated(success ? 0 : 1);
                // 推理内容不拼进 content：仅当 Provider 真实返回时才写入独立列
                if (StrUtil.isNotBlank(reasoning)) {
                    assistantMessage.setReasoningContent(reasoning);
                    assistantMessage.setReasoningKind(reasoningKind == null ? "provider_text" : reasoningKind);
                    assistantMessage.setReasoningDurationMs(
                            reasoningDurationMs == null ? null : reasoningDurationMs.intValue());
                }
                if (rag.trace() != null) {
                    try {
                        assistantMessage.setTraceJson(EVENT_MAPPER.writeValueAsString(rag.trace()));
                    } catch (Exception e) {
                        log.warn("RAG 轨迹序列化失败 convId={}: {}", conv.getId(), e.getMessage());
                    }
                }
                if(tutor==null)messageMapper.insert(assistantMessage);else tutorContext.saveAssistant(assistantMessage,tutor);

                String lastMsg = finalContent.length() > 3900 ? finalContent.substring(0, 3900) : finalContent;
                if (countTurn) {
                    conv.setMessageCount((conv.getMessageCount() == null ? 0 : conv.getMessageCount()) + 2);
                    conv.setLastMessage(lastMsg);
                    if(tutor==null)baseMapper.completeTurn(conv.getId(), uid, lastMsg);
                    ChatConversation latest = getById(conv.getId());
                    if (latest != null) conv.setMessageCount(latest.getMessageCount());
                } else {
                    ChatServiceImpl.this.update(
                            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ChatConversation>()
                                    .eq(ChatConversation::getId, conv.getId())
                                    .eq(ChatConversation::getUserId, uid)
                                    .set(ChatConversation::getLastMessage, lastMsg)
                                    .set(ChatConversation::getUpdateTime, LocalDateTime.now()));
                }

                if (emitter != null && assistantMessage.getId() != null) {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("messageId", assistantMessage.getId());
                    payload.put("conversationId", conv.getId());
                    payload.put("truncated", success ? 0 : 1);
                    payload.put("timestamp", System.currentTimeMillis());
                    sendEvent(emitter, "message", payload);
                }

                // 异步触发摘要生成（每 6 轮生成一次）
                if (countTurn && tutor == null) {
                    maybeSummarize(conv);
                }
            } catch (Exception e) {
                log.error("流式结束回调保存消息失败 convId={}", conv.getId(), e);
                if(tutor!=null){tutorContext.failHint(tutor);sendEvent(emitter,"error",Map.of("error","导师回复保存失败，请重新读取历史后重试"));completeQuietly(emitter);}
            }
        }
    }

    /**
     * 执行真实 RAG 检索并推送 rag_step / rag_done 事件。
     * 每个步骤的状态与耗时均来自 DocChunkService 的真实执行结果，不补造未执行的步骤。
     */
    private RagContext retrieveContext(String query, Long userId, SseEmitter emitter) {
        if (StrUtil.isBlank(query) || userId == null) {
            return RagContext.empty();
        }
        final int[] sequence = {0};
        DocChunkService.SearchOutcome outcome;
        try {
            outcome = docChunkService.searchSimilarTraced(query, 5, userId, step -> {
                sequence[0]++;
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("stepId", step.getStepId());
                payload.put("type", step.getType());
                payload.put("label", step.getLabel());
                payload.put("status", step.getStatus());
                payload.put("detail", step.getDetail());
                payload.put("durationMs", step.getDurationMs());
                payload.put("resultCount", step.getResultCount());
                payload.put("sequence", sequence[0]);
                payload.put("timestamp", System.currentTimeMillis());
                sendEvent(emitter, "rag_step", payload);
            });
        } catch (Exception e) {
            log.warn("知识库检索失败，本次回答不使用检索上下文: {}", e.getMessage());
            Map<String, Object> failed = new LinkedHashMap<>();
            failed.put("type", "retrieval");
            failed.put("label", "知识库检索");
            failed.put("status", "error");
            failed.put("detail", "检索服务异常，本次回答不使用知识库上下文");
            failed.put("sequence", sequence[0] + 1);
            failed.put("timestamp", System.currentTimeMillis());
            sendEvent(emitter, "rag_step", failed);
            Map<String, Object> done = new LinkedHashMap<>();
            done.put("resultCount", 0);
            done.put("success", false);
            sendEvent(emitter, "rag_done", done);
            return RagContext.empty();
        }

        RagTrace trace = outcome.trace;
        List<DocDocument> docs = new ArrayList<>();
        for (String chunk : outcome.chunks) {
            DocDocument d = new DocDocument();
            d.setTitle("相关文档片段");
            d.setContent(chunk);
            d.setSummary(chunk.length() > 200 ? chunk.substring(0, 200) + "..." : chunk);
            docs.add(d);
        }
        if (trace != null) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("traceId", trace.getTraceId());
            payload.put("strategy", trace.getStrategy());
            payload.put("accessibleDocCount", trace.getAccessibleDocCount());
            payload.put("resultCount", trace.getResultCount());
            payload.put("evidenceSufficient", trace.isEvidenceSufficient());
            payload.put("durationMs", trace.getDurationMs());
            payload.put("endedAt", System.currentTimeMillis());
            payload.put("startedAt", System.currentTimeMillis() - trace.getDurationMs());
            sendEvent(emitter, "rag_done", payload);
        }
        return new RagContext(docs, trace);
    }

    /** 统一的 SSE 事件发送；客户端断开时返回 false，不中断业务流程。 */
    private boolean sendEvent(SseEmitter emitter, String name, Object data) {
        if (emitter == null) return false;
        try {
            String json = EVENT_MAPPER.writeValueAsString(data);
            emitter.send(SseEmitter.event().name(name).data(json));
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE 发送失败（客户端可能已断开）: event={}, err={}", name, e.getMessage());
            return false;
        }
    }

    /** 拼装流式对话的 system prompt：摘要 + RAG 上下文。 */
    private String buildStreamSystemPrompt(ChatConversation conversation, List<DocDocument> contextDocs) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是 KnowFlow 学习助手。请基于以下信息回答用户的问题。\n\n");
        if (StrUtil.isNotBlank(conversation.getSummary())) {
            sb.append("【历史对话摘要】\n").append(conversation.getSummary()).append("\n\n");
        }
        if (contextDocs != null && !contextDocs.isEmpty()) {
            sb.append("【相关文档上下文】\n");
            for (int i = 0; i < contextDocs.size(); i++) {
                DocDocument d = contextDocs.get(i);
                sb.append("--- 文档").append(i + 1).append(" ---\n");
                String c = d.getContent();
                if (StrUtil.isNotBlank(c)) {
                    // 截断避免上下文过长
                    sb.append(c.length() > 1500 ? c.substring(0, 1500) + "..." : c);
                }
                sb.append("\n\n");
            }
        }
        return sb.toString();
    }

    /**
     * 每 6 轮（messageCount 为 6 的倍数）调用 AI 同步生成对话摘要。
     * 失败时静默降级，不影响主流程。
     */
    private void maybeSummarize(ChatConversation conversation) {
        if (conversation.getMessageCount() == null
                || conversation.getMessageCount() == 0
                || conversation.getMessageCount() % 6 != 0) {
            return;
        }
        try {
            List<ChatMessage> recent = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getConversationId, conversation.getId())
                    .orderByDesc(ChatMessage::getCreateTime)
                    .last("LIMIT 6"));
            if (recent.size() < 6) {
                return;
            }
            Collections.reverse(recent);  // 按时间正序拼接
            StringBuilder dialogue = new StringBuilder();
            for (ChatMessage m : recent) {
                dialogue.append("【").append(m.getRole()).append("】")
                        .append(m.getContent() != null ? m.getContent() : "")
                        .append("\n");
            }
            String systemPrompt = "请用 200 字以内总结以下对话的关键信息（用户意图、已讨论的主题、达成的结论、待解决的问题）。只输出总结，不要附加说明。";
            String summary = aiService.complete(systemPrompt, dialogue.toString(), null, conversation.getUserId());
            if (StrUtil.isNotBlank(summary)) {
                conversation.setSummary(summary);
                conversation.setSummaryUpdatedAt(LocalDateTime.now());
                this.update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ChatConversation>()
                        .eq(ChatConversation::getId, conversation.getId()).eq(ChatConversation::getUserId, conversation.getUserId())
                        .set(ChatConversation::getSummary, summary).set(ChatConversation::getSummaryUpdatedAt, LocalDateTime.now()));
                log.info("对话摘要已更新 convId={} len={}", conversation.getId(), summary.length());
            }
        } catch (Exception e) {
            log.warn("生成对话摘要失败 convId={}: {}", conversation.getId(), e.getMessage());
        }
    }

    /** 非流式入口的检索：复用同一套检索与权限过滤，只是不推送轨迹事件。 */
    private List<DocDocument> searchRelatedDocs(String query, Long userId) {
        return retrieveContext(query, userId, null).docs();
    }

    private String buildDocReferences(List<DocDocument> docs) {
        if (docs == null || docs.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(String.format("[%d] %s", i + 1, docs.get(i).getTitle()));
        }
        return sb.toString();
    }

    /**
     * 构造引用上下文：校验被引用消息属于当前用户和当前会话，避免越权读取他人内容。
     * 只拼接被引用的单条消息，不重复发送整段历史。
     */
    private String buildQuoteContext(Long quoteMessageId, Long conversationId, Long userId) {
        if (quoteMessageId == null) {
            return "";
        }
        ChatMessage quoted = messageMapper.selectById(quoteMessageId);
        if (quoted == null
                || !java.util.Objects.equals(quoted.getConversationId(), conversationId)
                || !java.util.Objects.equals(quoted.getUserId(), userId)) {
            throw new BusinessException(404, "引用的消息不存在");
        }
        String content = quoted.getContent() == null ? "" : quoted.getContent();
        if (content.length() > 1200) {
            content = content.substring(0, 1200) + "…";
        }
        return "【用户引用的历史消息】\n" + content + "\n\n请结合用户引用的内容作答。\n\n";
    }

    @Override
    @Transactional
    public MessageVO updateMessage(Long messageId, Long userId, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(400, "消息内容不能为空");
        }
        if (content.length() > 20_000) {
            throw new BusinessException(400, "消息内容过长");
        }
        ChatMessage message = messageMapper.selectById(messageId);
        if (message == null || !java.util.Objects.equals(message.getUserId(), userId)) {
            throw new BusinessException(404, "消息不存在");
        }
        if (!"user".equals(message.getRole())) {
            throw new BusinessException(400, "只能编辑用户消息");
        }
        if(tutorContext!=null)tutorContext.forbidBranch(message.getConversationId());
        message.setContent(content.trim());
        message.setTokenCount(content.length());
        messageMapper.updateById(message);
        // 版本分支：编辑后截断后续回答，再通过 regenerate 生成新的回答
        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, message.getConversationId())
                .gt(ChatMessage::getId, messageId));
        return BeanUtil.copyProperties(message, MessageVO.class);
    }

    @Override
    public SseEmitter regenerate(Long messageId, com.knowflow.dto.ChatRegenerateDTO dto, Long userId) {
        requireChatAiEnabled(userId);
        final com.knowflow.dto.ChatRegenerateDTO options = dto == null
                ? new com.knowflow.dto.ChatRegenerateDTO() : dto;
        ChatMessage target = messageMapper.selectById(messageId);
        if (target == null || !java.util.Objects.equals(target.getUserId(), userId)) {
            throw new BusinessException(404, "消息不存在");
        }
        ChatConversation conversation = this.getById(target.getConversationId());
        if (conversation == null || !java.util.Objects.equals(conversation.getUserId(), userId)) {
            throw new BusinessException(404, "对话不存在");
        }

        if(tutorContext!=null)tutorContext.forbidBranch(conversation.getId());
        // 找到触发消息对应的用户提问（assistant 消息取它之前最近的一条 user 消息）
        List<ChatMessage> all = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversation.getId())
                .orderByAsc(ChatMessage::getCreateTime)
                .orderByAsc(ChatMessage::getId));
        int targetIndex = -1;
        for (int i = 0; i < all.size(); i++) {
            if (java.util.Objects.equals(all.get(i).getId(), messageId)) {
                targetIndex = i;
                break;
            }
        }
        if (targetIndex < 0) {
            throw new BusinessException(404, "消息不存在");
        }
        ChatMessage userMessage = null;
        for (int i = targetIndex; i >= 0; i--) {
            if ("user".equals(all.get(i).getRole())) {
                userMessage = all.get(i);
                break;
            }
        }
        if (userMessage == null || StrUtil.isBlank(userMessage.getContent())) {
            throw new BusinessException(400, "没有可重新生成的用户消息");
        }

        // 截断分支：目标 assistant 消息及其后续回答都逻辑删除；用户消息本身保留
        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversation.getId())
                .ge(ChatMessage::getId, messageId)
                .eq(ChatMessage::getRole, "assistant"));
        if ("user".equals(target.getRole())) {
            messageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getConversationId, conversation.getId())
                    .gt(ChatMessage::getId, messageId));
        }

        SseEmitter emitter = new SseEmitter(180_000L);
        final ChatConversation conv = conversation;
        final Long uid = userId;
        final String prompt = userMessage.getContent();
        final Long userMessageId = userMessage.getId();
        final boolean isCoach = "LEARNING_COACH".equals(options.getMode());
        final boolean deepThinking = Boolean.TRUE.equals(options.getDeepThinking());
        final String model = options.getModel();
        final String quoteContext = buildQuoteContext(options.getQuoteMessageId(), conv.getId(), userId);

        // 与 /chat/stream 一致：生成过程在请求线程之外执行，保证事件逐条刷出而不是整段返回
        submitSse(() -> {
            try {
                // 开始事件：前端用 conversationId + 用户消息 ID 过滤迟到事件
                Map<String, Object> startEvent = new LinkedHashMap<>();
                startEvent.put("conversationId", conv.getId());
                startEvent.put("userMessageId", userMessageId);
                startEvent.put("regenerate", true);
                startEvent.put("timestamp", System.currentTimeMillis());
                sendEvent(emitter, "start", startEvent);

                final RagContext rag;
                final String systemPrompt;
                if (isCoach) {
                    systemPrompt = quoteContext + CoachPrompts.buildCoachSystemPrompt(options.getCoachContext());
                    rag = RagContext.empty();
                } else if (Boolean.FALSE.equals(options.getUseKnowledgeBase())) {
                    systemPrompt = quoteContext + buildStreamSystemPrompt(conv, Collections.emptyList());
                    rag = RagContext.empty();
                } else {
                    sendEvent(emitter, "status", Map.of("phase", "retrieving"));
                    sendEvent(emitter, "rag_start", Map.of("startedAt", System.currentTimeMillis()));
                    rag = retrieveContext(prompt, uid, emitter);
                    systemPrompt = quoteContext + buildStreamSystemPrompt(conv, rag.docs());
                }
                sendEvent(emitter, "status", Map.of("phase", "generating"));

                aiService.streamChat(systemPrompt, prompt, uid, null, emitter,
                        new PersistCallback(conv, uid, rag, emitter, false),
                        null, null, null, deepThinking, model);
            } catch (Exception e) {
                log.error("重新生成回答失败 convId={}", conv.getId(), e);
                sendEvent(emitter, "error", Map.of(
                        "error", "AI 调用失败，请稍后重试",
                        "timestamp", System.currentTimeMillis()));
                completeQuietly(emitter);
            }
        });

        return emitter;
    }
}
