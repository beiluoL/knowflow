package com.knowflow.vo;

import lombok.Data;

/**
 * 每日学习活跃度视图对象，用于学习热力图（date 为 yyyy-MM-dd，count 为当日学习事件数）。
 */
@Data
public class DailyActivityVO {

    private String date;

    private Integer count;
}
