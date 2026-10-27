package com.knowflow.service;

import com.knowflow.dto.FirstPrinciplesSubmitDTO;
import com.knowflow.vo.FirstPrinciplesRubricVO;
import com.knowflow.vo.FirstPrinciplesSubmitResultVO;

/**
 * 第一性原理推导服务（Architecture-4.1-§14~17，Understanding 节点）。
 * <p>
 * 职责边界：六环节 rubric 常量下发 + 结构完整度确定性评估 + FIRST_PRINCIPLES_DERIVED 事件落库。
 * 不调 AI 评分、不做语义正确性判断、不直写掌握度（§16/§33/§34）。
 */
public interface LearningFirstPrinciplesService {

    /**
     * 获取六环节 rubric（环节定义 / 提示 / 计分口径，全量确定性内容）。
     */
    FirstPrinciplesRubricVO rubric();

    /**
     * 提交第一性原理推导：校验 → 结构完整度评估 → 事件落库 → 返回结果。
     */
    FirstPrinciplesSubmitResultVO submit(Long userId, FirstPrinciplesSubmitDTO dto);
}
