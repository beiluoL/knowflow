package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户学习宠物 VO。
 */
@Data
public class UserPetVO {
    private Long id;
    private Long userId;
    private String name;
    private Integer level;
    private String mood;
    private Integer energy;
    private Integer exp;
    private Integer maxExp;
    private String avatar;
    private Integer totalFocusMinutes;
    private Integer totalPomodoros;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
