package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户学习偏好实体。沉浸学习时长/难度/节奏等个性化设置，跨设备同步。
 * 参照 sys_user_ai_config 的 upsert 模式：一个用户仅一条有效配置（唯一索引保证）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_pref")
public class UserLearningPref extends BaseEntity {
    /** 用户ID（逻辑外键 sys_user.id） */
    private Long userId;
    /** 默认专注时长（分钟） */
    private Integer focusMinutes;
    /** 短休时长（分钟） */
    private Integer shortBreak;
    /** 长休时长（分钟） */
    private Integer longBreak;
    /** 每组工作次数 */
    private Integer rounds;
    /** 闪卡抽取策略：RANDOM/DIFFICULTY/DUE/WEAK */
    private String cardStrategy;
    /** 每次卡片数量（0 表示全部） */
    private Integer cardCount;
    /** 难度过滤：1/2/3 或空 */
    private String difficultyFilter;
    /** 阅读主题：day/eye/night/parchment */
    private String theme;
    /** 字体大小：sm/md/lg/xl */
    private String fontSize;
    /** 声音提醒：0 关 / 1 开 */
    private Integer soundEnabled;
    /** 桌面通知：0 关 / 1 开 */
    private Integer notificationEnabled;
    /** 每日学习提醒时间 HH:mm */
    private String reminderTime;
    /** 白噪音：rain/cafe/wave 或空 */
    private String whiteNoise;
}
