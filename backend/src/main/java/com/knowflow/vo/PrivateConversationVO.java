package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 私聊会话视图对象（面向当前用户，含对方信息）
 */
@Data
public class PrivateConversationVO {

    private Long id;

    /** 对方用户ID */
    private Long targetUserId;

    /** 对方昵称 */
    private String targetUserName;

    /** 对方头像 */
    private String targetUserAvatar;

    /** 最后一条消息ID */
    private Long lastMessageId;

    /** 最后一条消息内容（用于列表预览） */
    private String lastMessageContent;

    /** 最后一条消息类型 */
    private String lastMessageType;

    /** 最后消息时间 */
    private LocalDateTime lastMessageTime;

    /** 当前用户在此会话的未读消息数 */
    private Integer unreadCount;

    private LocalDateTime createTime;
}
