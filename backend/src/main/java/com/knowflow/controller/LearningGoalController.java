package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.GoalDTO;
import com.knowflow.service.LearningGoalService;
import com.knowflow.vo.LearningGoalVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学习目标接口（Architecture-4.0 学习闭环起点）。
 * <p>
 * 端点：
 * - GET    /api/learning/goals        我的全部目标（ACTIVE 在前）
 * - GET    /api/learning/goals/active 当前进行中目标（无则 data=null）
 * - POST   /api/learning/goals        创建目标（pathId 非空则同时关联并自动报名）
 * - PUT    /api/learning/goals/{id}   覆盖式更新（含状态流转 / 换路径 / pathId=null 解除关联）
 * - DELETE /api/learning/goals/{id}   逻辑删除
 */
@Tag(name = "学习目标接口")
@RestController
@RequestMapping("/api/learning/goals")
@RequiredArgsConstructor
public class LearningGoalController {

    private final LearningGoalService goalService;

    @Operation(summary = "我的全部学习目标（按 ACTIVE → COMPLETED → ARCHIVED 排序）")
    @GetMapping
    public Result<List<LearningGoalVO>> listGoals() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(goalService.listGoals(userId));
    }

    @Operation(summary = "当前进行中学习目标（无则返回 null）")
    @GetMapping("/active")
    public Result<LearningGoalVO> activeGoal() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(goalService.activeGoal(userId));
    }

    @Operation(summary = "创建学习目标（可同时关联学习路径）")
    @PostMapping
    public Result<LearningGoalVO> createGoal(@RequestBody GoalDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(goalService.create(userId, dto));
    }

    @Operation(summary = "更新学习目标（标题/描述/状态/关联路径）")
    @PutMapping("/{id}")
    public Result<LearningGoalVO> updateGoal(@PathVariable Long id, @RequestBody GoalDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(goalService.update(userId, id, dto));
    }

    @Operation(summary = "删除学习目标（逻辑删除）")
    @DeleteMapping("/{id}")
    public Result<Void> removeGoal(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        goalService.remove(userId, id);
        return Result.success(null);
    }
}
