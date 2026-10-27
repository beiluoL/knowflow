package com.knowflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 场景练习提交结果 VO（Architecture-4.0-4，确定性覆盖评分反馈）。
 * <p>
 * score/coverage 由要点关键词覆盖度决定（Deterministic Workflow，§40），
 * 不含任何 AI 语义评分；掌握度不在此处改写，仅沉淀 SCENARIO_COMPLETED 学习事件。
 */
@Data
public class ScenarioSubmitResultVO {

    private Long scenarioId;

    /** 场景标题 */
    private String title;

    /** 要点总数 */
    private Integer totalPoints;

    /** 命中的要点数 */
    private Integer matchedPoints;

    /** 覆盖度 0.0 ~ 1.0 */
    private Double coverage;

    /** 百分制得分（coverage * 100 四舍五入） */
    private Integer score;

    /** 确定性分档点评（覆盖度驱动，非 AI 生成） */
    private String comment;

    /** 已覆盖的要点名称 */
    private List<String> matchedLabels;

    /** 未覆盖的要点名称（据此回补） */
    private List<String> missedLabels;

    /** 事件是否已记录（SCENARIO_COMPLETED 写入 learning_event） */
    private Boolean recorded;
}
