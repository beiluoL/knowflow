package com.knowflow.vo;

import lombok.Data;

/**
 * 场景卡片 VO（列表用，不携带 context 正文与答题关键词）。
 */
@Data
public class ScenarioSummaryVO {

    private Long id;

    /** 场景标题 */
    private String title;

    /** 要用户回答的问题（列表即可预览任务，点击进详情再读情景） */
    private String questionText;

    /** 难度 1-3 */
    private Integer difficulty;

    /** 关联知识点 ID（可为空） */
    private Long knowledgeId;

    /** 覆盖要点数（前端展示评分基数） */
    private Integer pointCount;
}
