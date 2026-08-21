package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务清单层级：领域(area) &gt; 项目(project) / 清单(list)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_list")
public class TaskList extends BaseEntity {

    private Long userId;

    private String name;

    /** area / project / list。 */
    private String kind;

    /** 父级 ID（area 的 parent 为 0；project/list 的 parent 指向 area 或 0）。 */
    private Long parentId;

    private String color;

    private String icon;

    private Integer sortOrder;
}
