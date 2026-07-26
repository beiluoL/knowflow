package com.knowflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.knowflow.dto.ChatSendDTO;
import com.knowflow.entity.ChatConversation;
import com.knowflow.vo.ConversationVO;
import com.knowflow.vo.MessageVO;

import java.util.List;

/** 聊天业务服务接口。 */
public interface ChatService extends IService<ChatConversation> {

    List<ConversationVO> getConversationList(Long userId);

    List<MessageVO> getMessageList(Long conversationId, Long userId);

    /** 发送用户消息并调用 AI 生成回复，返回助手消息（含相关文档引用）。 */
    MessageVO sendMessage(ChatSendDTO dto, Long userId);

    ConversationVO createConversation(Long userId, String title);

    void deleteConversation(Long conversationId, Long userId);
}
