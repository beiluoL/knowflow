package com.knowflow.vo;

import lombok.Data;

/**
 * 复习评分结果 VO：反馈本次 SM-2 调度后的新间隔与下次复习时间。
 */
@Data
public class WbReviewGradeResultVO {

    /** 卡片ID */
    private Long cardId;

    /** 本次评分质量 */
    private Integer quality;

    /** 连续答对次数 */
    private Integer repetitions;

    /** 新复习间隔（天） */
    private Integer intervalDay;

    /** 新难度系数（小数） */
    private Double easeFactor;

    /** 下次复习时间（毫秒时间戳，前端倒计时用） */
    private Long nextReviewAt;

    /** 是否遗忘（本次评分为 0/1） */
    private Boolean lapsed;
}
