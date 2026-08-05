package com.knowflow.dto;

import lombok.Data;

/**
 * 复习卡片评分入参：用户反馈驱动 SM-2 调度。
 */
@Data
public class WbReviewGradeDTO {

    /** 用户反馈评分：0 完全忘记 / 1 困难 / 2 一般 / 3 容易（映射 SM-2 质量 q） */
    private Integer quality;

    /** 本次作答耗时（毫秒），可选 */
    private Long costMs;
}
