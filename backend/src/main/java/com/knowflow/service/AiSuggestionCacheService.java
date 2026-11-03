package com.knowflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.entity.AiLearningSuggestion;
import com.knowflow.mapper.AiLearningSuggestionMapper;
import com.knowflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists generated suggestions after external AI processing has completed. */
@Service
@RequiredArgsConstructor
public class AiSuggestionCacheService {
    private final AiLearningSuggestionMapper mapper;
    private final com.knowflow.mapper.SysUserMapper users;

    /** Replaces the owner's period cache atomically; a failed insert retains old rows. */
    @Transactional(rollbackFor = Exception.class)
    public void replace(AiLearningSuggestion suggestion) {
        lockOwner(suggestion.getUserId());
        mapper.delete(new LambdaQueryWrapper<AiLearningSuggestion>()
                .eq(AiLearningSuggestion::getUserId, suggestion.getUserId())
                .eq(AiLearningSuggestion::getPeriod, suggestion.getPeriod()));
        persist(suggestion);
    }

    /** Inserts a generated cache row; AI generation occurs before this transaction. */
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
