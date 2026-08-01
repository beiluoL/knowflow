package com.knowflow.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 学习周报 VO。
 */
@Data
public class WeeklyReportVO {
    private Long id;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate weekStart;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate weekEnd;
    /** AI 生成的周报摘要 */
    private String summary;
    /** 本周成就列表 */
    private List<String> achievements;
    /** 下周学习建议列表 */
    private List<String> suggestions;
    private Integer studyMinutes;
    private Integer checkinDays;
    private Integer flashcardReviewed;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
