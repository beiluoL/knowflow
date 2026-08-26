package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 学习计划实体：AI 编排生成的日级计划。
 * <p>
 * 聚合三类执行项（学习任务 / 习惯 / todo），按早/午/晚三时段时间块存储。
 * 唯一维度 (user_id, plan_date, deleted) 保证单用户单日至多一条。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_plan")
public class LearningPlan extends BaseEntity {

    /** 所属用户ID。 */
    private Long userId;

    /** 计划日期。 */
    private LocalDate planDate;

    /** 学习任务 ID 列表（JSON 数组字符串，Long[]）。 */
    private String learningTaskIds;

    /** 习惯 ID 列表（JSON 数组字符串，Long[]）。 */
    private String habitIds;

    /** 待办任务 ID 列表（JSON 数组字符串，Long[]）。 */
    private String todoIds;

    /** 时间块 JSON 数组字符串（PlanBlockVO[] 序列化）。 */
    private String timeBlocks;

    /** 当日计划完成率(%)，0~100，保留两位小数。 */
    private BigDecimal completedRatio;

    /** 状态：0 草稿 / 1 已生成 / 2 已完成。 */
    private Integer status;
}
