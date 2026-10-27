package com.knowflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 费曼四维评分器 rubric（Architecture-4.0-7）。
 * <p>
 * rubric 由服务端常量定义并唯一下发：维度、准则文本、计分口径说明；
 * 前端渲染勾选清单，提交后服务端按同一份常量计分，保证确定性。
 */
@Data
public class FeynmanRubricVO {

    /** 四个维度（顺序固定：准确性 → 清晰度 → 完整度 → 结构） */
    private List<FeynmanDimensionVO> dimensions;

    /** 计分口径说明（如「每维 4 条自检，评分 = 勾选数 / 4」） */
    private String scoringNote;
}
