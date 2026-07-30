package com.knowflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 编程助手内联响应（SC1-AI-01）。
 * {@code configured=false} 表示服务端未配置可用的大模型密钥，前端据此给出引导提示而非报错。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeAssistResult {
    /** 服务端是否存在可用 AI 配置（全局或用户级） */
    private boolean configured;

    /** AI 生成的解释 / 建议文本；未配置时为引导说明 */
    private String answer;
}
