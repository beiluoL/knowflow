package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 思维导图列表项 VO（不含整图数据，仅概要）。
 */
@Data
public class MindMapSummaryVO {

    private Long id;
    private String title;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
