package com.knowflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 场景详情 VO（作答前展示：情景 + 问题 + 要点名称清单）。
 * <p>
 * 要点仅暴露 label（名称），不暴露命中关键词 keywords——关键词若提前下发，
 * 用户可机械拼凑命中，覆盖评分将失去「自检表达」意义。
 */
@Data
public class ScenarioDetailVO {

    private Long id;

    /** 场景标题 */
    private String title;

    /** 情景背景描述 */
    private String contextText;

    /** 要用户回答的问题 */
    private String questionText;

    /** 难度 1-3 */
    private Integer difficulty;

    /** 关联知识点 ID（可为空） */
    private Long knowledgeId;

    /** 要点名称清单（评分基数，提交后逐条反馈是否覆盖） */
    private List<String> pointLabels;
}
