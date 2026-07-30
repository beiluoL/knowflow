package com.knowflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.dto.PrivateMessageSendDTO;
import com.knowflow.vo.PrivateConversationVO;
import com.knowflow.vo.PrivateMessageVO;

import java.util.List;

/**
 * 私聊消息服务
 */
public interface PrivateMessageService {

    /** 获取或创建与某用户的会话 */
    PrivateConversationVO getOrCreateConversation(Long userId, Long targetUserId);

    /** 获取我的所有私聊会话（含对方信息、未读数） */
    List<PrivateConversationVO> getMyConversations(Long userId);

    /** 获取会话消息历史（分页） */
    Page<PrivateMessageVO> getMessages(Long conversationId, Long userId, int page, int size);

    /** 发送私聊消息（仅持久化，推送由调用方处理） */
    PrivateMessageVO sendMessage(PrivateMessageSendDTO dto, Long userId);

    /** 标记会话已读，返回已读到的最后一条消息Id（供已读回执广播） */
    Long markAsRead(Long conversationId, Long userId);

    /** 获取会话未读消息数 */
    int getUnreadCount(Long conversationId, Long userId);

    /** 撤回消息（仅本人） */
    void recallMessage(Long messageId, Long userId);
}
