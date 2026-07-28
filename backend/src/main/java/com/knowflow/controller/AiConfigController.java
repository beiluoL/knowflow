package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.config.AiConfig;
import com.knowflow.dto.UserAiConfigDTO;
import com.knowflow.entity.UserAiConfig;
import com.knowflow.mapper.UserAiConfigMapper;
import com.knowflow.vo.PlatformModelVO;
import com.knowflow.vo.UserAiConfigVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "AI 配置接口")
@RestController
@RequestMapping("/api/ai-config")
@RequiredArgsConstructor
public class AiConfigController {

    private final UserAiConfigMapper userAiConfigMapper;
    private final AiConfig aiConfig;

    @Operation(summary = "获取当前用户的 AI 配置")
    @GetMapping
    public Result<UserAiConfigVO> getConfig(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        LambdaQueryWrapper<UserAiConfig> wrapper = new LambdaQueryWrapper<UserAiConfig>()
                .eq(UserAiConfig::getUserId, userId)
                .eq(UserAiConfig::getIsActive, 1)
                .last("LIMIT 1");
        UserAiConfig config = userAiConfigMapper.selectOne(wrapper);
        if (config == null) {
            return Result.success(null);
        }
        UserAiConfigVO vo = new UserAiConfigVO();
        vo.setId(config.getId());
        vo.setProvider(config.getProvider());
        vo.setApiKeyMasked(maskKey(config.getApiKey()));
        vo.setBaseUrl(config.getBaseUrl());
        vo.setModel(config.getModel());
        vo.setIsActive(config.getIsActive());
        return Result.success(vo);
    }

    @Operation(summary = "保存当前用户的 AI 配置")
    @PostMapping
    public Result<UserAiConfigVO> saveConfig(@Valid @RequestBody UserAiConfigDTO dto,
                                              Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        // 查找已有配置
        LambdaQueryWrapper<UserAiConfig> wrapper = new LambdaQueryWrapper<UserAiConfig>()
                .eq(UserAiConfig::getUserId, userId);
        UserAiConfig existing = userAiConfigMapper.selectOne(wrapper);

        if (existing != null) {
            existing.setProvider(dto.getProvider());
            // 只在前端传了新 key 时更新（避免覆盖为脱敏值）
            if (dto.getApiKey() != null && !dto.getApiKey().contains("****")) {
                existing.setApiKey(dto.getApiKey());
            }
            if (dto.getBaseUrl() != null) existing.setBaseUrl(dto.getBaseUrl());
            if (dto.getModel() != null) existing.setModel(dto.getModel());
            if (dto.getIsActive() != null) existing.setIsActive(dto.getIsActive());
            userAiConfigMapper.updateById(existing);
        } else {
            UserAiConfig newConfig = new UserAiConfig();
            newConfig.setUserId(userId);
            newConfig.setProvider(dto.getProvider());
            newConfig.setApiKey(dto.getApiKey());
            newConfig.setBaseUrl(dto.getBaseUrl());
            newConfig.setModel(dto.getModel());
            newConfig.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : 1);
            userAiConfigMapper.insert(newConfig);
        }

        // 返回脱敏结果
        return getConfig(authentication);
    }

    @Operation(summary = "删除当前用户的 AI 配置（切换回平台模型）")
    @DeleteMapping
    public Result<Void> deleteConfig(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        LambdaQueryWrapper<UserAiConfig> wrapper = new LambdaQueryWrapper<UserAiConfig>()
                .eq(UserAiConfig::getUserId, userId);
        userAiConfigMapper.delete(wrapper);
        return Result.success();
    }

    @Operation(summary = "获取平台提供的模型列表")
    @GetMapping("/platform-models")
    public Result<List<PlatformModelVO>> platformModels() {
        List<PlatformModelVO> list = new ArrayList<>();

        // DeepSeek
        PlatformModelVO deepseek = new PlatformModelVO();
        deepseek.setProvider("deepseek");
        deepseek.setLabel("DeepSeek");
        deepseek.setBaseUrl("https://api.deepseek.com/v1");
        deepseek.setModel("deepseek-chat");
        deepseek.setSubscriptionRequired(false);
        deepseek.setPriceInfo("免费体验额度，超出需订阅");
        list.add(deepseek);

        // 硅基流动
        PlatformModelVO siliconflow = new PlatformModelVO();
        siliconflow.setProvider("siliconflow");
        siliconflow.setLabel("硅基流动 (SiliconFlow)");
        siliconflow.setBaseUrl("https://api.siliconflow.cn/v1");
        siliconflow.setModel("Qwen/Qwen2.5-7B-Instruct");
        siliconflow.setSubscriptionRequired(false);
        siliconflow.setPriceInfo("免费额度，超出需订阅");
        list.add(siliconflow);

        // OpenAI
        PlatformModelVO openai = new PlatformModelVO();
        openai.setProvider("openai");
        openai.setLabel("OpenAI");
        openai.setBaseUrl("https://api.openai.com/v1");
        openai.setModel("gpt-4o");
        openai.setSubscriptionRequired(true);
        openai.setPriceInfo("需要订阅，按量计费");
        list.add(openai);

        // 通义千问
        PlatformModelVO qwen = new PlatformModelVO();
        qwen.setProvider("qwen");
        qwen.setLabel("通义千问");
        qwen.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        qwen.setModel("qwen-plus");
        qwen.setSubscriptionRequired(false);
        qwen.setPriceInfo("免费额度，超出需订阅");
        list.add(qwen);

        return Result.success(list);
    }

    /** 脱敏：保留前4后4位 */
    private String maskKey(String key) {
        if (key == null || key.length() <= 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
