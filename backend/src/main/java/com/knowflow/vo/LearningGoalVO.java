package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习目标视图对象（Architecture-4.0 学习闭环起点）。
 * <p>
 * 当目标关联路径时，后端实时带出路径标题、章节数与学习进度（0~100），
 * 前端无需再并发拉取路径列表即可渲染「当前目标」首屏。
 */
@Data
public class LearningGoalVO {

    /** 目标 ID。 */
    private Long id;

    /** 目标标题。 */
    private String title;

    /** 目标描述。 */
    private String description;

    /** 达标标准。 */
    private String target;

    /** 状态：ACTIVE / COMPLETED / ARCHIVED。 */
    private String status;

    /** 关联学习路径 ID（可为空）。 */
    private Long pathId;

    /** 关联路径标题（pathId 为空时为 null）。 */
    private String pathTitle;

    /** 关联路径进度 0~100（未报名时 0）。 */
    private Integer pathProgress;

    /** 关联路径章节数。 */
    private Integer pathChapterCount;

    /** 开始日期。 */
    private LocalDate startDate;

    /** 截止日期。 */
    private LocalDate deadline;

    /** 每日计划投入分钟。 */
    private Integer dailyMinutes;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
