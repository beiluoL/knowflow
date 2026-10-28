package com.knowflow.coding.controller;

import com.knowflow.coding.workspace.JavaPracticeService;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/** 复用原CodeBank命名空间和原Tutor；所有身份只从认证上下文获取。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/code-bank")
@ConditionalOnProperty(
  name = "knowflow.product.java-practice.enabled",
  havingValue = "true"
)
public class JavaPracticeController {

  private final JavaPracticeService workspace;

  @GetMapping("/problems/{id}/workspace")
  public Result<?> get(@PathVariable long id) {
    return Result.success(
      workspace.workspace(SecurityUtils.getCurrentUserId(), id)
    );
  }

  @PostMapping("/problems/{id}/workspace/drafts")
  public Result<?> save(
    @PathVariable long id,
    @RequestBody JavaPracticeService.DraftInput input
  ) {
    return Result.success(
      workspace.save(SecurityUtils.getCurrentUserId(), id, input)
    );
  }

  @GetMapping("/problems/{id}/workspace/drafts/{revision}")
  public Result<?> version(@PathVariable long id, @PathVariable long revision) {
    return Result.success(
      workspace.draft(SecurityUtils.getCurrentUserId(), id, revision)
    );
  }

  @PostMapping("/problems/{id}/workspace/jobs")
  public Result<?> submit(
    @PathVariable long id,
    @RequestBody JavaPracticeService.JobInput input
  ) {
    return Result.success(
      workspace.submit(SecurityUtils.getCurrentUserId(), id, input)
    );
  }

  @GetMapping("/workspace/jobs/{id}")
  public Result<?> job(@PathVariable long id) {
    return Result.success(workspace.job(SecurityUtils.getCurrentUserId(), id));
  }

  @PostMapping("/workspace/jobs/{id}/retry")
  public Result<?> retry(
    @PathVariable long id,
    @RequestBody JavaPracticeService.RetryInput input
  ) {
    return Result.success(
      workspace.retry(SecurityUtils.getCurrentUserId(), id, input)
    );
  }

  @PostMapping("/workspace/jobs/{id}/tutor")
  public Result<?> tutor(@PathVariable long id) {
    return Result.success(
      workspace.openTutor(SecurityUtils.getCurrentUserId(), id)
    );
  }
}
