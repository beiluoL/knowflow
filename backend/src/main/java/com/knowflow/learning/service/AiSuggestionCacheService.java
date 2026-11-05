package com.knowflow.learning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.learning.entity.AiLearningSuggestion;
import com.knowflow.learning.mapper.AiLearningSuggestionMapper;
import com.knowflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 外部 AI 处理完成后持久化生成建议。 */
@Service
@RequiredArgsConstructor
public class AiSuggestionCacheService {
    private final AiLearningSuggestionMapper mapper;
    private final com.knowflow.identity.mapper.SysUserMapper users;

    /** 原子替换所有者的周期缓存，插入失败时保留旧行。 */
    @Transactional(rollbackFor = Exception.class)
    public void replace(AiLearningSuggestion suggestion) {
        lockOwner(suggestion.getUserId());
        mapper.delete(new LambdaQueryWrapper<AiLearningSuggestion>()
                .eq(AiLearningSuggestion::getUserId, suggestion.getUserId())
                .eq(AiLearningSuggestion::getPeriod, suggestion.getPeriod()));
        persist(suggestion);
    }

    /** 插入生成缓存行；AI 生成在此事务之前执行。 */
    @Transactional(rollbackFor = Exception.class)
    public void insert(AiLearningSuggestion suggestion) {
        lockOwner(suggestion.getUserId());
        mapper.delete(new LambdaQueryWrapper<AiLearningSuggestion>()
                .eq(AiLearningSuggestion::getUserId, suggestion.getUserId())
                .eq(AiLearningSuggestion::getPeriod, suggestion.getPeriod()));
        persist(suggestion);
    }

    private void lockOwner(Long userId) {
        if (users.lockActiveUserId(userId) == null) throw new BusinessException(404, "用户不存在");
    }

    private void persist(AiLearningSuggestion suggestion) {
        if (mapper.insert(suggestion) != 1) {
            throw new BusinessException(409, "学习建议保存冲突");
        }
    }
}
