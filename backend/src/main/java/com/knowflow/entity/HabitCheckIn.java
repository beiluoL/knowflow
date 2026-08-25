package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 习惯打卡记录实体：每习惯每自然日一行，count 累计当日打卡次数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("habit_checkin")
public class HabitCheckIn extends BaseEntity {

    /** 用户ID */
    private Long userId;

    /** 习惯ID（逻辑外键 habit.id） */
    private Long habitId;

    /** 打卡日期（自然日） */
    private LocalDate checkDate;

    /** 当日累计打卡次数 */
    private Integer count;

    /** 打卡备注 */
    private String note;
}
