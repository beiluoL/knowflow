package com.knowflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.knowflow.dto.ChatSendDTO;
import com.knowflow.entity.ChatConversation;
import com.knowflow.vo.ConversationVO;
import com.knowflow.vo.MessageVO;

import java.util.List;

public interface ChatService extends IService<ChatConversation> {

    List<ConversationVO> getConversationList(Long userId);

    List<MessageVO> getMessageList(Long conversationId, Long userId);

    MessageVO sendMessage(ChatSendDTO dto, Long userId);

    ConversationVO createConversation(Long userId, String title);

    void deleteConversation(Long conversationId, Long userId);
}
