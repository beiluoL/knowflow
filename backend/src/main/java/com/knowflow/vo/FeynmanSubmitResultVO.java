package com.knowflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 费曼表达评分提交结果（Architecture-4.0-7）。
 * <p>
 * 返回四维分 + 总分 + 确定性分档点评；同时已落 FEYNMAN_EXPRESSED 学习事件。
 */
@Data
public class FeynmanSubmitResultVO {

    /** 表达主题（回显） */
    private String topic;

    /** 总分 0-100（四维平均，round） */
    private int overall;

    /** 四维结果（顺序同 rubric） */
    private List<FeynmanDimensionResultVO> dimensions;

    /** 确定性分档点评（阈值驱动，非 AI 生成） */
    private String comment;

    /** 是否已落 FEYNMAN_EXPRESSED 事件 */
    private Boolean recorded;
}
