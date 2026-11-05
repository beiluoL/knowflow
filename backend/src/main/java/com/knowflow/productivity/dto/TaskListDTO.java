package com.knowflow.productivity.dto;

import lombok.Data;

/**
 * 任务清单（领域 / 项目 / 清单）创建 / 更新数据传输对象。
 */
@Data
public class TaskListDTO {

    private String name;

    /** 类型：area（领域）/ project（项目）/ list（清单）。 */
    private String kind;

    private Long parentId;

    private String color;

    private String icon;

    private Integer sortOrder;
}
