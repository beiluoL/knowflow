package com.zhishiku.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhishiku.dto.ChatSendDTO;
import com.zhishiku.entity.ChatConversation;
import com.zhishiku.entity.ChatMessage;
import com.zhishiku.exception.BusinessException;
import com.zhishiku.mapper.ChatConversationMapper;
import com.zhishiku.mapper.ChatMessageMapper;
import com.zhishiku.service.ChatService;
import com.zhishiku.vo.ConversationVO;
import com.zhishiku.vo.MessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl extends ServiceImpl<ChatConversationMapper, ChatConversation> implements ChatService {

    private final ChatMessageMapper messageMapper;

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
            throw new BusinessException("对话不存在");
        }
        List<ChatMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByAsc(ChatMessage::getCreateTime));
        return messages.stream()
                .map(m -> BeanUtil.copyProperties(m, MessageVO.class))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MessageVO sendMessage(ChatSendDTO dto, Long userId) {
        ChatConversation conversation;
        if (dto.getConversationId() == null) {
            conversation = createConversationEntity(userId, dto.getContent());
        } else {
            conversation = this.getById(dto.getConversationId());
            if (conversation == null || !conversation.getUserId().equals(userId)) {
                throw new BusinessException("对话不存在");
            }
        }
        ChatMessage userMessage = new ChatMessage();
        userMessage.setConversationId(conversation.getId());
        userMessage.setUserId(userId);
        userMessage.setRole("user");
        userMessage.setContent(dto.getContent());
        userMessage.setTokenCount(dto.getContent().length());
        messageMapper.insert(userMessage);
        String replyContent = generateMockReply(dto.getContent());
        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setConversationId(conversation.getId());
        assistantMessage.setUserId(userId);
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(replyContent);
        assistantMessage.setDocReferences("[1] 相关文档示例");
        assistantMessage.setTokenCount(replyContent.length());
        messageMapper.insert(assistantMessage);
        conversation.setMessageCount(conversation.getMessageCount() + 2);
        conversation.setLastMessage(replyContent);
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
            throw new BusinessException("对话不存在");
        }
        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId));
        this.removeById(conversationId);
    }

    private String generateMockReply(String userMessage) {
        return "这是一个模拟的 AI 回复。您的问题是：\"" + userMessage +
                "\"。在实际项目中，这里会接入真实的 AI 服务来生成智能回答。";
    }
}
