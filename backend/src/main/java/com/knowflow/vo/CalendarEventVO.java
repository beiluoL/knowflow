package com.knowflow.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 日历事件视图对象：在 TaskVO 基础上补充起止时间与清单着色信息，
 * 供月 / 周 / 日三种日历视图及范围查询统一消费。
 */
@Data
public class CalendarEventVO {

    private Long id;

    private Long listId;

    private Long parentId;

    private String title;

    private String notes;

    /** 0 待办 / 1 已完成。 */
    private Integer status;

    private Boolean someday;

    /** 重要程度：0 / 1。 */
    private Integer important;

    /** 紧急程度：0 / 1。 */
    private Integer urgent;

    /** 看板阶段：0 待办 / 1 进行中 / 2 已完成。 */
    private Integer stage;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate scheduledDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime endTime;

    /** 是否全天事件：startTime 为空且 scheduledDate 存在时为 true。 */
    private Boolean allDay;

    /** 所属清单名称（用于日历着色与 tooltip）。 */
    private String listName;

    /** 所属清单颜色（用于日历事件块着色）。 */
    private String listColor;
}
