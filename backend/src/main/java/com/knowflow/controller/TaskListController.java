package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.TaskListDTO;
import com.knowflow.service.TaskService;
import com.knowflow.vo.TaskListVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前台-Things3 式任务清单接口（清单维度）：领域 / 项目 / 清单的增删改查。
 * 全部接口需登录（user_id 维度隔离）。
 */
@Slf4j
@Tag(name = "任务清单-清单")
@RestController
@RequestMapping("/api/task-lists")
@RequiredArgsConstructor
public class TaskListController {

    private final TaskService taskService;

    private Long uid() {
        return SecurityUtils.getCurrentUserId();
    }

    @Operation(summary = "当前用户全部清单 / 项目 / 领域（含任务计数）")
    @GetMapping
    public Result<List<TaskListVO>> list() {
        return Result.success(taskService.listTaskLists(uid()));
    }

    @Operation(summary = "新建清单")
    @PostMapping
    public Result<Long> create(@RequestBody TaskListDTO dto) {
        return Result.success(taskService.createTaskList(uid(), dto));
    }

    @Operation(summary = "更新清单")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody TaskListDTO dto) {
        taskService.updateTaskList(uid(), id, dto);
        return Result.success();
    }

    @Operation(summary = "删除清单（其任务退回收件箱）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        taskService.deleteTaskList(uid(), id);
        return Result.success();
    }
}
