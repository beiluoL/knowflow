package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 私聊会话实体（单聊）
 * 用 user_a_id / user_b_id 存储两人，约定 user_a_id < user_b_id 保证唯一。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("private_conversation")
public class PrivateConversation extends BaseEntity {

    /** 用户A ID（较小的一方） */
    private Long userAId;

    /** 用户B ID（较大的一方） */
    private Long userBId;

    /** 最后一条消息ID */
    private Long lastMessageId;

    /** 最后消息时间 */
    private java.time.LocalDateTime lastMessageTime;
}
