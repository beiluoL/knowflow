package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.TaskTagDTO;
import com.knowflow.service.TaskService;
import com.knowflow.vo.TaskTagVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 任务标签接口（Things3 式标签）：标签增删改查，按用户隔离。
 */
@Tag(name = "任务清单-标签")
@RestController
@RequestMapping("/api/task-tags")
@RequiredArgsConstructor
public class TaskTagController {

    private final TaskService taskService;

    private Long uid() {
        return SecurityUtils.getCurrentUserId();
    }

    @Operation(summary = "当前用户全部标签（含关联任务计数）")
    @GetMapping
    public Result<List<TaskTagVO>> list() {
        return Result.success(taskService.listTags(uid()));
    }

    @Operation(summary = "新建标签")
    @PostMapping
    public Result<Long> create(@RequestBody TaskTagDTO dto) {
        return Result.success(taskService.createTag(uid(), dto));
    }

    @Operation(summary = "更新标签")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody TaskTagDTO dto) {
        taskService.updateTag(uid(), id, dto);
        return Result.success();
    }

    @Operation(summary = "删除标签（连带清理关联关系）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        taskService.deleteTag(uid(), id);
        return Result.success();
    }
}
