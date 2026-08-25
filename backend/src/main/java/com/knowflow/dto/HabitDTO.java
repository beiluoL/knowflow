package com.knowflow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

/**
 * 习惯创建 / 更新数据传输对象。
 */
@Data
public class HabitDTO {

    private String name;

    private String description;

    private String icon;

    private String color;

    /** daily 每日 / weekly 每周 */
    private String frequency;

    private Integer targetCount;

    /** 提醒时间 HH:mm */
    private String reminderTime;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    /** 1 启用 / 0 停用 */
    private Integer active;

    private Integer sortOrder;
}
