package com.knowflow.productivity.vo;

import lombok.Data;

/**
 * 任务清单视图对象，含该清单下未完成 / 已完成任务计数。
 */
@Data
public class TaskListVO {

    private Long id;

    private String name;

    /** 类型：area（领域）/ project（项目）/ list（清单）。 */
    private String kind;

    private Long parentId;

    private String color;

    private String icon;

    private Integer sortOrder;

    /** 未完成任务数。 */
    private Integer taskCount;

    /** 已完成任务数。 */
    private Integer doneCount;
}
