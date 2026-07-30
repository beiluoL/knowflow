package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.ChallengeSubmitDTO;
import com.knowflow.service.CodeChallengeService;
import com.knowflow.vo.ChallengeDetailVO;
import com.knowflow.vo.ChallengeRankVO;
import com.knowflow.vo.ChallengeStatsVO;
import com.knowflow.vo.ChallengeSubmitResultVO;
import com.knowflow.vo.ChallengeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 前台-编程挑战接口：赛道列表/详情/排行榜为公开只读（匿名可访问，登录后附带个人进度），
 * 提交关卡与个人统计需登录。
 */
@Slf4j
@Tag(name = "编程挑战")
@RestController
@RequestMapping("/api/challenges")
@RequiredArgsConstructor
public class CodeChallengeController {

    private final CodeChallengeService challengeService;

    @Operation(summary = "已发布挑战赛道列表（登录后附带我的进度）")
    @GetMapping
    public Result<List<ChallengeVO>> list() {
        return Result.success(challengeService.listChallenges(SecurityUtils.getCurrentUserIdNullable()));
    }

    @Operation(summary = "排行榜（challengeId 为空返回总榜）")
    @GetMapping("/leaderboard")
    public Result<List<ChallengeRankVO>> leaderboard(
            @RequestParam(required = false) Long challengeId,
            @RequestParam(required = false, defaultValue = "20") Integer limit) {
        return Result.success(challengeService.leaderboard(challengeId, limit));
    }

    @Operation(summary = "我的挑战累计统计")
    @GetMapping("/my/stats")
    public Result<ChallengeStatsVO> myStats() {
        return Result.success(challengeService.getMyStats(SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "挑战详情（含关卡地图与我的各关状态）")
    @GetMapping("/{id}")
    public Result<ChallengeDetailVO> detail(@PathVariable Long id) {
        return Result.success(challengeService.getDetail(id, SecurityUtils.getCurrentUserIdNullable()));
    }

    @Operation(summary = "提交关卡：判定通关并计算星级/积分/解锁")
    @PostMapping("/{challengeId}/levels/{levelId}/submit")
    public Result<ChallengeSubmitResultVO> submit(
            @PathVariable Long challengeId,
            @PathVariable Long levelId,
            @RequestBody ChallengeSubmitDTO dto) {
        return Result.success(challengeService.submitLevel(
                challengeId, levelId, dto, SecurityUtils.getCurrentUserId()));
    }
}
