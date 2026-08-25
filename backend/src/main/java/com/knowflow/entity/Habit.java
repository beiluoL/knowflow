package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 习惯实体：多习惯打卡管理，支持每日/每周频率与目标次数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("habit")
public class Habit extends BaseEntity {

    /** 用户ID（逻辑外键 sys_user.id） */
    private Long userId;

    /** 习惯名称 */
    private String name;

    /** 习惯描述 */
    private String description;

    /** 习惯图标 */
    private String icon;

    /** 习惯主题色 */
    private String color;

    /** 打卡频率：daily 每日 / weekly 每周 */
    private String frequency;

    /** 每周期目标打卡次数（如每日 8 杯水） */
    private Integer targetCount;

    /** 提醒时间 HH:mm（前端 Notification API 触发） */
    private String reminderTime;

    /** 习惯开始日期 */
    private LocalDate startDate;

    /** 是否启用：1 启用 / 0 停用（POJO 不加 is 前缀） */
    private Integer active;

    /** 排序 */
    private Integer sortOrder;
}
