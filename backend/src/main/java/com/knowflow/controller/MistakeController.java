package com.knowflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowflow.common.PageResult;
import com.knowflow.common.Result;
import com.knowflow.dto.CodeMistakeCollectRequest;
import com.knowflow.dto.CodeMistakeCollectResult;
import com.knowflow.entity.LearningMistake;
import com.knowflow.service.MistakeService;
import com.knowflow.vo.MistakeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/** 错题本 REST 接口，提供错题的增删查、掌握标记与统计。 */
@Tag(name = "错题本接口")
@RestController
@RequestMapping("/api/mistakes")
@RequiredArgsConstructor
public class MistakeController {

    private final MistakeService mistakeService;

    @Operation(summary = "错题列表")
    @GetMapping
    public Result<PageResult<MistakeVO>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer mastered,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            Authentication authentication) {
        Long userId = authentication != null ? (Long) authentication.getPrincipal() : null;
        IPage<MistakeVO> page = mistakeService.getMistakePage(userId, category, mastered, pageNum, pageSize);
        return Result.success(PageResult.of(page));
    }

    @Operation(summary = "错题详情")
    @GetMapping("/{id}")
    public Result<MistakeVO> detail(@PathVariable Long id, Authentication authentication) {
        Long userId = authentication != null ? (Long) authentication.getPrincipal() : null;
        return Result.success(mistakeService.getMistakeDetail(id, userId));
    }

    @Operation(summary = "标记已掌握")
    @PutMapping("/{id}/mastered")
    public Result<Void> markMastered(@PathVariable Long id, Authentication authentication) {
        if (authentication == null) {
            return Result.error(401, "请先登录");
        }
        Long userId = (Long) authentication.getPrincipal();
        mistakeService.markMastered(id, userId);
        return Result.success();
    }

    @Operation(summary = "添加错题")
    @PostMapping
    public Result<Void> add(@RequestBody LearningMistake mistake, Authentication authentication) {
        if (authentication == null) {
            return Result.error(401, "请先确保已登录");
        }
        Long userId = (Long) authentication.getPrincipal();
        mistakeService.addMistake(mistake, userId);
        return Result.success();
    }

    @Operation(summary = "代码运行异常自动归集到错题本（SC1-AI-03）：提取错误类型并关联知识库")
    @PostMapping("/collect-code")
    public Result<CodeMistakeCollectResult> collectCode(
            @RequestBody CodeMistakeCollectRequest req, Authentication authentication) {
        Long userId = authentication != null ? (Long) authentication.getPrincipal() : null;
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(mistakeService.collectCodeMistake(req, userId));
    }

    @Operation(summary = "错题统计")
    @GetMapping("/stats")
    public Result<Map<String, Integer>> stats(Authentication authentication) {
        Long userId = authentication != null ? (Long) authentication.getPrincipal() : null;
        Map<String, Integer> stats = new HashMap<>();
        stats.put("total", mistakeService.getTotalCount(userId));
        stats.put("mastered", mistakeService.getMasteredCount(userId));
        stats.put("pending", mistakeService.getPendingCount(userId));
        stats.put("weeklyNew", mistakeService.getWeeklyNewCount(userId));
        stats.put("dueToday", mistakeService.getDueTodayCount(userId));
        return Result.success(stats);
    }
}
