package com.knowflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowflow.dto.ChatSendDTO;
import com.knowflow.entity.ChatConversation;
import com.knowflow.entity.ChatMessage;
import com.knowflow.entity.DocDocument;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.ChatConversationMapper;
import com.knowflow.mapper.ChatMessageMapper;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.service.AiService;
import com.knowflow.service.ChatService;
import com.knowflow.service.DocChunkService;
import com.knowflow.vo.ConversationVO;
import com.knowflow.vo.MessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** 聊天业务服务实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl extends ServiceImpl<ChatConversationMapper, ChatConversation> implements ChatService {

    private final ChatMessageMapper messageMapper;
    private final AiService aiService;
    private final DocDocumentMapper docMapper;
    private final DocChunkService docChunkService;

    @Override
    public List<ConversationVO> getConversationList(Long userId) {
        List<ChatConversation> conversations = this.list(new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getUserId, userId)
                .orderByDesc(ChatConversation::getUpdateTime));
        return conversations.stream()
                .map(c -> BeanUtil.copyProperties(c, ConversationVO.class))
                .collect(Collectors.toList());
    }

    @Override
    public List<MessageVO> getMessageList(Long conversationId, Long userId) {
        ChatConversation conversation = this.getById(conversationId);
        if (conversation == null || !conversation.getUserId().equals(userId)) {
            throw new BusinessException(404, "对话不存在");
        }
        List<ChatMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByAsc(ChatMessage::getCreateTime));
        return messages.stream()
                .map(m -> BeanUtil.copyProperties(m, MessageVO.class))
                .collect(Collectors.toList());
    }

    /** 发送消息主流程：落库用户消息 → 检索相关文档 → 调用 AI → 落库助手消息并更新会话。 */
    @Override
    @Transactional
    public MessageVO sendMessage(ChatSendDTO dto, Long userId) {
        ChatConversation conversation;
        if (dto.getConversationId() == null) {
            conversation = createConversationEntity(userId, dto.getContent());
        } else {
            conversation = this.getById(dto.getConversationId());
            if (conversation == null || !conversation.getUserId().equals(userId)) {
                throw new BusinessException(404, "对话不存在");
            }
        }
        ChatMessage userMessage = new ChatMessage();
        userMessage.setConversationId(conversation.getId());
        userMessage.setUserId(userId);
        userMessage.setRole("user");
        userMessage.setContent(dto.getContent());
        userMessage.setTokenCount(dto.getContent().length());
        messageMapper.insert(userMessage);
        List<DocDocument> contextDocs = searchRelatedDocs(dto.getContent());
        // A-RAG + A-CHAT：有图片时使用视觉模型，否则使用文本模型
        List<String> images = dto.getImages();
        String replyContent;
        if (images != null && !images.isEmpty()) {
            replyContent = aiService.chatWithImages(
                    dto.getContent() != null ? dto.getContent() : "",
                    images, contextDocs, dto.getModel(), userId);
        } else {
            replyContent = aiService.chat(dto.getContent(), contextDocs, dto.getModel(), userId);
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
        this.updateById(conversation);
        return BeanUtil.copyProperties(assistantMessage, MessageVO.class);
    }

    @Override
    public ConversationVO createConversation(Long userId, String title) {
        ChatConversation conversation = createConversationEntity(userId, title);
        return BeanUtil.copyProperties(conversation, ConversationVO.class);
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

        // 2. 保存用户消息（同步）
        ChatMessage userMessage = new ChatMessage();
        userMessage.setConversationId(conversation.getId());
        userMessage.setUserId(userId);
        userMessage.setRole("user");
        userMessage.setContent(dto.getContent());
        userMessage.setTokenCount(dto.getContent().length());
        userMessage.setTruncated(0);
        messageMapper.insert(userMessage);

        // 3. 检索 RAG 文档 + 拼上下文 system prompt
        List<DocDocument> contextDocs = searchRelatedDocs(dto.getContent());
        String systemPrompt = buildStreamSystemPrompt(conversation, contextDocs);
        final ChatConversation conv = conversation;
        final List<DocDocument> docs = contextDocs;
        final Long uid = userId;

        // 4. 异步流式调用 AI；onComplete 回调持久化 assistant 消息
        aiService.streamChat(systemPrompt, dto.getContent(), userId, null, emitter,
                (content, success) -> {
                    try {
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
                        assistantMessage.setDocReferences(buildDocReferences(docs));
                        assistantMessage.setTokenCount(finalContent.length());
                        // success=false 表示流被打断（用户点停止或网络中断），标记 truncated=1
                        assistantMessage.setTruncated(success ? 0 : 1);
                        messageMapper.insert(assistantMessage);

                        conv.setMessageCount((conv.getMessageCount() == null ? 0 : conv.getMessageCount()) + 2);
                        String lastMsg = finalContent.length() > 3900 ? finalContent.substring(0, 3900) : finalContent;
                        conv.setLastMessage(lastMsg);
                        ChatServiceImpl.this.updateById(conv);

                        // 异步触发摘要生成（每 6 轮生成一次）
                        maybeSummarize(conv);
                    } catch (Exception e) {
                        log.error("流式结束回调保存消息失败 convId={}", conv.getId(), e);
                    }
                },
                null, null, null);  // temperature/maxTokens/topP 用默认值

        return emitter;
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
                this.updateById(conversation);
                log.info("对话摘要已更新 convId={} len={}", conversation.getId(), summary.length());
            }
        } catch (Exception e) {
            log.warn("生成对话摘要失败 convId={}: {}", conversation.getId(), e.getMessage());
        }
    }

    private List<DocDocument> searchRelatedDocs(String query) {
        if (StrUtil.isBlank(query)) {
            return Collections.emptyList();
        }
        // A-RAG：优先使用向量相似度检索，降级为 LIKE 关键词匹配
        try {
            List<String> chunks = docChunkService.searchSimilar(query, 5);
            if (!chunks.isEmpty()) {
                // 从分块内容反查归属文档（取文档标题作为上下文指示）
                List<DocDocument> results = new ArrayList<>();
                for (String chunk : chunks) {
                    // 默认只取前 5 个字符作为文档展示，实际 content 在后端 AI 拼接
                    DocDocument d = new DocDocument();
                    d.setTitle("相关文档片段");
                    d.setContent(chunk);
                    d.setSummary(chunk.length() > 200 ? chunk.substring(0, 200) + "..." : chunk);
                    results.add(d);
                }
                return results;
            }
        } catch (Exception e) {
            log.warn("向量检索失败，降级为 LIKE 检索: {}", e.getMessage());
        }
        // 降级：LIKE 关键词匹配
        String keyword = query.length() > 50 ? query.substring(0, 50) : query;
        return docMapper.selectList(new LambdaQueryWrapper<DocDocument>()
                .eq(DocDocument::getStatus, 1)
                .and(w -> w.like(DocDocument::getTitle, keyword)
                        .or().like(DocDocument::getSummary, keyword)
                        .or().like(DocDocument::getContent, keyword)
                        .or().like(DocDocument::getTags, keyword))
                .last("LIMIT 5"));
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
}
