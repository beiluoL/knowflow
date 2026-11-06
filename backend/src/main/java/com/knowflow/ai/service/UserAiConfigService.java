package com.knowflow.ai.service;
import com.knowflow.common.Result;
import com.knowflow.exception.BusinessException;
import com.knowflow.config.AiProviderRegistry;
import com.knowflow.ai.dto.UserAiConfigDTO;
import com.knowflow.ai.entity.UserAiConfig;
import com.knowflow.ai.mapper.UserAiConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 原子持久化配置变更与当前启用项变更。 */
@Service
@RequiredArgsConstructor
public class UserAiConfigService {
    private final UserAiConfigMapper userAiConfigMapper;
    private final AiProviderRegistry providerRegistry;
    private final com.knowflow.identity.mapper.SysUserMapper users;

    /** 将保存配置和清除原启用项作为同一个操作执行。 */
    @Transactional(rollbackFor = Exception.class)
    public Result<UserAiConfig> save(Long userId, UserAiConfigDTO dto) {
        lockOwner(userId);
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
            requireWrite(userAiConfigMapper.updateById(existing));
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
            requireWrite(userAiConfigMapper.insert(newConfig));
            if (Integer.valueOf(1).equals(newConfig.getIsActive())) {
                clearOtherActive(userId, newConfig.getId());
            }
        }
        return Result.success(findActive(userId));
    }
    /** 切换启用项；任一更新失败时整体回滚。 */
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> activate(Long userId, Long id) {
        lockOwner(userId);
        UserAiConfig existing = userAiConfigMapper.selectById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return Result.error("配置不存在或无权操作");
        }
        clearOtherActive(userId, id);
        requireWrite(userAiConfigMapper.update(null, new LambdaUpdateWrapper<UserAiConfig>()
                .eq(UserAiConfig::getId, id)
                .eq(UserAiConfig::getUserId, userId)
                .set(UserAiConfig::getIsActive, 1)));
        return Result.success();
    }
    /** 解析按所有者隔离的当前模型配置。 */
    public UserAiConfig findActive(Long userId) {
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

    private void lockOwner(Long userId) {
        if (users.lockActiveUserId(userId) == null) throw new BusinessException(404, "用户不存在");
    }

    private void requireWrite(int affected) {
        if (affected != 1) throw new BusinessException(409, "模型配置更新冲突");
    }

    /** 列出配置时按所有者隔离。 */
    public java.util.List<UserAiConfig> listConfigs(Long userId) {
        java.util.List<UserAiConfig> list = userAiConfigMapper.selectList(
                new LambdaQueryWrapper<UserAiConfig>()
                        .eq(UserAiConfig::getUserId, userId)
                        .orderByDesc(UserAiConfig::getIsActive)
                        .orderByDesc(UserAiConfig::getCreateTime));
        return list;
    }

    /** 删除配置时按所有者隔离。 */
    public Result<Void> deleteConfig(Long userId, Long id) {
        UserAiConfig existing = userAiConfigMapper.selectById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return Result.error("配置不存在或无权操作");
        }
        userAiConfigMapper.deleteById(id);
        return Result.success();
    }

    /** 删除全部配置时按所有者隔离。 */
    public Result<Void> deleteAll(Long userId) {
        userAiConfigMapper.delete(new LambdaQueryWrapper<UserAiConfig>()
                .eq(UserAiConfig::getUserId, userId));
        return Result.success();
    }
    /** 仅在指定认证所有者范围内解析配置。 */
    public UserAiConfig findOwned(Long userId, Long configId) {
        return userAiConfigMapper.selectOne(new LambdaQueryWrapper<UserAiConfig>()
                .eq(UserAiConfig::getUserId, userId).eq(UserAiConfig::getId, configId));
    }
}
