package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户挑战进度实体：记录某用户在某赛道的整体进度（当前关卡、累计积分与星星）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("code_challenge_record")
public class CodeChallengeRecord extends BaseEntity {

    /** 用户 ID（逻辑外键 sys_user.id） */
    private Long userId;

    /** 挑战 ID（逻辑外键 code_challenge.id） */
    private Long challengeId;

    /** 已通关关卡数 */
    private Integer clearedLevels;

    /** 当前解锁到的关卡序号 */
    private Integer currentLevel;

    /** 本赛道累计获得积分 */
    private Integer totalPoints;

    /** 本赛道累计星星数 */
    private Integer totalStars;

    /** 状态：0 进行中 / 1 已通关 */
    private Integer status;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 通关时间 */
    private LocalDateTime finishTime;
}
