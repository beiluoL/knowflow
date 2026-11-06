package com.knowflow.catalog.controller;

import com.knowflow.catalog.service.QuestionCatalogService;
import com.knowflow.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 提供统一题目目录查询、批量状态、审计和筛选接口。 */
@RestController
@RequestMapping("/api/admin/question-catalog")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class QuestionCatalogController {
  private final QuestionCatalogService service;
  private final com.knowflow.coding.contract.CodeJudgeStatusQueryPort judgeStatus;

  /**
   * 判题能力状态。
   *
   * <p>`productionSandboxReady` 恒为 false：当前判题实现（disabled/mock/local-host）均未通过 独立 Runner
   * 隔离验收，不能作为生产在线判题。此常量与 `CodeJudgeGateway#isolatedExecutionVerified` 的口径一致。
   *
   * <p>依赖方向：通过 coding 模块的 `CodeJudgeStatusQueryPort` 契约读取，不直接依赖判题实现。
   */
  @GetMapping("/judge-status")
  public Result<?> judgeStatus() {
    return Result.success(
        java.util.Map.of(
            "mode",
            judgeStatus.mode(),
            "executionEnabled",
            judgeStatus.executionEnabled(),
            "productionSandboxReady",
            judgeStatus.productionSandboxReady()));
  }

  @GetMapping
  public Result<?> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String questionType,
      @RequestParam(required = false) String sourceType,
      @RequestParam(required = false) Integer difficulty,
      @RequestParam(required = false) Integer status,
      @RequestParam(required = false) String reviewStatus,
      @RequestParam(required = false) Long categoryId,
      @RequestParam(required = false) String topicCode) {
    return Result.success(
        service.page(
            page,
            pageSize,
            keyword,
            questionType,
            sourceType,
            difficulty,
            status,
            reviewStatus,
            categoryId,
            topicCode));
  }

  public record BatchStatus(java.util.List<String> catalogIds, boolean published) {}

  @PostMapping("/batch-status")
  public Result<?> batch(@RequestBody BatchStatus body) {
    service.batchStatus(body.catalogIds(), body.published());
    return Result.success();
  }

  @GetMapping("/audit")
  public Result<?> audit(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
    return Result.success(service.auditPage(page, pageSize));
  }

  @GetMapping("/facets")
  public Result<?> facets() {
    return Result.success(service.facets());
  }

  @PutMapping("/{catalogId}/basic")
  public Result<?> basic(
      @PathVariable String catalogId, @RequestBody QuestionCatalogService.BasicEdit body) {
    service.editBasic(catalogId, body);
    return Result.success();
  }

  @GetMapping("/{catalogId}")
  public Result<?> detail(@PathVariable String catalogId) {
    return Result.success(service.detail(catalogId));
  }
}
