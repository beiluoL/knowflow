package com.knowflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.common.Result;
import com.knowflow.dto.PrivateMessageSendDTO;
import com.knowflow.entity.PrivateMessage;
import com.knowflow.mapper.PrivateMessageMapper;
import com.knowflow.service.PrivateMessageService;
import com.knowflow.vo.PrivateConversationVO;
import com.knowflow.vo.PrivateMessageVO;
import com.knowflow.util.UploadHelper;
import com.knowflow.websocket.ImWebSocketHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/**
 * 私聊消息接口
 */
@Tag(name = "私聊消息")
@RestController
@RequestMapping("/api/im/private")
@RequiredArgsConstructor
public class PrivateMessageController {

    private final PrivateMessageService privateMessageService;
    private final ImWebSocketHandler imWebSocketHandler;
    private final PrivateMessageMapper messageMapper;

    @Value("${app.upload.dir:${user.home}/knowflow/uploads}")
    private String uploadDir;

    @Operation(summary = "获取或创建与用户的私聊会话")
    @PostMapping("/conversations")
    public Result<PrivateConversationVO> getOrCreateConversation(@RequestParam Long targetUserId,
                                                                Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(privateMessageService.getOrCreateConversation(userId, targetUserId));
    }

    @Operation(summary = "我的私聊会话列表")
    @GetMapping("/conversations")
    public Result<List<PrivateConversationVO>> getMyConversations(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(privateMessageService.getMyConversations(userId));
    }

    @Operation(summary = "会话消息历史")
    @GetMapping("/conversations/{id}/messages")
    public Result<Page<PrivateMessageVO>> getMessages(@PathVariable Long id,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size,
                                                      Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(privateMessageService.getMessages(id, userId, page, size));
    }

    @Operation(summary = "发送私聊消息")
    @PostMapping("/messages")
    public Result<PrivateMessageVO> sendMessage(@Valid @RequestBody PrivateMessageSendDTO dto,
                                               Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        PrivateMessageVO vo = privateMessageService.sendMessage(dto, userId);
        imWebSocketHandler.pushToPeer(dto.getConversationId(), userId, vo);
        return Result.success(vo);
    }

    @Operation(summary = "标记会话已读")
    @PostMapping("/conversations/{id}/read")
    public Result<Void> markAsRead(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Long lastReadMessageId = privateMessageService.markAsRead(id, userId);
        // 广播已读回执，让对方实时看到“已读”
        imWebSocketHandler.broadcastReadReceipt(id, userId, lastReadMessageId);
        return Result.success();
    }

    @Operation(summary = "获取会话未读消息数")
    @GetMapping("/conversations/{id}/unread")
    public Result<Integer> getUnreadCount(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(privateMessageService.getUnreadCount(id, userId));
    }

    @Operation(summary = "撤回消息")
    @DeleteMapping("/messages/{id}")
    public Result<Void> recallMessage(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        PrivateMessage message = messageMapper.selectById(id);
        privateMessageService.recallMessage(id, userId);
        // 实时广播撤回，让对方会话里的该消息立即变为“已撤回”
        if (message != null) {
            imWebSocketHandler.broadcastRecall(message.getConversationId(), id);
        }
        return Result.success();
    }

    @Operation(summary = "上传文件（图片/文件）")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file,
                                                   Authentication authentication) {
        if (file.isEmpty()) {
            return Result.error("文件不能为空");
        }

        try {
            Map<String, Object> result = UploadHelper.save(file, uploadDir);
            return Result.success(result);
        } catch (IOException e) {
            return Result.error("文件上传失败：" + e.getMessage());
        }
    }
}
