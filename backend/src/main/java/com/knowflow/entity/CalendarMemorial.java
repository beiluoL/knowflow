package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 日历·自定义纪念日（Memorial）：用户自定义的固定 / 每年重复日期。
 * 两类：fixed（固定日期，仅当天生效）/ yearly（每年重复，按 month_day 展开）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("calendar_memorial")
public class CalendarMemorial extends BaseEntity {

    /** 所属用户ID（逻辑外键 sys_user.id） */
    private Long userId;

    /** 纪念日名称 */
    private String name;

    /** fixed 固定日期 / yearly 每年重复 */
    private String type;

    /** 月-日（MM-dd），yearly 展开依据；fixed 类型冗余存储便于统一排序 */
    private String monthDay;

    /** 固定日期（type=fixed 时必填，完整 yyyy-MM-dd） */
    private LocalDate fixedDate;

    /** 标记颜色（十六进制，如 #8B5CF6；空则取默认主题色） */
    private String color;

    /** 备注 */
    private String note;
}
