package com.knowflow.dto;

import lombok.Data;

/**
 * 任务清单（领域 / 项目 / 清单）创建 / 更新数据传输对象。
 */
@Data
public class TaskListDTO {

    private String name;

    /** area / project / list。 */
    private String kind;

    private Long parentId;

    private String color;

    private String icon;

    private Integer sortOrder;
}
