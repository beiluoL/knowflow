package com.knowflow.identity.controller;

import com.knowflow.common.Result;
import com.knowflow.identity.service.UserBackgroundPresetService;
import com.knowflow.identity.dto.BackgroundPresetSaveDTO;
import com.knowflow.identity.entity.UserBackgroundPreset;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户自定义背景预设接口。
 * 用户可将当前背景配置（纯色/渐变/内置预设）保存为自定义预设，并管理（查询/删除）。
 */
@Tag(name = "用户自定义背景预设接口")
@RestController
@RequestMapping("/api/settings/background-presets")
@RequiredArgsConstructor
public class UserBackgroundPresetController {

    private final UserBackgroundPresetService presetService;

    @Operation(summary = "获取当前用户的所有自定义预设")
    @GetMapping
    public Result<List<UserBackgroundPreset>> list(Authentication authentication) {
        return Result.success(presetService.list());
    }

    @Operation(summary = "保存自定义预设")
    @PostMapping
    public Result<UserBackgroundPreset> save(@RequestBody BackgroundPresetSaveDTO dto, Authentication authentication) {
        return Result.success(presetService.save(dto));
    }

    @Operation(summary = "删除自定义预设")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, Authentication authentication) {
        presetService.delete(id);
        return Result.success(null);
    }
}
