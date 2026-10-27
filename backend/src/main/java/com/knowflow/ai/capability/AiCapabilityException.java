package com.knowflow.ai.capability;

import com.knowflow.exception.BusinessException;

/**
 * AI 能力调用异常：携带结构化的 {@link AiFailureKind}，供执行器判断可重试性、业务层选择降级策略、
 * 前端展示用户可理解的状态。
 *
 * <p>继承 {@link BusinessException} 以兼容现有全局异常映射（HTTP 200 包裹体 code=422），
 * 但不再允许上层用“系统错误”一言蔽之——{@link #getKind()} 是必答的失败语义。
 */
public class AiCapabilityException extends BusinessException {

    private final AiFailureKind kind;

    /** 429 Retry-After（毫秒），供应商给出时透传给退避策略；其它情况为 null。 */
    private final Long retryAfterMs;

    public AiCapabilityException(AiFailureKind kind, String detail) {
        this(kind, detail, null);
    }

    public AiCapabilityException(AiFailureKind kind, String detail, Long retryAfterMs) {
        super(422, kind.userMessage() + (detail == null || detail.isBlank() ? "" : "：" + detail));
        this.kind = kind;
        this.retryAfterMs = retryAfterMs;
    }

    public AiFailureKind getKind() {
        return kind;
    }

    public Long getRetryAfterMs() {
        return retryAfterMs;
    }

    public boolean retryable() {
        return kind.retryable();
    }
}
