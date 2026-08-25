package com.knowflow.dto;

import lombok.Data;

/**
 * 任务批量排序项：拖拽重排时单项 {id, sortOrder}。
 */
@Data
public class TaskReorderDTO {

    /** 任务 ID */
    private Long id;

    /** 新的排序序号 */
    private Integer sortOrder;
}
