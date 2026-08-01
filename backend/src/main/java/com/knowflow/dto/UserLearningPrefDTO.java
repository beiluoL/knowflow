package com.knowflow.dto;

import lombok.Data;

/**
 * 用户学习偏好保存 DTO。全部字段可空，仅传需要更新的字段（upsert 合并）。
 */
@Data
public class UserLearningPrefDTO {
    private Integer focusMinutes;
    private Integer shortBreak;
    private Integer longBreak;
    private Integer rounds;
    private String cardStrategy;
    private Integer cardCount;
    private String difficultyFilter;
    private String theme;
    private String fontSize;
    private Integer soundEnabled;
    private Integer notificationEnabled;
    private String reminderTime;
    private String whiteNoise;
}
