package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话视图对象，返回会话标题、消息数与最近消息概要。
 */
@Data
public class ConversationVO {

    private Long id;

    private String title;

    private Integer messageCount;

    private String lastMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
