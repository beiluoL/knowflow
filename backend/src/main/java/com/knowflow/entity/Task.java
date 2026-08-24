package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Things3 式任务实体：支持清单/项目/领域层级（list_id）、
 * 子任务（parent_id）、智能列表（scheduled_date / someday / status）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task")
public class Task extends BaseEntity {

    private Long userId;

    /** 所属清单/项目 ID（task_list.id）；null 表示收件箱（Inbox）。 */
    private Long listId;

    /** 父任务 ID（子任务用）；0 表示顶层任务。 */
    private Long parentId;

    private String title;

    private String notes;

    /** 0 待办 / 1 已完成。 */
    private Integer status;

    /** 计划日期（Today / Upcoming 智能列表依据）；null 表示未计划。 */
    private LocalDate scheduledDate;

    /** 截止日期（可选）。 */
    private LocalDate dueDate;

    /** 任务开始时间（日历定时事件用）；null 表示无具体时间（按 scheduled_date 作为全天事件）。 */
    private LocalDateTime startTime;

    /** 任务结束时间（可选，仅当 startTime 存在时有意义）。 */
    private LocalDateTime endTime;

    /** 1 表示放入「某天 / 也许」清单（Someday）。 */
    private Integer someday;

    /** 重要程度：0 不重要 / 1 重要（四象限视图用）。 */
    private Integer important;

    /** 紧急程度：0 不紧急 / 1 紧急（四象限视图用）。 */
    private Integer urgent;

    /** 看板阶段：0 待办 / 1 进行中 / 2 已完成（看板视图用，与 status 同步）。 */
    private Integer stage;

    private Integer sortOrder;
}
