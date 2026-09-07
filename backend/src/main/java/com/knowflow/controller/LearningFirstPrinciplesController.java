package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.FirstPrinciplesSubmitDTO;
import com.knowflow.service.LearningFirstPrinciplesService;
import com.knowflow.vo.FirstPrinciplesRubricVO;
import com.knowflow.vo.FirstPrinciplesSubmitResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 第一性原理推导接口（Architecture-4.1-§14~17）。
 * <p>
 * 端点（需登录；评分事件按 userId 隔离）：
 * - GET  /api/learning/first-principles/rubric  获取六环节定义与计分口径（确定性内容）
 * - POST /api/learning/first-principles/submit  提交六环节 → 结构完整度评估 + FIRST_PRINCIPLES_DERIVED 事件
 */
@Tag(name = "第一性原理推导")
@RestController
@RequestMapping("/api/learning/first-principles")
@RequiredArgsConstructor
public class LearningFirstPrinciplesController {

    private final LearningFirstPrinciplesService firstPrinciplesService;

    @Operation(summary = "第一性原理六环节 rubric（已知事实/假设/约束/底层机制/因果关系/重新推导）")
    @GetMapping("/rubric")
    public Result<FirstPrinciplesRubricVO> rubric() {
        return Result.success(firstPrinciplesService.rubric());
    }

    @Operation(summary = "提交第一性原理推导（结构完整度评估 + FIRST_PRINCIPLES_DERIVED 事件）")
    @PostMapping("/submit")
    public Result<FirstPrinciplesSubmitResultVO> submit(@RequestBody FirstPrinciplesSubmitDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(firstPrinciplesService.submit(userId, dto));
    }
}
