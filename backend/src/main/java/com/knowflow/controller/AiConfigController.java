package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.config.AiConfig;
import com.knowflow.config.AiProviderRegistry;
import com.knowflow.dto.UserAiConfigDTO;
import com.knowflow.entity.UserAiConfig;
import com.knowflow.mapper.UserAiConfigMapper;
import com.knowflow.vo.PlatformModelVO;
import com.knowflow.vo.UserAiConfigVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "AI 配置接口")
@RestController
@RequestMapping("/api/ai-config")
@RequiredArgsConstructor
/**
 * AI 模型配置接口。
 * <p>
 * 兼容两种使用场景：
 * <ul>
 *   <li>通用 Chat：通过 {@code isActive=1} 标记唯一激活配置，{@link #getConfig} 返回该条</li>
 *   <li>编程 Agent：通过 {@link #listConfigs} 返回用户全部配置，前端自由切换</li>
 * </ul>
 * 提供商元信息统一来自 {@link AiProviderRegistry}（11 云端 + 4 本地）。
 */
public class AiConfigController {

    private final UserAiConfigMapper userAiConfigMapper;
    private final AiConfig aiConfig;
    private final AiProviderRegistry providerRegistry;

    @Operation(summary = "获取当前用户的激活配置（兼容通用 Chat）")
    @GetMapping
    public Result<UserAiConfigVO> getConfig(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        UserAiConfig config = findActive(userId);
        if (config == null) {
            return Result.success(null);
        }
        return Result.success(toVO(config));
    }

    @Operation(summary = "列出当前用户的全部模型配置（编程 Agent 用）")
    @GetMapping("/list")
    public Result<List<UserAiConfigVO>> listConfigs(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<UserAiConfig> list = userAiConfigMapper.selectList(
                new LambdaQueryWrapper<UserAiConfig>()
                        .eq(UserAiConfig::getUserId, userId)
                        .orderByDesc(UserAiConfig::getIsActive)
                        .orderByDesc(UserAiConfig::getCreateTime));
        List<UserAiConfigVO> vos = list.stream().map(this::toVO).collect(Collectors.toList());
        return Result.success(vos);
    }

