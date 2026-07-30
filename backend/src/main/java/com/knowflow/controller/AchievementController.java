package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.service.AchievementService;
import com.knowflow.vo.AchievementPageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 成就/勋章系统接口：获取成就列表（含进度与解锁状态）、概览统计、最近解锁时间线。
 */
@Tag(name = "成就系统接口")
@RestController
@RequestMapping("/api/achievements")
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;

    @Operation(summary = "我的成就页（列表 + 统计 + 最近解锁）")
    @GetMapping
    public Result<AchievementPageVO> myAchievements() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(achievementService.getMyAchievements(userId));
    }
}
