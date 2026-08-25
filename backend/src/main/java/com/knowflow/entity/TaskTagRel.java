package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务-标签关联实体（多对多，逻辑外键，无物理外键）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_tag_rel")
public class TaskTagRel extends BaseEntity {

    /** 任务ID（逻辑外键 task.id） */
    private Long taskId;

    /** 标签ID（逻辑外键 task_tag.id） */
    private Long tagId;
}
