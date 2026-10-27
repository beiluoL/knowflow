package com.knowflow.ai.capability;

/**
 * 一次模型调用的原始结果：内容与供应商上报的 token 用量。
 */
public record AiRawResult(String content, int totalTokens) {

    public AiRawResult(String content) {
        this(content, 0);
    }
}
