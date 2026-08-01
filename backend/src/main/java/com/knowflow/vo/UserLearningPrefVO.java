package com.knowflow.vo;

import lombok.Data;

/**
 * 用户学习偏好 VO。
 */
@Data
public class UserLearningPrefVO {
    private Long id;
    private Long userId;
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
