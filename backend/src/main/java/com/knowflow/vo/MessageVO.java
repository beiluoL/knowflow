package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话消息视图对象，封装单条聊天的角色、内容与文档引用。
 */
@Data
public class MessageVO {

    private Long id;

    private Long conversationId;

    /** 消息角色，如 user、assistant */
    private String role;

    private String content;

    /** 引用的文档信息，通常为 JSON 字符串 */
    private String docReferences;

    private LocalDateTime createTime;
}
