package com.knowflow.interview.controller.generation;

import com.knowflow.common.*;
import com.knowflow.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import com.knowflow.interview.service.generation.InterviewRagGenerationService;
import com.knowflow.interview.service.v2.InterviewV2Service;
/** 身份只取安全上下文；生成题与任务均按服务端用户隔离。 */
@RestController
@RequestMapping("/api/interview/generation")
@RequiredArgsConstructor
public class InterviewRagGenerationController {

    private final InterviewRagGenerationService service;
    private final com.knowflow.interview.service.v2.InterviewV2Service interviews;
    public record StartInterview(java.util.List<Long> questionIds, String requestId) {}
    /** 从已审核私人题目启动原V2面试，不创建另一套会话。 */
    @PostMapping("/interviews")
    public Result<?> startInterview(@RequestBody StartInterview request) {
        if (request == null) throw new BusinessException(400, "缺少面试参数");
        Long user = SecurityUtils.getCurrentUserId();
        return Result.success(interviews.startGenerated(user, request.requestId(), service.publishedSeeds(user, request.questionIds())));
    }

    @GetMapping("/capabilities")
    public Result<?> capabilities() {
        return Result.success(service.capabilities(SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/tasks")
    public Result<?> submit(@Valid @RequestBody InterviewRagGenerationService.GenerateRequest request) {
        return Result.success(service.submit(SecurityUtils.getCurrentUserId(), request));
    }

    /** 最近任务从服务端恢复，不依赖浏览器本地存储。 */
    @GetMapping("/tasks")
    public Result<?> tasks() {
        return Result.success(service.tasks(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/tasks/{id}")
    public Result<?> task(@PathVariable Long id) {
        return Result.success(service.task(SecurityUtils.getCurrentUserId(), id));
    }

    @PostMapping("/tasks/{id}/retry")
    public Result<?> retry(@PathVariable Long id) {
        service.retry(SecurityUtils.getCurrentUserId(), id);
        return Result.success(service.task(SecurityUtils.getCurrentUserId(), id));
    }

    @GetMapping("/questions")
    public Result<?> questions(@RequestParam(required = false) String status) {
        return Result.success(service.list(SecurityUtils.getCurrentUserId(), status));
    }

    @PutMapping("/questions/{id}")
    public Result<?> update(@PathVariable Long id, @Valid @RequestBody InterviewRagGenerationService.UpdateRequest request) {
        return Result.success(service.update(SecurityUtils.getCurrentUserId(), id, request));
    }

    @PostMapping("/questions/{id}/publish")
    public Result<?> publish(@PathVariable Long id) {
        return Result.success(service.publish(SecurityUtils.getCurrentUserId(), id));
    }

    @PostMapping("/questions/{id}/archive")
    public Result<?> archive(@PathVariable Long id) {
        return Result.success(service.archive(SecurityUtils.getCurrentUserId(), id));
    }

    @DeleteMapping("/questions/{id}")
    public Result<?> remove(@PathVariable Long id) {
        service.remove(SecurityUtils.getCurrentUserId(), id);
        return Result.success(Map.of("removed", id));
    }

    @PostMapping("/questions/bulk")
    public Result<?> bulk(@Valid @RequestBody InterviewRagGenerationService.BulkRequest request) {
        return Result.success(service.bulk(SecurityUtils.getCurrentUserId(), request));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> business(BusinessException e) {
        int code = e.getCode();
        if (code < 400 || code > 599) code = 500;
        return ResponseEntity.status(code).body(Result.error(code, e.getMessage()));
    }
}
