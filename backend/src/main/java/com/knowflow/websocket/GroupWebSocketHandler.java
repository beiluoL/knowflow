package com.knowflow.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.dto.GroupMessageSendDTO;
import com.knowflow.service.StudyGroupService;
import com.knowflow.vo.GroupMessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 学习小组 WebSocket 处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupWebSocketHandler extends TextWebSocketHandler {

    private final StudyGroupService studyGroupService;
    // 注入 Spring 托管的 ObjectMapper（已注册 jackson-datatype-jsr310，可序列化 LocalDateTime）
    private final ObjectMapper objectMapper;

    // 用户会话映射：userId -> session
    private final Map<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    // 小组用户映射：groupId -> Set<userId>
    private final Map<Long, Set<Long>> groupUsers = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            userSessions.put(userId, session);
            log.info("WebSocket 连接建立: userId={}", userId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
            String type = (String) payload.get("type");

            switch (type) {
                case "join" -> handleJoin(userId, payload);
                case "leave" -> handleLeave(userId, payload);
                case "message" -> handleMessage(userId, payload);
                case "typing" -> handleTyping(userId, payload);
                default -> log.warn("未知消息类型: {}", type);
            }
        } catch (Exception e) {
            log.error("处理 WebSocket 消息失败", e);
            sendError(session, "消息处理失败: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            userSessions.remove(userId);
            // 从所有小组中移除
            groupUsers.forEach((groupId, users) -> users.remove(userId));
            log.info("WebSocket 连接关闭: userId={}, status={}", userId, status);
        }
    }

    private void handleJoin(Long userId, Map<String, Object> payload) {
        Long groupId = Long.valueOf(payload.get("groupId").toString());
        
        // 加入小组房间
        groupUsers.computeIfAbsent(groupId, k -> ConcurrentHashMap.newKeySet()).add(userId);
        
        // 发送加入通知
        broadcastToGroup(groupId, Map.of(
                "type", "user_joined",
                "userId", userId,
                "timestamp", System.currentTimeMillis()
        ), null);
        
        log.info("用户加入小组: userId={}, groupId={}", userId, groupId);
    }

    private void handleLeave(Long userId, Map<String, Object> payload) {
        Long groupId = Long.valueOf(payload.get("groupId").toString());
        
        Set<Long> users = groupUsers.get(groupId);
        if (users != null) {
            users.remove(userId);
        }
        
        // 发送离开通知
        broadcastToGroup(groupId, Map.of(
                "type", "user_left",
                "userId", userId,
                "timestamp", System.currentTimeMillis()
        ), null);
        
        log.info("用户离开小组: userId={}, groupId={}", userId, groupId);
    }

    private void handleMessage(Long userId, Map<String, Object> payload) {
        Long groupId = Long.valueOf(payload.get("groupId").toString());
        
        GroupMessageSendDTO dto = new GroupMessageSendDTO();
        dto.setGroupId(groupId);
        dto.setMessageType((String) payload.getOrDefault("messageType", "TEXT"));
        dto.setContent((String) payload.get("content"));
        dto.setFileUrl((String) payload.get("fileUrl"));
        dto.setFileName((String) payload.get("fileName"));
        dto.setFileSize(payload.get("fileSize") != null ? Long.valueOf(payload.get("fileSize").toString()) : null);
        dto.setCodeLanguage((String) payload.get("codeLanguage"));
        
        // 解析 @提及
        if (payload.get("mentionUserIds") != null) {
            try {
                List<?> raw = (List<?>) payload.get("mentionUserIds");
                List<Long> ids = new ArrayList<>();
                for (Object o : raw) {
                    ids.add(Long.valueOf(o.toString()));
                }
                dto.setMentionUserIds(ids);
            } catch (Exception e) {
                log.warn("解析 mentionUserIds 失败", e);
            }
        }

        try {
            GroupMessageVO messageVO = studyGroupService.sendMessage(dto, userId);
            
            // 广播消息给小组所有在线成员
            broadcastToGroup(groupId, Map.of(
                    "type", "message",
                    "data", messageVO
            ), null);
            
            // 发送 @提及 通知
            if (messageVO.getMentionUsers() != null && !messageVO.getMentionUsers().isEmpty()) {
                for (GroupMessageVO.MentionedUser mentionedUser : messageVO.getMentionUsers()) {
                    sendToUser(mentionedUser.getId(), Map.of(
                            "type", "mention",
                            "groupId", groupId,
                            "messageId", messageVO.getId(),
                            "senderName", messageVO.getSenderName(),
                            "content", messageVO.getContent()
                    ));
                }
            }
        } catch (Exception e) {
            log.error("发送消息失败", e);
            WebSocketSession session = userSessions.get(userId);
            if (session != null) {
                sendError(session, "发送消息失败: " + e.getMessage());
            }
        }
    }

    private void handleTyping(Long userId, Map<String, Object> payload) {
        Long groupId = Long.valueOf(payload.get("groupId").toString());
        boolean isTyping = Boolean.TRUE.equals(payload.get("isTyping"));
        
        // 广播正在输入状态
        broadcastToGroup(groupId, Map.of(
                "type", "typing",
                "userId", userId,
                "isTyping", isTyping
        ), userId);  // 排除自己
    }

    /**
     * 广播消息到小组所有在线成员
     */
    public void broadcastToGroup(Long groupId, Object message, Long excludeUserId) {
        Set<Long> users = groupUsers.get(groupId);
        if (users == null || users.isEmpty()) return;

        for (Long userId : users) {
            if (excludeUserId != null && userId.equals(excludeUserId)) continue;
            sendToUser(userId, message);
        }
    }

    /**
     * 撤回消息实时广播：通知小组所有在线成员该消息已撤回
     */
    public void broadcastRecall(Long groupId, Long messageId) {
        broadcastToGroup(groupId, Map.of(
                "type", "recall",
                "groupId", groupId,
                "messageId", messageId
        ), null);
    }

    /**
     * 已读回执实时广播：通知小组成员"readerId 已读到 lastReadMessageId"
     */
    public void broadcastReadReceipt(Long groupId, Long userId, Long lastReadMessageId) {
        broadcastToGroup(groupId, Map.of(
                "type", "read_receipt",
                "groupId", groupId,
                "userId", userId,
                "lastReadMessageId", lastReadMessageId
        ), null);
    }

    /**
     * 发送消息给指定用户
     */
    public void sendToUser(Long userId, Object message) {
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(message);
                // 同一 session 的发送必须串行，避免多线程并发 send 触发
                // Tomcat 的 TEXT_PARTIAL_WRITING 竞态导致消息丢失
                synchronized (session) {
                    session.sendMessage(new TextMessage(json));
                }
            } catch (IOException e) {
                log.error("发送 WebSocket 消息失败: userId={}", userId, e);
            }
        }
    }

    private void sendError(WebSocketSession session, String error) {
        try {
            Map<String, Object> errorMsg = Map.of(
                    "type", "error",
                    "message", error,
                    "timestamp", System.currentTimeMillis()
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorMsg)));
        } catch (IOException e) {
            log.error("发送错误消息失败", e);
        }
    }
}