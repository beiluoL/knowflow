package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 绘图列表项 VO（不含整图数据，仅概要）。
 */
@Data
public class DrawingSummaryVO {

    private Long id;
    private String title;
    private String type;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
