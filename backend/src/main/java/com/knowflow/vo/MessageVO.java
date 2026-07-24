package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageVO {

    private Long id;

    private Long conversationId;

    private String role;

    private String content;

    private String docReferences;

    private LocalDateTime createTime;
}
