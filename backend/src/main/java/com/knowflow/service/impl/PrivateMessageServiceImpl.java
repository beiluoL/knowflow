package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.dto.PrivateMessageSendDTO;
import com.knowflow.entity.PrivateConversation;
import com.knowflow.entity.PrivateConversationRead;
import com.knowflow.entity.PrivateMessage;
import com.knowflow.entity.SysUser;
import com.knowflow.mapper.PrivateConversationMapper;
import com.knowflow.mapper.PrivateConversationReadMapper;
import com.knowflow.mapper.PrivateMessageMapper;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.service.PrivateMessageService;
import com.knowflow.vo.PrivateConversationVO;
import com.knowflow.vo.PrivateMessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 私聊消息服务实现
 */
@Service
@RequiredArgsConstructor
public class PrivateMessageServiceImpl implements PrivateMessageService {

    private final PrivateConversationMapper conversationMapper;
    private final PrivateConversationReadMapper conversationReadMapper;
    private final PrivateMessageMapper messageMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    @Transactional
    public PrivateConversationVO getOrCreateConversation(Long userId, Long targetUserId) {
        if (targetUserId == null || targetUserId.equals(userId)) {
            throw new RuntimeException("不能与自己发起私聊");
        }
        // 统一存储顺序，保证两人会话唯一
        long a = Math.min(userId, targetUserId);
        long b = Math.max(userId, targetUserId);

        LambdaQueryWrapper<PrivateConversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrivateConversation::getUserAId, a);
        wrapper.eq(PrivateConversation::getUserBId, b);
        PrivateConversation conversation = conversationMapper.selectOne(wrapper);

