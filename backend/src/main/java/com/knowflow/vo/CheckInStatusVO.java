package com.knowflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 每日打卡状态视图对象：今日是否已打卡、连续天数与本月打卡日历。
 */
@Data
public class CheckInStatusVO {

    /** 今日是否已打卡 */
    private Boolean checkedToday;

    /** 当前连续打卡天数 */
    private Integer continuousDays;

    /** 累计打卡天数 */
    private Integer totalDays;

    /** 本月已打卡的日期号列表（如 [1,2,5]） */
    private List<Integer> monthCheckedDays;
}
