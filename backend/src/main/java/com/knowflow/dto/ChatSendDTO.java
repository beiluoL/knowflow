package com.knowflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 聊天发送请求参数，封装会话标识与用户消息内容。
 */
@Data
public class ChatSendDTO {

    /** 会话ID，为空时表示发起新会话 */
    private Long conversationId;

    @NotBlank(message = "消息内容不能为空")
    private String content;
}
