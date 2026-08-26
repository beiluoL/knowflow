package com.knowflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.knowflow.dto.ChatSendDTO;
import com.knowflow.entity.ChatConversation;
import com.knowflow.vo.ConversationVO;
import com.knowflow.vo.MessageVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/** 聊天业务服务接口。 */
public interface ChatService extends IService<ChatConversation> {

    List<ConversationVO> getConversationList(Long userId);

    List<MessageVO> getMessageList(Long conversationId, Long userId);

    /** 发送用户消息并调用 AI 生成回复，返回助手消息（含相关文档引用）。 */
    MessageVO sendMessage(ChatSendDTO dto, Long userId);

    ConversationVO createConversation(Long userId, String title);

    void deleteConversation(Long conversationId, Long userId);

    /**
     * F1：流式发送消息，通过 SSE 逐 token 推送 AI 回复。
     * 内部完成：保存用户消息 → 拼 RAG+summary 上下文 → 调 AiService.streamChat →
     * 流结束时保存 assistant 消息 + 更新 conversation + 可能更新 summary。
     *
     * @param dto    用户消息（content 必填，conversationId 可空表示新建会话）
     * @param userId 用户 ID
     * @return SseEmitter，由 Controller 直接返回给前端
     */
    SseEmitter streamSend(ChatSendDTO dto, Long userId);
}
