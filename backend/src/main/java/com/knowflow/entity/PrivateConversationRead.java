package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 私聊已读游标实体
 * 每个用户在每个会话里一条记录，记录最后已读的消息ID。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("private_conversation_read")
public class PrivateConversationRead extends BaseEntity {

    /** 会话ID */
    private Long conversationId;

    /** 用户ID */
    private Long userId;

    /** 最后已读消息ID */
    private Long lastReadMessageId;
}
