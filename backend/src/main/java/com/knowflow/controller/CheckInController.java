package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.service.CheckInService;
import com.knowflow.vo.CheckInResultVO;
import com.knowflow.vo.CheckInStatusVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 每日打卡 REST 接口：打卡与打卡状态查询。 */
@Tag(name = "每日打卡接口")
@RestController
@RequestMapping("/api/checkin")
@RequiredArgsConstructor
public class CheckInController {

    private final CheckInService checkInService;

    @Operation(summary = "今日打卡")
    @PostMapping
    public Result<CheckInResultVO> checkIn() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(checkInService.checkIn(userId));
    }

    @Operation(summary = "打卡状态（今日是否已打卡、连续天数、本月日历）")
    @GetMapping("/status")
    public Result<CheckInStatusVO> status() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(checkInService.getStatus(userId));
    }
}
