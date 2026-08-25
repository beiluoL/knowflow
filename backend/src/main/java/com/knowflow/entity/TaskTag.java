package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务标签实体（Things3 式标签，按用户隔离）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_tag")
public class TaskTag extends BaseEntity {

    /** 用户ID */
    private Long userId;

    /** 标签名称 */
    private String name;

    /** 标签颜色 */
    private String color;

    /** 排序 */
    private Integer sortOrder;
}
