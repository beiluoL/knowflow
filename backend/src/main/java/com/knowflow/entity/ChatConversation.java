package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chat_conversation")
/** 对话会话实体，聚合某用户的多轮聊天消息。 */
public class ChatConversation extends BaseEntity {

    private Long userId;

    private String title;

    private Integer messageCount;

    private String lastMessage;
}
