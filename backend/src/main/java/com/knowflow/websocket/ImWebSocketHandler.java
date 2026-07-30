package com.knowflow.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.dto.PrivateMessageSendDTO;
import com.knowflow.entity.PrivateConversation;
import com.knowflow.mapper.PrivateConversationMapper;
import com.knowflow.service.PrivateMessageService;
import com.knowflow.vo.PrivateMessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 私聊 IM WebSocket 处理器（处理 /ws/im）
 * 单聊点对点推送，复用 WebSocketAuthInterceptor 鉴权。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImWebSocketHandler extends TextWebSocketHandler {

    private final PrivateMessageService privateMessageService;
    private final PrivateConversationMapper conversationMapper;
    // 注入 Spring 托管的 ObjectMapper（已注册 jackson-datatype-jsr310，可序列化 LocalDateTime）
    private final ObjectMapper objectMapper;

    // 用户会话映射：userId -> session（本端点内）
    private final Map<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            userSessions.put(userId, session);
            log.info("IM WebSocket 连接建立: userId={}", userId);
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
                case "message" -> handleMessage(userId, payload);
                case "read" -> handleRead(userId, payload);
                case "typing" -> handleTyping(userId, payload);
                default -> log.warn("未知 IM 消息类型: {}", type);
            }
        } catch (Exception e) {
            log.error("处理 IM WebSocket 消息失败", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            userSessions.remove(userId);
            log.info("IM WebSocket 连接关闭: userId={}, status={}", userId, status);
        }
    }

    private void handleMessage(Long userId, Map<String, Object> payload) {
        Long conversationId = Long.valueOf(payload.get("conversationId").toString());

        PrivateMessageSendDTO dto = new PrivateMessageSendDTO();
        dto.setConversationId(conversationId);
        dto.setMessageType((String) payload.getOrDefault("messageType", ""));
        dto.setContent((String) payload.get("content"));
        dto.setFileUrl((String) payload.get("fileUrl"));
        dto.setFileName((String) payload.get("fileName"));
        dto.setFileSize(payload.get("fileSize") != null ? Long.valueOf(payload.get("fileSize").toString()) : null);
        dto.setCodeLanguage((String) payload.get("codeLanguage"));

        PrivateMessageVO vo = privateMessageService.sendMessage(dto, userId);

        Long targetId = resolveTarget(conversationId, userId);
        if (targetId == null) return;

        // 推送给对方与自己（多端同步）
        sendToUser(targetId, Map.of("type", "message", "data", vo));
        sendToUser(userId, Map.of("type", "message", "data", vo));
        int unread = privateMessageService.getUnreadCount(conversationId, targetId);
        sendToUser(targetId, Map.of("type", "conversation_update", "conversationId", conversationId, "unreadCount", unread));
    }

    private void handleRead(Long userId, Map<String, Object> payload) {
        Long conversationId = Long.valueOf(payload.get("conversationId").toString());
        privateMessageService.markAsRead(conversationId, userId);

        Long targetId = resolveTarget(conversationId, userId);
        if (targetId == null) return;
        // 已读回执广播给对方与自己
        sendToUser(targetId, Map.of("type", "read_receipt", "conversationId", conversationId, "userId", userId));
        sendToUser(userId, Map.of("type", "read_receipt", "conversationId", conversationId, "userId", userId));
    }

    private void handleTyping(Long userId, Map<String, Object> payload) {
        Long conversationId = Long.valueOf(payload.get("conversationId").toString());
        boolean isTyping = Boolean.TRUE.equals(payload.get("isTyping"));
        Long targetId = resolveTarget(conversationId, userId);
        if (targetId == null) return;
        sendToUser(targetId, Map.of(
                "type", "typing",
                "conversationId", conversationId,
                "userId", userId,
                "isTyping", isTyping
        ));
    }

    /** 供 REST 发送后调用：实时推送给对方与自己 */
    public void pushToPeer(Long conversationId, Long fromUserId, PrivateMessageVO vo) {
        Long targetId = resolveTarget(conversationId, fromUserId);
        if (targetId == null) return;
        sendToUser(targetId, Map.of("type", "message", "data", vo));
        sendToUser(fromUserId, Map.of("type", "message", "data", vo));
        int unread = privateMessageService.getUnreadCount(conversationId, targetId);
        sendToUser(targetId, Map.of("type", "conversation_update", "conversationId", conversationId, "unreadCount", unread));
    }

    /** 撤回消息实时广播：通知双方该消息已撤回 */
    public void broadcastRecall(Long conversationId, Long messageId) {
        PrivateConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) return;
        Map<String, Object> evt = new HashMap<>();
        evt.put("type", "recall");
        evt.put("conversationId", conversationId);
        evt.put("messageId", messageId);
        sendToUser(conversation.getUserAId(), evt);
        sendToUser(conversation.getUserBId(), evt);
    }

    /** 已读回执实时广播：通知双方“readerId 已读到 lastReadMessageId” */
    public void broadcastReadReceipt(Long conversationId, Long readerId, Long lastReadMessageId) {
        PrivateConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) return;
        Map<String, Object> evt = new HashMap<>();
        evt.put("type", "read_receipt");
        evt.put("conversationId", conversationId);
        evt.put("userId", readerId);
        evt.put("lastReadMessageId", lastReadMessageId);
        sendToUser(conversation.getUserAId(), evt);
        sendToUser(conversation.getUserBId(), evt);
    }

    /** 根据会话与当前用户计算对方ID */
    private Long resolveTarget(Long conversationId, Long userId) {
        PrivateConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) return null;
        return conversation.getUserAId().equals(userId)
                ? conversation.getUserBId()
                : conversation.getUserAId();
    }

    public void sendToUser(Long userId, Object message) {
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                // 同一 session 的发送必须串行，避免多线程并发 send 触发
                // Tomcat 的 TEXT_PARTIAL_WRITING 竞态导致消息丢失
                synchronized (session) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
                }
            } catch (IOException e) {
                log.error("发送 IM WebSocket 消息失败: userId={}", userId, e);
            }
        }
    }
}
