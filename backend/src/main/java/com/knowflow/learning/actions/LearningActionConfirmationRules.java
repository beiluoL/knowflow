package com.knowflow.learning.actions;

import com.knowflow.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Supplier;

/** Validates confirmation credentials in version, token, then idempotency-key order. */
final class LearningActionConfirmationRules {
    private LearningActionConfirmationRules() {}

    /** Token generation stays lazy so stale versions fail before token validation. */
    static void validate(PlanModels.ConfirmRequest request, Integer version, Supplier<String> token) {
        if (request == null
                || request.getVersion() == null
                || !request.getVersion().equals(version))
            throw new BusinessException(409, "草案版本已变化");
        if (request.getConfirmationToken() == null
                || !MessageDigest.isEqual(
                        token.get().getBytes(StandardCharsets.UTF_8),
                        request.getConfirmationToken().getBytes(StandardCharsets.UTF_8)))
            throw new BusinessException(403, "缺少有效确认令牌");
        if (request.getIdempotencyKey() == null
                || !request.getIdempotencyKey().matches("[A-Za-z0-9_-]{8,80}"))
            throw new BusinessException(400, "无效幂等键");
    }
}
