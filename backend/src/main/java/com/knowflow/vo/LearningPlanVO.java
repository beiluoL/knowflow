package com.knowflow.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 单日学习计划 VO。
 */
@Data
public class LearningPlanVO {

    /** 计划 ID（未生成则为 null）。 */
    private Long planId;

    /** 计划日期。 */
    private LocalDate date;

    /** 完成率 0~100，两位小数。 */
    private BigDecimal completedRatio;

    /** 状态：0 草稿 / 1 已生成 / 2 已完成。 */
    private Integer status;

    /** 总条目数（页面统计用）。 */
    private Integer totalItems;

    /** 已完成条目数。 */
    private Integer completedItems;

    /** 三时段块列表。 */
    private List<PlanBlockVO> blocks = new ArrayList<>();
}
