package com.knowflow.ai.capability;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 能力状态查询接口（Phase 3H）。
 *
 * <p>前端所有 AI 区域在渲染前可查询本状态决定“AI 模式 / 基础模式”。本接口：
 * <ul>
 *   <li>不调用付费模型（无健康检查式真实请求），状态由配置 + 近期调用结果聚合；</li>
 *   <li>带秒级缓存，避免频繁查询数据库；</li>
 *   <li>不泄露 Key / baseUrl / 堆栈。</li>
 * </ul>
 */
@Tag(name = "AI 能力状态")
@RestController
@RequestMapping("/api/ai-capability")
@RequiredArgsConstructor
public class AiCapabilityController {

    private final AiService aiService;
    private final AiCapabilityStateService stateService;
    private final AiCapabilityProperties properties;

    private record Cached(AiCapabilityVO vo, long expireAt) {
    }

    private final ConcurrentHashMap<Long, Cached> cache = new ConcurrentHashMap<>();

    @Operation(summary = "当前用户的 AI 能力状态（缓存、不触发模型调用）")
    @GetMapping("/status")
    public Result<AiCapabilityVO> status() {
        Long userId = SecurityUtils.getCurrentUserId();
        long now = System.currentTimeMillis();
        Cached cached = cache.get(userId == null ? 0L : userId);
        if (cached != null && cached.expireAt() > now) {
            return Result.success(cached.vo());
        }
        AiCapabilityVO vo = build(userId);
        cache.put(userId == null ? 0L : userId,
                new Cached(vo, now + (long) Math.max(1, properties.getStatusCacheSeconds()) * 1000));
        return Result.success(vo);
    }

    private AiCapabilityVO build(Long userId) {
        boolean enabled = properties.isEnabled();
        boolean configured = enabled && (userId == null ? aiService.isConfigured() : aiService.isConfigured(userId));
        String scope = userId == null ? null : aiService.currentScope(userId);
        AiCapabilityStatus status = stateService.currentStatus(configured, false, scope);

        AiCapabilityVO vo = new AiCapabilityVO();
        vo.setEnabled(enabled);
        vo.setConfigured(configured);
        vo.setStatus(status.name());
        if (scope != null) {
            int idx = scope.indexOf(':');
            vo.setProvider(idx > 0 ? scope.substring(0, idx) : scope);
        }
        vo.setRecentFailureKind(scope == null ? null : stateService.recentFailureKind(scope));
        vo.setUseBasicMode(status == AiCapabilityStatus.UNCONFIGURED
                || status == AiCapabilityStatus.DISABLED
                || status == AiCapabilityStatus.UNAVAILABLE);
        return vo;
    }
}
