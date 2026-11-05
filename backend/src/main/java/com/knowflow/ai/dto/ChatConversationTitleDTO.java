package com.knowflow.ai.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
/**
 * 承载聊天会话重命名请求。
 */
public record ChatConversationTitleDTO(@NotBlank @Size(max=200) String title) {}
