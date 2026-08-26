package com.knowflow.vo;

import lombok.Data;

/**
 * 计划条目 VO：属于某个时段块（PlanBlockVO）中的单个执行项。
 * type ∈ {learningTask, todo, habit}，对应只填一种关联 ID，其余为 null。
 */
@Data
public class PlanItemVO {

    /** 条目类型：learningTask / todo / habit。 */
    private String type;

    /** 关联「学习任务」ID（type=learningTask 时非空）。 */
    private Long learningTaskId;

    /** 关联「章节」ID（type=learningTask 时有值，便于跳转学习页）。 */
    private Long chapterId;

    /** 关联「待办任务」ID（type=todo 时非空）。 */
    private Long taskId;

    /** 关联「习惯」ID（type=habit 时非空）。 */
    private Long habitId;

    /** 展示标题。 */
    private String title;

    /** 预计耗时（分钟）。 */
    private Integer duration;

    /** 建议开始时间 HH:mm（ICS 导出用，空则按块起始分摊）。 */
    private String startTime;

    /** 建议结束时间 HH:mm。 */
    private String endTime;

    /** 是否已完成（实时查三张表计算，非缓存）。 */
    private Boolean completed;
}
