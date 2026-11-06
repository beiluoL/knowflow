package com.knowflow.learning.service;

import com.knowflow.common.SecurityUtils;
import com.knowflow.learning.dto.UserLearningPrefDTO;
import com.knowflow.learning.entity.UserLearningPref;
import com.knowflow.learning.mapper.UserLearningPrefMapper;
import com.knowflow.learning.vo.UserLearningPrefVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 管理认证用户的偏好读取、默认值及部分更新。 */
@Service
@RequiredArgsConstructor
public class UserLearningPrefService {
    private final UserLearningPrefMapper prefMapper;

    /** 获取当前用户的学习偏好（不存在则返回默认值） */
    public UserLearningPrefVO get() {
        Long userId = SecurityUtils.getCurrentUserId();
        UserLearningPref pref = prefMapper.selectOne(new LambdaQueryWrapper<UserLearningPref>()
                .eq(UserLearningPref::getUserId, userId));
        if (pref == null) {
            // 返回默认值，不持久化（首次保存时才落库）
            return defaultVO(userId);
        }
        return toVO(pref);
    }

    /** 保存 / 更新学习偏好（upsert） */
    public UserLearningPrefVO save(UserLearningPrefDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        UserLearningPref existing = prefMapper.selectOne(new LambdaQueryWrapper<UserLearningPref>()
                .eq(UserLearningPref::getUserId, userId));
        if (existing != null) {
            // 合并更新：仅覆盖非空字段
            applyDto(existing, dto);
            prefMapper.updateById(existing);
            return toVO(existing);
        }
        UserLearningPref pref = new UserLearningPref();
        pref.setUserId(userId);
        applyDto(pref, dto);
        // 补齐默认值（避免空字段）
        if (pref.getFocusMinutes() == null) pref.setFocusMinutes(25);
        if (pref.getShortBreak() == null) pref.setShortBreak(5);
        if (pref.getLongBreak() == null) pref.setLongBreak(15);
        if (pref.getRounds() == null) pref.setRounds(4);
        if (pref.getCardStrategy() == null) pref.setCardStrategy("RANDOM");
        if (pref.getCardCount() == null) pref.setCardCount(20);
        if (pref.getTheme() == null) pref.setTheme("day");
        if (pref.getFontSize() == null) pref.setFontSize("md");
        if (pref.getSoundEnabled() == null) pref.setSoundEnabled(1);
        if (pref.getNotificationEnabled() == null) pref.setNotificationEnabled(1);
        prefMapper.insert(pref);
        return toVO(pref);
    }

    /** 将 DTO 非空字段合并到实体 */
    private void applyDto(UserLearningPref pref, UserLearningPrefDTO dto) {
        if (dto.getFocusMinutes() != null) pref.setFocusMinutes(dto.getFocusMinutes());
        if (dto.getShortBreak() != null) pref.setShortBreak(dto.getShortBreak());
        if (dto.getLongBreak() != null) pref.setLongBreak(dto.getLongBreak());
        if (dto.getRounds() != null) pref.setRounds(dto.getRounds());
        if (dto.getCardStrategy() != null) pref.setCardStrategy(dto.getCardStrategy());
        if (dto.getCardCount() != null) pref.setCardCount(dto.getCardCount());
        if (dto.getDifficultyFilter() != null) pref.setDifficultyFilter(dto.getDifficultyFilter());
        if (dto.getTheme() != null) pref.setTheme(dto.getTheme());
        if (dto.getFontSize() != null) pref.setFontSize(dto.getFontSize());
        if (dto.getSoundEnabled() != null) pref.setSoundEnabled(dto.getSoundEnabled());
        if (dto.getNotificationEnabled() != null) pref.setNotificationEnabled(dto.getNotificationEnabled());
        if (dto.getReminderTime() != null) pref.setReminderTime(dto.getReminderTime());
        if (dto.getWhiteNoise() != null) pref.setWhiteNoise(dto.getWhiteNoise());
    }

    private UserLearningPrefVO toVO(UserLearningPref pref) {
        UserLearningPrefVO vo = new UserLearningPrefVO();
        vo.setId(pref.getId());
        vo.setUserId(pref.getUserId());
        vo.setFocusMinutes(pref.getFocusMinutes());
        vo.setShortBreak(pref.getShortBreak());
        vo.setLongBreak(pref.getLongBreak());
        vo.setRounds(pref.getRounds());
        vo.setCardStrategy(pref.getCardStrategy());
        vo.setCardCount(pref.getCardCount());
        vo.setDifficultyFilter(pref.getDifficultyFilter());
        vo.setTheme(pref.getTheme());
        vo.setFontSize(pref.getFontSize());
        vo.setSoundEnabled(pref.getSoundEnabled());
        vo.setNotificationEnabled(pref.getNotificationEnabled());
        vo.setReminderTime(pref.getReminderTime());
        vo.setWhiteNoise(pref.getWhiteNoise());
        return vo;
    }

    /** 默认偏好 VO（未保存过时返回） */
    private UserLearningPrefVO defaultVO(Long userId) {
        UserLearningPrefVO vo = new UserLearningPrefVO();
        vo.setUserId(userId);
        vo.setFocusMinutes(25);
        vo.setShortBreak(5);
        vo.setLongBreak(15);
        vo.setRounds(4);
        vo.setCardStrategy("RANDOM");
        vo.setCardCount(20);
        vo.setTheme("day");
        vo.setFontSize("md");
        vo.setSoundEnabled(1);
        vo.setNotificationEnabled(1);
        return vo;
    }
}
