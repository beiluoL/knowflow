package com.knowflow.service;

import com.knowflow.dto.GoalDTO;
import com.knowflow.vo.LearningGoalVO;

import java.util.List;

/**
 * 学习目标服务（Architecture-4.0 学习闭环起点）。
 * <p>
 * 职责边界：只持久化「目标」及其到 learning_path 的关联，并实时带出路径进度；
 * 不做 AI 生成、不做掌握度计算 —— 那些复用 LearningService / Mastery 引擎。
 */
public interface LearningGoalService {

    /**
     * 我的全部目标（未删），按 ACTIVE → COMPLETED → ARCHIVED 排序，组内按创建时间倒序。
     */
    List<LearningGoalVO> listGoals(Long userId);

    /**
     * 当前进行中目标（status=ACTIVE 中创建最早的一条；无则 null）。
     */
    LearningGoalVO activeGoal(Long userId);

    /**
     * 创建目标。title 必填；dto.pathId 非空时立即校验并关联（未报名自动报名）。
     */
    LearningGoalVO create(Long userId, GoalDTO dto);

    /**
     * 覆盖式更新目标（含状态流转 / 更换关联路径 / 解除关联 pathId=null）。仅限本人目标。
     */
    LearningGoalVO update(Long userId, Long goalId, GoalDTO dto);

    /**
     * 逻辑删除目标（仅限本人）。
     */
    void remove(Long userId, Long goalId);
}
