package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.ScenarioSubmitDTO;
import com.knowflow.service.LearningScenarioService;
import com.knowflow.vo.ScenarioDetailVO;
import com.knowflow.vo.ScenarioSubmitResultVO;
import com.knowflow.vo.ScenarioSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 场景练习接口（Architecture-4.0-4 Scenario Lab）。
 * <p>
 * 端点（需登录；内容为共享上架数据，作答事件按 userId 隔离）：
 * - GET  /api/learning/scenarios        上架场景列表（可按 knowledgeId / difficulty 过滤）
 * - GET  /api/learning/scenarios/{id}   场景详情（情景+问题+要点名称）
 * - POST /api/learning/scenarios/{id}/submit  提交作答 → 确定性覆盖评分 + 事件落库
 */
@Tag(name = "场景练习接口")
@RestController
@RequestMapping("/api/learning/scenarios")
@RequiredArgsConstructor
public class LearningScenarioController {

    private final LearningScenarioService scenarioService;

    @Operation(summary = "上架场景列表（Practice 节点入口）")
    @GetMapping
    public Result<List<ScenarioSummaryVO>> listScenarios(
            @RequestParam(required = false) Long knowledgeId,
            @RequestParam(required = false) Integer difficulty) {
        return Result.success(scenarioService.listPublished(knowledgeId, difficulty));
    }

    @Operation(summary = "场景详情（含要点名称清单，不含命中关键词）")
    @GetMapping("/{id}")
    public Result<ScenarioDetailVO> scenarioDetail(@PathVariable Long id) {
        return Result.success(scenarioService.detail(id));
    }

    @Operation(summary = "提交场景作答（确定性要点关键词覆盖评分 + SCENARIO_COMPLETED 事件）")
    @PostMapping("/{id}/submit")
    public Result<ScenarioSubmitResultVO> submitScenario(@PathVariable Long id,
                                                         @RequestBody ScenarioSubmitDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(scenarioService.submit(userId, id, dto));
    }
}
