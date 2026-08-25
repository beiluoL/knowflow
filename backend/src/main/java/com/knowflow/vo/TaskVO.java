package com.knowflow.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务视图对象，含子任务树（children）与是否含子任务标记。
 */
@Data
public class TaskVO {

    private Long id;

    private Long listId;

    private Long parentId;

    private String title;

    private String notes;

    /** 0 待办 / 1 已完成。 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate scheduledDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime endTime;

    private Boolean someday;

    /** 重要程度：0 / 1（四象限视图用）。 */
    private Integer important;

    /** 紧急程度：0 / 1（四象限视图用）。 */
    private Integer urgent;

    /** 看板阶段：0 待办 / 1 进行中 / 2 已完成。 */
    private Integer stage;

    private Integer sortOrder;

    /** 子任务（嵌套）。 */
    private List<TaskVO> children;

    /** 是否拥有子任务（用于前端展开图标）。 */
    private Boolean hasChildren;

    /** 关联标签（Things3 式标签）。 */
    private List<TaskTagVO> tags;
}
