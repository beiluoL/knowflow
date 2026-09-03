package com.knowflow.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 学习目标新增 / 编辑入参（Architecture-4.0）。
 * <p>
 * 前端每次提交完整可编辑字段（含 status / pathId / deadline 等），
 * 语义为「覆盖式更新」：pathId 传 null 表示解除路径关联，description / target 传 null 表示清空。
 */
@Data
public class GoalDTO {

    /** 目标标题：一句话目标，必填（如「7 天掌握 JVM GC」）。 */
    private String title;

    /** 目标描述（可选）。 */
    private String description;

    /** 达标标准（可选）。 */
    private String target;

    /** 状态：ACTIVE / COMPLETED / ARCHIVED。创建时缺省 ACTIVE。 */
    private String status;

    /** 关联学习路径 ID（可选；null = 不关联 / 解除关联）。 */
    private Long pathId;

    /** 开始日期（可选）。 */
    private LocalDate startDate;

    /** 截止日期（可选）。 */
    private LocalDate deadline;

    /** 每日计划投入分钟（可选）。 */
    private Integer dailyMinutes;
}
