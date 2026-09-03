package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.FeynmanSubmitDTO;
import com.knowflow.service.LearningFeynmanService;
import com.knowflow.vo.FeynmanRubricVO;
import com.knowflow.vo.FeynmanSubmitResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 费曼表达四维评分接口（Architecture-4.0-7）。
 * <p>
 * 端点（需登录；评分事件按 userId 隔离）：
 * - GET  /api/learning/feynman/rubric   获取四维自检 rubric（确定性内容）
 * - POST /api/learning/feynman/submit   提交解释 + 勾选准则 → 确定性计分 + FEYNMAN_EXPRESSED 事件
 */
@Tag(name = "费曼表达四维评分")
@RestController
@RequestMapping("/api/learning/feynman")
@RequiredArgsConstructor
public class LearningFeynmanController {

    private final LearningFeynmanService feynmanService;

    @Operation(summary = "费曼四维评分 rubric（Accuracy/Clarity/Completeness/Structure）")
    @GetMapping("/rubric")
    public Result<FeynmanRubricVO> rubric() {
        return Result.success(feynmanService.rubric());
    }

    @Operation(summary = "提交费曼表达（确定性自检计分 + FEYNMAN_EXPRESSED 事件）")
    @PostMapping("/submit")
    public Result<FeynmanSubmitResultVO> submit(@RequestBody FeynmanSubmitDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(feynmanService.submit(userId, dto));
    }
}
