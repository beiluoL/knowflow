package com.knowflow.ai.capability;

/**
 * AI 能力对外状态（Phase 3H 统一能力状态）。
 *
 * <p>面向前端与业务层的七态模型，不暴露 Key、堆栈与供应商内部错误细节。
 */
public enum AiCapabilityStatus {

    /** 配置正常且近期调用成功（或尚未有失败证据）。 */
    AVAILABLE,
    /** 没有任何有效模型配置（系统级与用户级均缺 Key）。 */
    UNCONFIGURED,
    /** 部分能力降级：近期出现可恢复失败（超时/5xx/限流恢复后），仍可尝试。 */
    DEGRADED,
    /** 正处于供应商限流冷却窗口。 */
    RATE_LIMITED,
    /** 最近一次失败为超时（冷却窗口内）。 */
    TIMEOUT,
    /** 熔断打开或出现不可恢复故障，当前不可用。 */
    UNAVAILABLE,
    /** 被用户或系统显式关闭。 */
    DISABLED
}
