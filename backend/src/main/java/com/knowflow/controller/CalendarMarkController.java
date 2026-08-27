package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.MemorialDTO;
import com.knowflow.service.CalendarMarkService;
import com.knowflow.vo.DateMarkVO;
import com.knowflow.vo.MemorialVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 前台-日历日期标记接口：法定节假日（休/班）、中国传统节日、现代节日、自定义纪念日。
 * 全部接口需登录（user_id 维度隔离）；纪念日 CRUD 供设置页使用。
 */
@Slf4j
@Tag(name = "日历标记")
@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarMarkController {

    private final CalendarMarkService calendarMarkService;

    private Long uid() {
        return SecurityUtils.getCurrentUserId();
    }

    @Operation(summary = "按日期区间查询日期标记（start/end 必填，yyyy-MM-dd）")
    @GetMapping("/marks")
    public Result<List<DateMarkVO>> marks(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return Result.success(calendarMarkService.listMarks(uid(), start, end));
    }

    @Operation(summary = "纪念日列表（当前登录用户）")
    @GetMapping("/memorials")
    public Result<List<MemorialVO>> memorials() {
        return Result.success(calendarMarkService.listMemorials(uid()));
    }

    @Operation(summary = "新建纪念日（fixed 固定日期 / yearly 每年重复）")
    @PostMapping("/memorials")
    public Result<Long> create(@RequestBody MemorialDTO dto) {
        return Result.success(calendarMarkService.createMemorial(dto, uid()));
    }

    @Operation(summary = "更新纪念日")
    @PutMapping("/memorials/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody MemorialDTO dto) {
        calendarMarkService.updateMemorial(id, dto, uid());
        return Result.success();
    }

    @Operation(summary = "删除纪念日")
    @DeleteMapping("/memorials/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        calendarMarkService.deleteMemorial(id, uid());
        return Result.success();
    }
}
