package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 学习目标实体（Learning Goal，Architecture-4.0 学习闭环起点）。
 * <p>
 * 用户以一句话创建目标（如「7 天掌握 JVM GC」），目标可关联一条真实落地路径
 * （learning_path.id，逻辑外键）；路径进度即目标进度，不在此表冗余存储。
 * 状态机：ACTIVE（进行中，全表至多一个默认推荐）/ COMPLETED（达成）/ ARCHIVED（归档）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_goal")
public class LearningGoal extends BaseEntity {

    /** 所属用户 ID（逻辑外键 sys_user.id）。 */
    private Long userId;

    /** 目标标题：一句话目标，如「7 天掌握 JVM GC」。 */
    private String title;

    /** 目标描述：为什么学、背景（可选）。 */
    private String description;

    /** 达标标准：做到什么算掌握（可选，作为目标完成判定参照）。 */
    private String target;

    /** 状态：ACTIVE 进行中 / COMPLETED 已完成 / ARCHIVED 已归档。 */
    private String status;

    /** 关联学习路径 ID（逻辑外键 learning_path.id，可为空 = 尚未关联路线）。 */
    private Long pathId;

    /** 开始日期（可选，用于时间盒展示）。 */
    private LocalDate startDate;

    /** 截止日期（可选，时间盒终点）。 */
    private LocalDate deadline;

    /** 每日计划投入分钟（可选，默认 0=未约定）。 */
    private Integer dailyMinutes;
}
