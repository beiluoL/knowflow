package com.knowflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 输出准确率评估结果：量化 Agent 输出与用户真实意图的匹配度，并给出改进建议。
 */
@Data
public class AgentEvalVO {
    /** 总体匹配度 0~1 */
    private Double matchScore;
    /** 各维度得分（intent 意图 / spec 规格 / format 格式） */
    private java.util.Map<String, Double> dimensions;
    /** 未满足点（用于知识库回流） */
    private List<String> misses;
    /** 改进建议 */
    private List<String> suggestions;
    /** 本次评估是否基于用户显式反馈（点赞/改写/澄清补充） */
    private Boolean fromFeedback;
}
