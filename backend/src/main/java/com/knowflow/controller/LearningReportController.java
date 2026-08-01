package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.service.LearningReportService;
import com.knowflow.vo.LearningReportVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 学习报告 REST 接口：按周期聚合当前用户的学习数据。 */
@Tag(name = "学习报告接口")
@RestController
@RequestMapping("/api/learning/report")
@RequiredArgsConstructor
public class LearningReportController {

    private final LearningReportService reportService;

    @Operation(summary = "获取学习报告（周期：week/month/all，默认 month）")
    @GetMapping
    public Result<LearningReportVO> getReport(@RequestParam(defaultValue = "month") String period) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(reportService.getReport(userId, period));
    }
}
