package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.dto.ChatSendDTO;
import com.knowflow.service.ChatService;
import com.knowflow.vo.ConversationVO;
import com.knowflow.vo.MessageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "聊天接口")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

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
}
