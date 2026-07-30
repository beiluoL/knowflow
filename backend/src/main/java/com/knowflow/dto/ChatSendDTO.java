package com.knowflow.dto;

import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

import java.util.List;

/**
 * 聊天发送请求参数，封装会话标识与用户消息内容（可选图片）。
 */
@Data
public class ChatSendDTO {

    /** 会话ID，为空时表示发起新会话 */
    private Long conversationId;

    /** 用户消息文本（与 images 至少提供一个） */
    private String content;

    /** 可选模型标识，为 null 时回退到服务配置默认模型（用于对话多模型切换）。 */
    private String model;

    /** 图片 base64 列表（不含 data:image/...;base64, 前缀，前端直接传 raw base64）。 */
    private List<String> images;

    /** 自定义校验：至少提供 content 或 images 之一 */
    @AssertTrue(message = "消息内容或图片不能同时为空")
    public boolean isValid() {
        return (content != null && !content.isBlank()) || (images != null && !images.isEmpty());
    }
}
