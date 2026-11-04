package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.learning.report.WeeklyReportService;
import com.knowflow.vo.WeeklyReportVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 学习周报接口。 */
@Tag(name = "学习周报接口")
@RestController
@RequestMapping("/api/learning/weekly-report")
@RequiredArgsConstructor
public class WeeklyReportController {

    private final WeeklyReportService reports;

    @Operation(summary = "获取当前周周报（不存在则自动生成）")
    @GetMapping
    public Result<WeeklyReportVO> current() {
        return Result.success(reports.current(SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "历史周报列表（最近 8 周）")
    @GetMapping("/list")
    public Result<List<WeeklyReportVO>> list() {
        return Result.success(reports.list(SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "手动生成当前周周报")
    @PostMapping("/generate")
    public Result<WeeklyReportVO> generate() {
        return Result.success(reports.generate(SecurityUtils.getCurrentUserId()));
    }
}
