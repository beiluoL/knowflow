package com.knowflow.interview.controller.career.tailoring;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import com.knowflow.interview.service.career.tailoring.CareerTailoringService;
/** 简历定制工作室 API；所有任务只属于当前用户。 */
@RestController
@RequestMapping("/api/interview/career/tailoring")
@RequiredArgsConstructor
public class CareerTailoringController {

    private final CareerTailoringService service;

    public record GenerateRequest(long resumeId, Integer resumeVersion, long jobId, Integer jobVersion,
                                  String mode, Boolean allowModelProcessing) { }

    public record ReviewRequest(List<String> acceptedIds) { }

    @PostMapping("/tasks")
    public Result<?> generate(@RequestBody GenerateRequest body) {
        if (body == null) throw new BusinessException(400, "缺少定制参数");
        return Result.success(service.generate(SecurityUtils.getCurrentUserId(),
                body.resumeId(), body.resumeVersion(), body.jobId(), body.jobVersion(),
                body.mode(), Boolean.TRUE.equals(body.allowModelProcessing())));
    }

    @GetMapping("/tasks")
    public Result<?> list() {
        return Result.success(service.list(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/tasks/{id}")
    public Result<?> detail(@PathVariable long id) {
        return Result.success(service.detail(SecurityUtils.getCurrentUserId(), id));
    }

    @GetMapping("/tasks/{id}/diff")
    public Result<?> diff(@PathVariable long id) {
        return Result.success(service.detail(SecurityUtils.getCurrentUserId(), id));
    }

    @PostMapping("/tasks/{id}/review")
    public Result<?> review(@PathVariable long id, @RequestBody ReviewRequest body) {
        if (body == null) throw new BusinessException(400, "缺少审核结果");
        return Result.success(service.review(SecurityUtils.getCurrentUserId(), id, body.acceptedIds()));
    }

    @PostMapping("/tasks/{id}/create-version")
    public Result<?> createVersion(@PathVariable long id) {
        return Result.success(service.createVersion(SecurityUtils.getCurrentUserId(), id));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> error(BusinessException error) {
        int code = error.getCode();
        if (code < 400 || code > 599) code = 500;
        return ResponseEntity.status(code).body(Result.error(code, error.getMessage()));
    }
}
