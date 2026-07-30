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
