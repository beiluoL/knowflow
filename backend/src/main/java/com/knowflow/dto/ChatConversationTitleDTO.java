package com.knowflow.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record ChatConversationTitleDTO(@NotBlank @Size(max=200) String title) {}
