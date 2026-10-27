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
    List<ConversationVO> getConversationList(Long userId, boolean archived);
    ConversationVO updateState(Long id, Long userId, Boolean pinned, Boolean archived);

    List<MessageVO> getMessageList(Long conversationId, Long userId);

    /** 发送用户消息并调用 AI 生成回复，返回助手消息（含相关文档引用）。 */
    MessageVO sendMessage(ChatSendDTO dto, Long userId);

    ConversationVO createConversation(Long userId, String title);

    ConversationVO renameConversation(Long id, Long userId, String title);

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

    /**
     * 编辑用户消息：校验归属后更新内容，并截断该消息之后的回答分支。
     * 前端随后通过 {@link #regenerate(Long, com.knowflow.dto.ChatRegenerateDTO, Long)} 生成新的回答。
     */
    MessageVO updateMessage(Long messageId, Long userId, String content);

    /**
     * 重新生成回答（真实调用模型，不复制历史内容）。
     * 若传入的是 assistant 消息则先删除该条及其后续分支；
     * 若传入的是 user 消息则保留该消息并删除其后续回答。
     *
     * @param messageId 触发重新生成的消息 ID（用户或助手消息）
     * @param dto       模型 / 知识库 / 引用 / 模式参数
     * @param userId    当前登录用户
     * @return SseEmitter，事件协议与 /chat/stream 一致
     */
    SseEmitter regenerate(Long messageId, com.knowflow.dto.ChatRegenerateDTO dto, Long userId);
}
