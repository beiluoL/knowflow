package com.knowflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.common.Result;
import com.knowflow.entity.UserBackgroundPreset;
import com.knowflow.mapper.UserBackgroundPresetMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.Data;
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

    private final UserBackgroundPresetMapper presetMapper;

    @Data
    public static class PresetSaveDTO {
        private String name;
        private String bgType;
        private String bgValue;
        private String thumbnail;
    }

    @Operation(summary = "获取当前用户的所有自定义预设")
    @GetMapping
    public Result<List<UserBackgroundPreset>> list(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<UserBackgroundPreset> presets = presetMapper.selectList(
                new LambdaQueryWrapper<UserBackgroundPreset>()
                        .eq(UserBackgroundPreset::getUserId, userId)
                        .orderByDesc(UserBackgroundPreset::getCreateTime)
        );
        return Result.success(presets);
    }

    @Operation(summary = "保存自定义预设")
    @PostMapping
    public Result<UserBackgroundPreset> save(@RequestBody PresetSaveDTO dto, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        // 同名预设更新，否则新增
        UserBackgroundPreset existing = presetMapper.selectOne(
                new LambdaQueryWrapper<UserBackgroundPreset>()
                        .eq(UserBackgroundPreset::getUserId, userId)
                        .eq(UserBackgroundPreset::getName, dto.getName())
        );
        if (existing != null) {
            existing.setBgType(dto.getBgType());
            existing.setBgValue(dto.getBgValue());
            existing.setThumbnail(dto.getThumbnail());
            presetMapper.updateById(existing);
            return Result.success(existing);
        }
        UserBackgroundPreset preset = new UserBackgroundPreset();
        preset.setUserId(userId);
        preset.setName(dto.getName());
        preset.setBgType(dto.getBgType());
        preset.setBgValue(dto.getBgValue());
        preset.setThumbnail(dto.getThumbnail());
        presetMapper.insert(preset);
        return Result.success(preset);
    }

    @Operation(summary = "删除自定义预设")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        presetMapper.delete(
                new LambdaQueryWrapper<UserBackgroundPreset>()
                        .eq(UserBackgroundPreset::getId, id)
                        .eq(UserBackgroundPreset::getUserId, userId)
        );
        return Result.success(null);
    }
}
