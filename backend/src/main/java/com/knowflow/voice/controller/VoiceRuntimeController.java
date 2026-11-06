package com.knowflow.voice.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.exception.BusinessException;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.knowflow.voice.service.VoiceCapabilityService;
import com.knowflow.voice.service.VoiceRuntimeSettingsService;
import com.knowflow.voice.model.VoiceRuntimeSetting;
/** 语音运行时设置与能力。读取需登录；切换模式需 ADMIN（影响整机所有语音会话）。 */
@RestController
@RequestMapping("/api/voice/runtime")
public class VoiceRuntimeController {

    private final VoiceCapabilityService capabilities;
    private final VoiceRuntimeSettingsService settings;

    public VoiceRuntimeController(VoiceCapabilityService capabilities, VoiceRuntimeSettingsService settings) {
        this.capabilities = capabilities;
        this.settings = settings;
    }

    @GetMapping("/status")
    public Result<?> status() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(capabilities.snapshot(userId));
    }

    public record ModeRequest(String mode, Boolean allowRemoteFallback) { }

    @PutMapping("/mode")
    public Result<?> updateMode(@RequestBody ModeRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (!SecurityUtils.isAdmin()) throw new BusinessException(403, "只有管理员可以切换语音运行模式");
        if (request == null) throw new BusinessException(400, "缺少运行模式");
        VoiceRuntimeSetting updated = settings.update(userId, request.mode(), request.allowRemoteFallback());
        return Result.success(Map.of(
                "mode", updated.mode().name(),
                "allowRemoteFallback", updated.allowRemoteFallback(),
                "leavesLocal", updated.leavesLocal()));
    }
}
