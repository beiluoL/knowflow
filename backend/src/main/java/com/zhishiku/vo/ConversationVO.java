package com.zhishiku.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationVO {

    private Long id;

    private String title;

    private Integer messageCount;

    private String lastMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