        if (conversation == null) {
            conversation = new PrivateConversation();
            conversation.setUserAId(a);
            conversation.setUserBId(b);
            conversationMapper.insert(conversation);
        }
        return toConversationVO(conversation, userId);
    }

    @Override
    public List<PrivateConversationVO> getMyConversations(Long userId) {
        LambdaQueryWrapper<PrivateConversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrivateConversation::getUserAId, userId)
                .or()
                .eq(PrivateConversation::getUserBId, userId);
        wrapper.orderByDesc(PrivateConversation::getLastMessageTime);

        List<PrivateConversation> conversations = conversationMapper.selectList(wrapper);
        return conversations.stream()
                .map(c -> toConversationVO(c, userId))
                .collect(Collectors.toList());
    }

    @Override
    public Page<PrivateMessageVO> getMessages(Long conversationId, Long userId, int page, int size) {
        checkParticipant(conversationId, userId);

        LambdaQueryWrapper<PrivateMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrivateMessage::getConversationId, conversationId);
        wrapper.orderByDesc(PrivateMessage::getCreateTime);

        Page<PrivateMessage> messagePage = new Page<>(page, size);
        messagePage = messageMapper.selectPage(messagePage, wrapper);

        Set<Long> senderIds = messagePage.getRecords().stream()
                .map(PrivateMessage::getSenderId)
                .collect(Collectors.toSet());
        Map<Long, SysUser> senderMap = new HashMap<>();
        if (!senderIds.isEmpty()) {
            LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.in(SysUser::getId, senderIds);
            sysUserMapper.selectList(userWrapper).forEach(u -> senderMap.put(u.getId(), u));
        }

        // 对方（会话另一人）已读游标：用于判断“我发出的消息”是否被对方读过
        Long otherReadCursor = getOtherReadCursor(conversationId, userId);

        Page<PrivateMessageVO> voPage = new Page<>(page, size, messagePage.getTotal());
        voPage.setRecords(messagePage.getRecords().stream().map(m -> {
            PrivateMessageVO vo = convertToMessageVO(m, senderMap);
            boolean isMine = m.getSenderId().equals(userId);
            vo.setIsMine(isMine);
            // 已读只对我发出的消息有意义
            vo.setRead(isMine && otherReadCursor != null && m.getId() <= otherReadCursor);
            return vo;
        }).collect(Collectors.toList()));

        return voPage;
    }

    @Override
    @Transactional
    public PrivateMessageVO sendMessage(PrivateMessageSendDTO dto, Long userId) {
        PrivateConversation conversation = checkParticipant(dto.getConversationId(), userId);

        PrivateMessage message = new PrivateMessage();
        message.setConversationId(dto.getConversationId());
        message.setSenderId(userId);
        message.setMessageType(dto.getMessageType() != null ? dto.getMessageType() : "TEXT");
        message.setContent(dto.getContent());
        message.setFileUrl(dto.getFileUrl());
        message.setFileName(dto.getFileName());
        message.setFileSize(dto.getFileSize());
        message.setCodeLanguage(dto.getCodeLanguage());
        message.setRecalled(0);
        messageMapper.insert(message);

        // 更新会话最后消息
        conversation.setLastMessageId(message.getId());
        conversation.setLastMessageTime(message.getCreateTime());
        conversationMapper.updateById(conversation);

        // 发送者自己发的消息算已读
        upsertRead(dto.getConversationId(), userId, message.getId());

        SysUser sender = sysUserMapper.selectById(userId);
        Map<Long, SysUser> senderMap = new HashMap<>();
        if (sender != null) senderMap.put(userId, sender);

        PrivateMessageVO vo = convertToMessageVO(message, senderMap);
        vo.setIsMine(true);
        // 刚发出的消息，对方尚未阅读
        vo.setRead(false);
        return vo;
    }

    @Override
    @Transactional
    public Long markAsRead(Long conversationId, Long userId) {
        checkParticipant(conversationId, userId);

        LambdaQueryWrapper<PrivateMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrivateMessage::getConversationId, conversationId);
        wrapper.orderByDesc(PrivateMessage::getId);
        wrapper.last("LIMIT 1");
        PrivateMessage lastMessage = messageMapper.selectOne(wrapper);

        if (lastMessage != null) {
            upsertRead(conversationId, userId, lastMessage.getId());
            return lastMessage.getId();
        }
        return null;
    }

    @Override
    public int getUnreadCount(Long conversationId, Long userId) {
        checkParticipant(conversationId, userId);

        LambdaQueryWrapper<PrivateConversationRead> readWrapper = new LambdaQueryWrapper<>();
        readWrapper.eq(PrivateConversationRead::getConversationId, conversationId);
        readWrapper.eq(PrivateConversationRead::getUserId, userId);
        PrivateConversationRead read = conversationReadMapper.selectOne(readWrapper);

        if (read == null || read.getLastReadMessageId() == null) {
            LambdaQueryWrapper<PrivateMessage> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PrivateMessage::getConversationId, conversationId);
            return Math.toIntExact(messageMapper.selectCount(wrapper));
        }

        LambdaQueryWrapper<PrivateMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrivateMessage::getConversationId, conversationId);
        wrapper.gt(PrivateMessage::getId, read.getLastReadMessageId());
        return Math.toIntExact(messageMapper.selectCount(wrapper));
    }

    @Override
    @Transactional
    public void recallMessage(Long messageId, Long userId) {
        PrivateMessage message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new RuntimeException("消息不存在");
        }
        if (!message.getSenderId().equals(userId)) {
            throw new RuntimeException("只能撤回自己的消息");
        }
        message.setRecalled(1);
        messageMapper.updateById(message);
    }

    // ===== 私有辅助方法 =====

    private PrivateConversation checkParticipant(Long conversationId, Long userId) {
        PrivateConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new RuntimeException("会话不存在");
        }
        if (!conversation.getUserAId().equals(userId) && !conversation.getUserBId().equals(userId)) {
            throw new RuntimeException("无权访问该会话");
        }
        return conversation;
    }

    private void upsertRead(Long conversationId, Long userId, Long lastReadMessageId) {
        LambdaQueryWrapper<PrivateConversationRead> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrivateConversationRead::getConversationId, conversationId);
        wrapper.eq(PrivateConversationRead::getUserId, userId);
        PrivateConversationRead read = conversationReadMapper.selectOne(wrapper);

        if (read == null) {
            read = new PrivateConversationRead();
            read.setConversationId(conversationId);
            read.setUserId(userId);
            read.setLastReadMessageId(lastReadMessageId);
            conversationReadMapper.insert(read);
        } else {
            read.setLastReadMessageId(lastReadMessageId);
            conversationReadMapper.updateById(read);
        }
    }

    /** 取会话中“对方”的最后已读消息Id，用于判断我发出的消息是否被已读 */
    private Long getOtherReadCursor(Long conversationId, Long viewerId) {
        PrivateConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) return null;
        Long otherId = conversation.getUserAId().equals(viewerId)
                ? conversation.getUserBId()
                : conversation.getUserAId();
        LambdaQueryWrapper<PrivateConversationRead> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrivateConversationRead::getConversationId, conversationId);
        wrapper.eq(PrivateConversationRead::getUserId, otherId);
        PrivateConversationRead read = conversationReadMapper.selectOne(wrapper);
        return read != null ? read.getLastReadMessageId() : null;
    }

    private PrivateConversationVO toConversationVO(PrivateConversation conversation, Long userId) {
        PrivateConversationVO vo = new PrivateConversationVO();
        vo.setId(conversation.getId());
        vo.setCreateTime(conversation.getCreateTime());

        // 对方用户ID
        Long targetId = conversation.getUserAId().equals(userId)
                ? conversation.getUserBId()
                : conversation.getUserAId();
        vo.setTargetUserId(targetId);

        SysUser target = sysUserMapper.selectById(targetId);
        if (target != null) {
            vo.setTargetUserName(target.getNickname() != null ? target.getNickname() : target.getUsername());
            vo.setTargetUserAvatar(target.getAvatar());
        }

        // 最后一条消息预览
        if (conversation.getLastMessageId() != null) {
            PrivateMessage last = messageMapper.selectById(conversation.getLastMessageId());
            if (last != null) {
                vo.setLastMessageId(last.getId());
                vo.setLastMessageType(last.getMessageType());
                vo.setLastMessageContent(last.getRecalled() == 1 ? "[撤回的消息]" : last.getContent());
                vo.setLastMessageTime(last.getCreateTime());
            }
        }

        vo.setUnreadCount(getUnreadCount(conversation.getId(), userId));
        return vo;
    }

    private PrivateMessageVO convertToMessageVO(PrivateMessage message, Map<Long, SysUser> senderMap) {
        PrivateMessageVO vo = new PrivateMessageVO();
        vo.setId(message.getId());
        vo.setConversationId(message.getConversationId());
        vo.setSenderId(message.getSenderId());
        vo.setMessageType(message.getMessageType());
        vo.setContent(message.getContent());
        vo.setFileUrl(message.getFileUrl());
        vo.setFileName(message.getFileName());
        vo.setFileSize(message.getFileSize());
        vo.setCodeLanguage(message.getCodeLanguage());
        vo.setCreateTime(message.getCreateTime());
        vo.setRecalled(message.getRecalled() == 1);

        SysUser sender = senderMap.get(message.getSenderId());
        if (sender != null) {
            vo.setSenderName(sender.getNickname() != null ? sender.getNickname() : sender.getUsername());
            vo.setSenderAvatar(sender.getAvatar());
        }
        return vo;
    }
}
