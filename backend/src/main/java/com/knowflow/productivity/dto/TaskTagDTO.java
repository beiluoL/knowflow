package com.knowflow.productivity.dto;

import lombok.Data;

import java.util.List;

/**
 * 任务标签创建 / 更新数据传输对象。
 */
@Data
public class TaskTagDTO {

    private String name;

    private String color;

    private Integer sortOrder;
}
