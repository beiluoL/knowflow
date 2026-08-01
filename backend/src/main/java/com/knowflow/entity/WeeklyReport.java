package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 学习周报实体。
 * 按自然周（周一至周日）维度汇总用户学习统计，并由 AI 生成摘要、成就与下周建议。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("weekly_report")
public class WeeklyReport extends BaseEntity {
    /** 用户ID（逻辑外键 sys_user.id） */
    private Long userId;
    /** 本周一日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate weekStart;
    /** 本周日日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate weekEnd;
    /** AI 生成的周报摘要 */
    private String summary;
    /** JSON：本周成就列表 */
    private String achievements;
    /** JSON：下周学习建议 */
    private String suggestions;
    /** 本周学习分钟 */
    private Integer studyMinutes;
    /** 本周签到天数 */
    private Integer checkinDays;
    /** 本周闪卡复习数 */
    private Integer flashcardReviewed;
}
