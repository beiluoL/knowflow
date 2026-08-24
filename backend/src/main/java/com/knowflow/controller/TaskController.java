package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.TaskDTO;
import com.knowflow.service.TaskService;
import com.knowflow.vo.CalendarEventVO;
import com.knowflow.vo.TaskVO;
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

import java.time.LocalDateTime;
import java.util.List;

/**
 * 前台-Things3 式任务清单接口（任务维度）：智能列表 / 按清单查询 / 增删改 / 状态切换。
 * 全部接口需登录（user_id 维度隔离）。
 */
@Slf4j
@Tag(name = "任务清单")
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    private Long uid() {
        return SecurityUtils.getCurrentUserId();
    }

    @Operation(summary = "智能列表任务（inbox/today/upcoming/someday/logbook/all），嵌套子任务")
    @GetMapping
    public Result<List<TaskVO>> list(@RequestParam(defaultValue = "inbox") String smart) {
        return Result.success(taskService.listBySmartList(uid(), smart));
    }

    @Operation(summary = "某清单 / 项目下的任务树")
    @GetMapping("/list/{listId}")
    public Result<List<TaskVO>> listByList(@PathVariable Long listId) {
        return Result.success(taskService.listByList(uid(), listId));
    }

    @Operation(summary = "按时间区间查询日历事件（start/end 必填，本地时间 ISO 格式，如 2026-08-01T00:00:00）")
    @GetMapping("/range")
    public Result<List<CalendarEventVO>> range(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long listId) {
        return Result.success(taskService.listByRange(uid(), start, end, status, listId));
    }

    @Operation(summary = "新建任务")
    @PostMapping
    public Result<Long> create(@RequestBody TaskDTO dto) {
        return Result.success(taskService.createTask(uid(), dto));
    }

    @Operation(summary = "更新任务（需传完整对象）")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody TaskDTO dto) {
        taskService.updateTask(uid(), id, dto);
        return Result.success();
    }

    @Operation(summary = "切换任务状态（0 待办 / 1 已完成）")
    @PutMapping("/{id}/status")
    public Result<Void> setStatus(@PathVariable Long id, @RequestParam Integer status) {
        taskService.setStatus(uid(), id, status);
        return Result.success();
    }

    @Operation(summary = "看板视图数据：当前用户全部顶层任务（parent_id=0）")
    @GetMapping("/board")
    public Result<List<TaskVO>> board() {
        return Result.success(taskService.listBoard(uid()));
    }

    @Operation(summary = "更新看板阶段（0 待办 / 1 进行中 / 2 已完成），自动同步完成态")
    @PutMapping("/{id}/stage")
    public Result<Void> updateStage(@PathVariable Long id, @RequestParam Integer stage) {
        taskService.updateStage(uid(), id, stage);
        return Result.success();
    }

    @Operation(summary = "删除任务及其子任务")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        taskService.deleteTask(uid(), id);
        return Result.success();
    }
}
