package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户成就解锁记录：用户与成就的多对多关系，同一成就仅能解锁一次。
 */
@Data
@TableName("user_achievement")
public class UserAchievement {

    private Long id;
    /** 用户ID */
    private Long userId;
    /** 成就ID */
    private Long achievementId;
    private java.time.LocalDateTime createTime;
    private Integer deleted;
}
