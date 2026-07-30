package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户关卡通关记录实体：记录某用户单个关卡的通关结果、星级与获得积分。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("code_challenge_level_record")
public class CodeChallengeLevelRecord extends BaseEntity {

    /** 用户 ID（逻辑外键 sys_user.id） */
    private Long userId;

    /** 挑战 ID（逻辑外键 code_challenge.id） */
    private Long challengeId;

    /** 关卡 ID（逻辑外键 code_challenge_level.id） */
    private Long levelId;

    /** 关卡序号 */
    private Integer levelNo;

    /** 是否通关：0 未通关 / 1 已通关 */
    private Integer passed;

    /** 获得星级：1-3 */
    private Integer stars;

    /** 提交次数 */
    private Integer attempts;

    /** 本关获得积分 */
    private Integer pointsEarned;

    /** 最近一次提交的代码 */
    private String lastCode;

    /** 通关时间 */
    private LocalDateTime finishTime;
}
