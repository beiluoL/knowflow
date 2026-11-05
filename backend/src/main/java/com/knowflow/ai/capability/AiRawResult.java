package com.knowflow.ai.capability;

/** 模型结果及用量来源，未上报和预留估算不能用于成本核算。 */
public record AiRawResult(String content, int totalTokens, UsageSource usageSource, Long inputTokens, Long outputTokens) {
    public enum UsageSource { REPORTED, ESTIMATED, UNREPORTED }

    public AiRawResult(String content, int totalTokens, UsageSource usageSource) {
        this(content, totalTokens, usageSource, null, null);
    }

    public AiRawResult(String content, int totalTokens) {
        this(content, Math.max(0, totalTokens),
                totalTokens > 0 ? UsageSource.REPORTED : UsageSource.UNREPORTED);
    }

    public AiRawResult(String content) {
        this(content, 0);
    }
}
