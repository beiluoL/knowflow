package com.knowflow.learning.service.actions;

import com.knowflow.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Supplier;

import com.knowflow.learning.actions.PlanModels;
/** 按版本、令牌、幂等键的顺序校验确认凭据。 */
final class LearningActionConfirmationRules {
    private LearningActionConfirmationRules() {}

    /** 延迟生成令牌，使过期版本在令牌校验前失败。 */
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
