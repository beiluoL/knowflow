package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

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

    /** 1 表示放入「某天 / 也许」清单（Someday）。 */
    private Integer someday;

    private Integer sortOrder;
}
