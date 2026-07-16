package com.zhishiku.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chat_message")
public class ChatMessage extends BaseEntity {

    private Long conversationId;

    private Long userId;

    private String role;

    private String content;

    private String docReferences;

    private Integer tokenCount;
}
