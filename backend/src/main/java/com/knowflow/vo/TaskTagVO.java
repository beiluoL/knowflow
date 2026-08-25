package com.knowflow.vo;

import lombok.Data;

/**
 * 任务标签视图对象。
 */
@Data
public class TaskTagVO {

    private Long id;

    private String name;

    private String color;

    private Integer sortOrder;

    /** 关联任务数（侧栏计数用）。 */
    private Integer taskCount;
}