    @Operation(summary = "保存（新增或更新）一条模型配置")
    @PostMapping
    public Result<UserAiConfigVO> saveConfig(@Valid @RequestBody UserAiConfigDTO dto,
                                              Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        // 校验 provider 是否合法
        AiProviderRegistry.ProviderInfo info = providerRegistry.find(dto.getProvider());
        if (info == null) {
            return Result.error("未知的模型提供商：" + dto.getProvider());
        }

        // 本地模型 apiKey 约定填 "local"
        boolean isLocal = info.getType() == AiProviderRegistry.ProviderType.LOCAL;
        String apiKey = isLocal ? "local" : dto.getApiKey();

        if (dto.getId() != null) {
            // 编辑现有
            UserAiConfig existing = userAiConfigMapper.selectById(dto.getId());
            if (existing == null || !existing.getUserId().equals(userId)) {
                return Result.error("配置不存在或无权操作");
            }
            existing.setProvider(dto.getProvider());
            // 只在前端传了新 key 时更新（避免覆盖为脱敏值）；本地模型固定 "local"
            if (isLocal) {
                existing.setApiKey("local");
            } else if (apiKey != null && !apiKey.contains("****")) {
                existing.setApiKey(apiKey);
            }
            if (dto.getBaseUrl() != null) existing.setBaseUrl(dto.getBaseUrl());
            if (dto.getModel() != null) existing.setModel(dto.getModel());
            existing.setProviderType(info.getType().name());
            existing.setCapability(dto.getCapability() != null ? dto.getCapability() : info.getCapability().name());
            existing.setDisplayName(dto.getDisplayName());
            if (dto.getIsActive() != null) existing.setIsActive(dto.getIsActive());
            userAiConfigMapper.updateById(existing);
            // 若设为 active，取消其他配置的 active
            if (Integer.valueOf(1).equals(dto.getIsActive())) {
                clearOtherActive(userId, dto.getId());
            }
        } else {
            // 新增
            if (apiKey == null || apiKey.isBlank()) {
                return Result.error("API Key 不能为空");
            }
            UserAiConfig newConfig = new UserAiConfig();
            newConfig.setUserId(userId);
            newConfig.setProvider(dto.getProvider());
            newConfig.setApiKey(apiKey);
            newConfig.setBaseUrl(dto.getBaseUrl());
            newConfig.setModel(dto.getModel());
            newConfig.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : 0);
            newConfig.setProviderType(info.getType().name());
            newConfig.setCapability(dto.getCapability() != null ? dto.getCapability() : info.getCapability().name());
            newConfig.setDisplayName(dto.getDisplayName());
            userAiConfigMapper.insert(newConfig);
            if (Integer.valueOf(1).equals(newConfig.getIsActive())) {
                clearOtherActive(userId, newConfig.getId());
            }
        }
        return Result.success(toVO(findActive(userId)));
    }

    @Operation(summary = "删除一条模型配置")
    @DeleteMapping("/{id}")
    public Result<Void> deleteConfig(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        UserAiConfig existing = userAiConfigMapper.selectById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return Result.error("配置不存在或无权操作");
        }
        userAiConfigMapper.deleteById(id);
        return Result.success();
    }

    @Operation(summary = "设置某条配置为激活（通用 Chat 使用）")
    @PostMapping("/{id}/activate")
    public Result<Void> activate(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        UserAiConfig existing = userAiConfigMapper.selectById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return Result.error("配置不存在或无权操作");
        }
        clearOtherActive(userId, id);
        userAiConfigMapper.update(null, new LambdaUpdateWrapper<UserAiConfig>()
                .eq(UserAiConfig::getId, id)
                .set(UserAiConfig::getIsActive, 1));
        return Result.success();
    }

    @Operation(summary = "删除当前用户的全部配置（切换回平台模型）")
    @DeleteMapping
    public Result<Void> deleteAll(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        userAiConfigMapper.delete(new LambdaQueryWrapper<UserAiConfig>()
                .eq(UserAiConfig::getUserId, userId));
        return Result.success();
    }

    @Operation(summary = "获取平台提供的全部模型列表（云端 + 本地）")
    @GetMapping("/platform-models")
    public Result<List<PlatformModelVO>> platformModels() {
        List<PlatformModelVO> list = new ArrayList<>();
        for (AiProviderRegistry.ProviderInfo info : providerRegistry.all()) {
            PlatformModelVO vo = new PlatformModelVO();
            vo.setProvider(info.getProvider());
            vo.setLabel(info.getLabel());
            vo.setBaseUrl(info.getBaseUrl());
            vo.setModel(info.getDefaultModel());
            vo.setDefaultModel(info.getDefaultModel());
            vo.setSubscriptionRequired(info.isSubscriptionRequired());
            vo.setPriceInfo(info.getPriceInfo());
            vo.setProviderType(info.getType().name());
            vo.setCapability(info.getCapability().name());
            vo.setWebsiteUrl(info.getWebsiteUrl());
            vo.setKeyGuide(info.getKeyGuide());
            vo.setPopularModels(info.getPopularModels());
            list.add(vo);
        }
        return Result.success(list);
    }

    @Operation(summary = "测试 AI 配置连通性")
    @PostMapping("/test")
    public Result<Map<String, Object>> testConnection(@RequestBody Map<String, String> body,
                                                       Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        String provider = body.get("provider");
        String apiKey = body.get("apiKey");
        String baseUrl = body.get("baseUrl");
        String model = body.get("model");

        // 优先用前端传入的 apiKey；若为 **** 脱敏值则从数据库取真实 key
        if (apiKey == null || apiKey.contains("****") || apiKey.isBlank()) {
            UserAiConfig cfg = findActive(userId);
            if (cfg != null && provider != null && provider.equals(cfg.getProvider())) {
                apiKey = cfg.getApiKey();
            } else if (cfg != null) {
                apiKey = cfg.getApiKey();
            }
        }
        // 本地模型固定 key
        AiProviderRegistry.ProviderInfo info = providerRegistry.find(provider);
        if (info != null && info.getType() == AiProviderRegistry.ProviderType.LOCAL) {
            apiKey = "local";
        }

        // 默认值回退
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = info != null ? info.getBaseUrl() : "";
        }
        if (model == null || model.isBlank()) {
            model = info != null ? info.getDefaultModel() : "";
        }

        if (apiKey == null || apiKey.isBlank()) {
            Map<String, Object> r = new HashMap<>();
            r.put("success", false);
            r.put("message", "API Key 为空，请先保存配置或输入 Key");
            r.put("elapsedMs", 0);
            return Result.success(r);
        }

        String url = baseUrl.endsWith("/v1") ? baseUrl + "/chat/completions" : baseUrl + "/chat/completions";

        Map<String, Object> reqBody = new HashMap<>();
        reqBody.put("model", model);
        reqBody.put("messages", new Object[]{
                Map.of("role", "user", "content", "Hi")
        });
        reqBody.put("max_tokens", 5);

        long start = System.currentTimeMillis();
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(reqBody)))
                    .build();

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            long elapsed = System.currentTimeMillis() - start;
            Map<String, Object> r = new HashMap<>();
            r.put("elapsedMs", elapsed);

            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                r.put("success", true);
                r.put("message", "连通成功，模型响应正常");
                try {
                    com.fasterxml.jackson.databind.JsonNode node = om.readTree(resp.body());
                    if (node.has("choices")) {
                        String content = node.path("choices").path(0).path("message").path("content").asText("");
                        if (!content.isEmpty()) r.put("reply", content);
                    }
                } catch (Exception ignored) {}
            } else {
                r.put("success", false);
                r.put("message", "HTTP " + resp.statusCode() + "：" + resp.body());
            }
            return Result.success(r);
        } catch (java.net.ConnectException e) {
            Map<String, Object> r = new HashMap<>();
            r.put("success", false);
            r.put("message", "连接失败：" + e.getMessage() + "（请检查接口地址是否正确）");
            r.put("elapsedMs", System.currentTimeMillis() - start);
            return Result.success(r);
        } catch (java.net.SocketTimeoutException e) {
            Map<String, Object> r = new HashMap<>();
            r.put("success", false);
            r.put("message", "请求超时：" + e.getMessage() + "（请检查网络或模型是否可用）");
            r.put("elapsedMs", System.currentTimeMillis() - start);
            return Result.success(r);
        } catch (Exception e) {
            Map<String, Object> r = new HashMap<>();
            r.put("success", false);
            r.put("message", "异常：" + e.getMessage());
            r.put("elapsedMs", System.currentTimeMillis() - start);
            return Result.success(r);
        }
    }

    // ==================== 内部工具 ====================

    private UserAiConfig findActive(Long userId) {
        return userAiConfigMapper.selectOne(new LambdaQueryWrapper<UserAiConfig>()
                .eq(UserAiConfig::getUserId, userId)
                .eq(UserAiConfig::getIsActive, 1)
                .last("LIMIT 1"));
    }

    /** 取消其他配置的 active 标记，确保同时只有一条激活。 */
    private void clearOtherActive(Long userId, Long keepId) {
        userAiConfigMapper.update(null, new LambdaUpdateWrapper<UserAiConfig>()
                .eq(UserAiConfig::getUserId, userId)
                .ne(UserAiConfig::getId, keepId)
                .set(UserAiConfig::getIsActive, 0));
    }

    private UserAiConfigVO toVO(UserAiConfig config) {
        if (config == null) return null;
        UserAiConfigVO vo = new UserAiConfigVO();
        vo.setId(config.getId());
        vo.setProvider(config.getProvider());
        vo.setApiKeyMasked(maskKey(config.getApiKey()));
        vo.setBaseUrl(config.getBaseUrl());
        vo.setModel(config.getModel());
        vo.setIsActive(config.getIsActive());
        vo.setProviderType(config.getProviderType());
        vo.setCapability(config.getCapability());
        vo.setDisplayName(config.getDisplayName());
        // 回填 Registry 中的元信息
        AiProviderRegistry.ProviderInfo info = providerRegistry.find(config.getProvider());
        if (info != null) {
            vo.setProviderLabel(info.getLabel());
            vo.setIsLocal(info.getType() == AiProviderRegistry.ProviderType.LOCAL);
            if (vo.getProviderType() == null) vo.setProviderType(info.getType().name());
            if (vo.getCapability() == null) vo.setCapability(info.getCapability().name());
        }
        return vo;
    }

    /** 脱敏：保留前4后4位；本地模型显示 "local"。 */
    private String maskKey(String key) {
        if (key == null) return null;
        if ("local".equals(key)) return "local";
        if (key.length() <= 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
