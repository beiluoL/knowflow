package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.PlanGenerateDTO;
import com.knowflow.service.LearningPlanService;
import com.knowflow.vo.LearningPlanVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

/**
 * F3 学习计划智能编排：REST 接口。
 * <p>
 * 端点：
 * - POST /api/learning/plan/generate   批量生成范围计划
 * - GET  /api/learning/plan/today      懒加载今日计划
 * - GET  /api/learning/plan/calendar.ics  导出 ICS 日历
 */
@Slf4j
@Tag(name = "学习计划接口")
@RestController
@RequestMapping("/api/learning/plan")
@RequiredArgsConstructor
public class LearningPlanController {

    private final LearningPlanService planService;

    /**
     * 生成范围计划。
     *
     * @param dto startDate（默认下周一）/ days（默认 7 / ≤30）/ force（默认 false）
     * @return generatedDays 实际生成天数（force=false 时同日期已存在则跳过）
     */
    @Operation(summary = "生成范围学习计划（默认下一周 7 天）")
    @PostMapping("/generate")
    public Result<Map<String, Integer>> generatePlan(@RequestBody(required = false) PlanGenerateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        int generated = planService.generate(dto, userId);
        return Result.success(Map.of("generatedDays", generated));
    }

    /**
     * 获取今日计划（无计划则同步生成一次）。
     */
    @Operation(summary = "获取今日学习计划（懒生成 + 实时完成状态）")
    @GetMapping("/today")
    public Result<LearningPlanVO> getTodayPlan() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(planService.getTodayPlan(userId));
    }

    /**
     * 导出 ICS 日历文件。
     *
     * @param date  基准日期（默认今天）
     * @param range 范围天数（默认 1，上限 30）
     */
    @Operation(summary = "导出学习计划 ICS 日历文件")
    @GetMapping("/calendar.ics")
    public void exportCalendar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer range,
            HttpServletResponse response) throws IOException {
        Long userId = SecurityUtils.getCurrentUserId();
        String ics = planService.exportCalendarIcs(userId, date, range);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/calendar;charset=utf-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"knowflow-plan.ics\"; filename*=UTF-8''knowflow-plan.ics");
        response.setHeader("Cache-Control", "no-store, no-cache");
        response.setContentLength(ics.getBytes(StandardCharsets.UTF_8).length);
        try (Writer w = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8)) {
            w.write(ics);
            w.flush();
        }
    }
}
