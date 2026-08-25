package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.HabitDTO;
import com.knowflow.service.HabitService;
import com.knowflow.vo.HabitVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 习惯打卡 REST 接口：习惯 CRUD、今日打卡、撤销打卡与进度查询。
 */
@Tag(name = "习惯打卡接口")
@RestController
@RequestMapping("/api/habits")
@RequiredArgsConstructor
public class HabitController {

    private final HabitService habitService;

    private Long uid() {
        return SecurityUtils.getCurrentUserId();
    }

    @Operation(summary = "当前用户全部启用习惯（含今日打卡与进度）")
    @GetMapping
    public Result<List<HabitVO>> list() {
        return Result.success(habitService.listHabits(uid()));
    }

    @Operation(summary = "新建习惯")
    @PostMapping
    public Result<Long> create(@RequestBody HabitDTO dto) {
        return Result.success(habitService.createHabit(uid(), dto));
    }

    @Operation(summary = "更新习惯")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody HabitDTO dto) {
        habitService.updateHabit(uid(), id, dto);
        return Result.success();
    }

    @Operation(summary = "删除习惯")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        habitService.deleteHabit(uid(), id);
        return Result.success();
    }

    @Operation(summary = "今日打卡（幂等累加，每次 count+1）")
    @PostMapping("/{id}/checkin")
    public Result<HabitVO> checkIn(@PathVariable Long id) {
        return Result.success(habitService.checkIn(uid(), id));
    }

    @Operation(summary = "撤销今日最近一次打卡")
    @PostMapping("/{id}/undo")
    public Result<HabitVO> undo(@PathVariable Long id) {
        return Result.success(habitService.undoCheckIn(uid(), id));
    }

    @Operation(summary = "获取单个习惯详情（含进度）")
    @GetMapping("/{id}")
    public Result<HabitVO> get(@PathVariable Long id) {
        return Result.success(habitService.getHabit(uid(), id));
    }
}
