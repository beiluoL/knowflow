package com.zhishiku.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhishiku.dto.ChatSendDTO;
import com.zhishiku.entity.ChatConversation;
import com.zhishiku.vo.ConversationVO;
import com.zhishiku.vo.MessageVO;

import java.util.List;

public interface ChatService extends IService<ChatConversation> {

    List<ConversationVO> getConversationList(Long userId);

    List<MessageVO> getMessageList(Long conversationId, Long userId);

    MessageVO sendMessage(ChatSendDTO dto, Long userId);

    ConversationVO createConversation(Long userId, String title);

    void deleteConversation(Long conversationId, Long userId);
}
