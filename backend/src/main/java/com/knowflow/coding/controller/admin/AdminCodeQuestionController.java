package com.knowflow.coding.controller.admin;

import com.knowflow.common.Result;
import com.knowflow.coding.entity.CodeQuestion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员-代码题库管理：提供题目的增删改查与发布/下架能力。
 * 前端后台「题库管理」页面对接此 Controller。
 */
@Slf4j
@Tag(name = "管理员代码题库管理")
@RestController
@RequestMapping("/api/admin/code-questions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCodeQuestionController {

    private final com.knowflow.coding.service.AdminCodeQuestionApplicationService applicationService;

    @Operation(summary = "题目列表（支持按标题/难度/语言/状态筛选）")
    @GetMapping
    public Result<List<CodeQuestion>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) Integer status) {
        return applicationService.list(keyword, difficulty, language, status);
    }

    @Operation(summary = "题目详情")
    @GetMapping("/{id}")
    public Result<CodeQuestion> detail(@PathVariable Long id) {
        return applicationService.detail(id);
    }

    @Operation(summary = "新增题目")
    @PostMapping
    public Result<CodeQuestion> add(@RequestBody CodeQuestion question) {
        return applicationService.add(question);
    }

    @Operation(summary = "更新题目")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody CodeQuestion question) {
        return applicationService.update(id, question);
    }

    @Operation(summary = "删除题目")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        return applicationService.delete(id);
    }

    @Operation(summary = "发布题目")
    @PutMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        return applicationService.publish(id);
    }

    @Operation(summary = "下架题目（转为草稿）")
    @PutMapping("/{id}/unpublish")
    public Result<Void> unpublish(@PathVariable Long id) {
        return applicationService.unpublish(id);
    }
}
