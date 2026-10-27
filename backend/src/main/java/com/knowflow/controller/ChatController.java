package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.ChatRegenerateDTO;
import com.knowflow.dto.ChatSendDTO;
import com.knowflow.exception.BusinessException;
import com.knowflow.service.AiService;
import com.knowflow.service.ChatService;
import com.knowflow.vo.ConversationVO;
import com.knowflow.vo.MessageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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
    public Result<List<ConversationVO>> conversations(@RequestParam(defaultValue = "false") boolean archived) {
        return Result.success(chatService.getConversationList(currentUserId(), archived));
    }

    @Operation(summary = "创建对话")
    @PostMapping("/conversations")
    public Result<ConversationVO> createConversation(@RequestParam(required = false) String title) {
        return Result.success(chatService.createConversation(currentUserId(), title));
    }

    @PatchMapping("/conversations/{id}")
    public Result<ConversationVO> rename(@PathVariable Long id, @Valid @RequestBody com.knowflow.dto.ChatConversationTitleDTO dto) {
        return Result.success(chatService.renameConversation(id, currentUserId(), dto.title()));
    }

    public record StateBody(Boolean pinned, Boolean archived) {}

    @PatchMapping("/conversations/{id}/state")
    public Result<ConversationVO> state(@PathVariable Long id, @RequestBody StateBody body) {
        return Result.success(chatService.updateState(id, currentUserId(), body.pinned(), body.archived()));
    }

    @Operation(summary = "删除对话")
    @DeleteMapping("/conversations/{id}")
    public Result<Void> deleteConversation(@PathVariable Long id) {
        chatService.deleteConversation(id, currentUserId());
        return Result.success();
    }

    @Operation(summary = "消息列表")
    @GetMapping("/conversations/{id}/messages")
    public Result<List<MessageVO>> messages(@PathVariable Long id) {
        return Result.success(chatService.getMessageList(id, currentUserId()));
    }

    @Operation(summary = "发送消息")
    @PostMapping("/send")
    public Result<MessageVO> send(@Valid @RequestBody ChatSendDTO dto) {
        return Result.success(chatService.sendMessage(dto, currentUserId()));
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
    public SseEmitter stream(@Valid @RequestBody ChatSendDTO dto) {
        return chatService.streamSend(dto, currentUserId());
    }

    @Operation(summary = "可用模型列表")
    @GetMapping("/models")
    public Result<List<String>> models() {
        return Result.success(aiService.getAvailableModels());
    }

    /**
     * 深度思考能力：按 provider + 模型族返回是否支持推理内容、推理摘要、工具调用。
     * 前端据此启用/禁用「深度思考」开关并说明原因，不通过模型名字符串自行猜测。
     */
    @Operation(summary = "模型深度思考能力")
    @GetMapping("/capabilities")
    public Result<com.knowflow.vo.ChatModelCapabilityVO> capabilities() {
        return Result.success(aiService.getModelCapabilities(currentUserId()));
    }

    public record UpdateMessageBody(String content) {}

    /** 消息编辑（版本分支）：更新用户消息并截断其后的回答，随后调用 regenerate 重新生成。 */
    @Operation(summary = "编辑用户消息")
    @PatchMapping("/messages/{id}")
    public Result<MessageVO> updateMessage(@PathVariable Long id, @RequestBody UpdateMessageBody body) {
        return Result.success(chatService.updateMessage(id, currentUserId(), body.content()));
    }

    /** 重新生成回答（SSE），返回真实模型输出，不复用历史回答。 */
    @Operation(summary = "重新生成回答（SSE）")
    @PostMapping(value = "/messages/{id}/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter regenerate(@PathVariable Long id, @RequestBody(required = false) ChatRegenerateDTO dto) {
        return chatService.regenerate(id, dto, currentUserId());
    }

    /**
     * P1-A：统一获取当前登录用户 ID；未登录（匿名 / token 缺失或失效）时返回 401。
     *
     * <p>复用既有 {@link SecurityUtils} 与 {@code BusinessException} 的业务码机制
     * （401 由 {@code GlobalExceptionHandler} 映射为 HTTP 401），不引入新的鉴权机制。
     *
     * <p>{@code /api/chat/stream} 因 SSE 异步分发需求被配置为 {@code permitAll}，
     * 匿名请求会真实到达控制器，因此必须在此显式区分「未登录」与「已登录」，
     * 而不能依赖安全过滤链兜底。
     */
    private static Long currentUserId() {
        Long userId = SecurityUtils.getCurrentUserIdNullable();
        if (userId == null) {
            throw new BusinessException(401, "未登录或登录已失效");
        }
        return userId;
    }
}
