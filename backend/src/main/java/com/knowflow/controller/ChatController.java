package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.dto.ChatSendDTO;
import com.knowflow.service.AiService;
import com.knowflow.service.ChatService;
import com.knowflow.vo.ConversationVO;
import com.knowflow.vo.MessageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/** 聊天会话 REST 接口，提供对话管理、消息查询与 AI 消息收发能力。 */
@Tag(name = "聊天接口")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    private final AiService aiService;

    @Operation(summary = "对话列表")
    @GetMapping("/conversations")
    public Result<List<ConversationVO>> conversations(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(chatService.getConversationList(userId));
    }

    @Operation(summary = "创建对话")
    @PostMapping("/conversations")
    public Result<ConversationVO> createConversation(@RequestParam(required = false) String title,
                                                      Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(chatService.createConversation(userId, title));
    }

    @Operation(summary = "删除对话")
    @DeleteMapping("/conversations/{id}")
    public Result<Void> deleteConversation(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        chatService.deleteConversation(id, userId);
        return Result.success();
    }

    @Operation(summary = "消息列表")
    @GetMapping("/conversations/{id}/messages")
    public Result<List<MessageVO>> messages(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(chatService.getMessageList(id, userId));
    }

    @Operation(summary = "发送消息")
    @PostMapping("/send")
    public Result<MessageVO> send(@Valid @RequestBody ChatSendDTO dto, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(chatService.sendMessage(dto, userId));
    }

    /**
     * F1：流式发送消息（SSE），逐 token 推送 AI 回复。
     * <p>
     * 推送事件：
     * <ul>
     *   <li>{@code delta}：{ content: "token" }</li>
     *   <li>{@code done}：{ content: "完整文本" }</li>
     *   <li>{@code error}：{ error: "错误信息" }</li>
     * </ul>
     * 注意：Nginx 反代需配置 {@code proxy_buffering off;} 否则流式会退化为整段返回。
     */
    @Operation(summary = "流式发送消息（SSE）")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatSendDTO dto, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return chatService.streamSend(dto, userId);
    }

    @Operation(summary = "可用模型列表")
    @GetMapping("/models")
    public Result<List<String>> models() {
        return Result.success(aiService.getAvailableModels());
    }
}
