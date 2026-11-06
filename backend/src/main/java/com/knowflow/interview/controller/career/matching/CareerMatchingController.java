package com.knowflow.interview.controller.career.matching;

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

import com.knowflow.interview.service.career.matching.MultiResumeMatchService;
/** 多简历 / 多岗位匹配 API；单条匹配仍走 /matches。 */
@RestController
@RequestMapping("/api/interview/career/matches")
@RequiredArgsConstructor
public class CareerMatchingController {

    private final MultiResumeMatchService service;

    public record MultiResumeRequest(long jobId, Integer jobVersion) { }

    public record BatchRequest(long resumeId, Integer resumeVersion, List<Long> jobIds) { }

    @PostMapping("/multi-resume")
    public Result<?> multiResume(@RequestBody MultiResumeRequest body) {
        if (body == null) throw new BusinessException(400, "缺少岗位参数");
        return Result.success(service.matchJobWithResumes(
                SecurityUtils.getCurrentUserId(), body.jobId(), body.jobVersion()));
    }

    @PostMapping("/batch")
    public Result<?> batch(@RequestBody BatchRequest body) {
        if (body == null) throw new BusinessException(400, "缺少批量匹配参数");
        return Result.success(service.matchResumeWithJobs(
                SecurityUtils.getCurrentUserId(), body.resumeId(), body.resumeVersion(), body.jobIds()));
    }

    @GetMapping("/{id}/gaps")
    public Result<?> gaps(@PathVariable long id) {
        return Result.success(service.gaps(SecurityUtils.getCurrentUserId(), id));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> error(BusinessException error) {
        int code = error.getCode();
        if (code < 400 || code > 599) code = 500;
        return ResponseEntity.status(code).body(Result.error(code, error.getMessage()));
    }
}
