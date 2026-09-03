package com.knowflow.service;

import com.knowflow.dto.FeynmanSubmitDTO;
import com.knowflow.vo.FeynmanRubricVO;
import com.knowflow.vo.FeynmanSubmitResultVO;

/**
 * 费曼表达四维评分服务（Architecture-4.0-7，Expression 节点）。
 * <p>
 * 职责边界：rubric（四维 × 4 条自检准则）常量下发 + 确定性计分 + FEYNMAN_EXPRESSED 事件落库。
 * 不调 AI 评分、不直写掌握度（作者自检为据，与 Scenario 同属「确定性证据候选」）。
 */
public interface LearningFeynmanService {

    /**
     * 获取评分 rubric（维度/准则文本/计分口径，全量确定性内容）。
     */
    FeynmanRubricVO rubric();

    /**
     * 提交费曼表达：校验 → 按勾选准则确定性计分（四维 + 总分）→ 事件落库 → 返回结果。
     */
    FeynmanSubmitResultVO submit(Long userId, FeynmanSubmitDTO dto);
}
