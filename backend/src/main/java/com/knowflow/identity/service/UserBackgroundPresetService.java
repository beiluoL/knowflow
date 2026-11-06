package com.knowflow.identity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.common.SecurityUtils;
import com.knowflow.identity.dto.BackgroundPresetSaveDTO;
import com.knowflow.identity.entity.UserBackgroundPreset;
import com.knowflow.identity.mapper.UserBackgroundPresetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

/** 在认证用户的命名空间内管理背景预设。 */
@Service
@RequiredArgsConstructor
public class UserBackgroundPresetService {
    private final UserBackgroundPresetMapper presetMapper;

    /** 仅返回认证用户自有预设，按最新优先排列。 */
    public List<UserBackgroundPreset> list() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<UserBackgroundPreset> presets = presetMapper.selectList(
                new LambdaQueryWrapper<UserBackgroundPreset>()
                        .eq(UserBackgroundPreset::getUserId, userId)
                        .orderByDesc(UserBackgroundPreset::getCreateTime)
        );
        return presets;
    }

    /** 更新所有者同名预设，不存在时创建。 */
    public UserBackgroundPreset save(BackgroundPresetSaveDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
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
            return existing;
        }
        UserBackgroundPreset preset = new UserBackgroundPreset();
        preset.setUserId(userId);
        preset.setName(dto.getName());
        preset.setBgType(dto.getBgType());
        preset.setBgValue(dto.getBgValue());
        preset.setThumbnail(dto.getThumbnail());
        presetMapper.insert(preset);
        return preset;
    }

    /** 仅删除自有预设，未知或他人标识保持无操作。 */
    public void delete(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        presetMapper.delete(
                new LambdaQueryWrapper<UserBackgroundPreset>()
                        .eq(UserBackgroundPreset::getId, id)
                        .eq(UserBackgroundPreset::getUserId, userId)
        );
    }
}
