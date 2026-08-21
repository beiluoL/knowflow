package com.knowflow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

/**
 * 任务创建 / 更新数据传输对象。
 */
@Data
public class TaskDTO {

    private String title;

    private Long listId;

    /** 父任务 ID（子任务用）；为 null 时视为顶层任务（0）。 */
    private Long parentId;

    private String notes;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate scheduledDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueDate;

    private Boolean someday;

    private Integer sortOrder;

    /** 仅更新时使用：任务状态 0/1。 */
    private Integer status;
}
