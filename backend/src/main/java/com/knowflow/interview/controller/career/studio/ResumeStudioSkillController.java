package com.knowflow.interview.controller.career.studio;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.knowflow.interview.service.career.studio.ResumeStudioSkillService;
/** 简历专项规则 API，身份只来自服务端认证上下文。 */
@RestController
@RequestMapping("/api/interview/career/studio")
@RequiredArgsConstructor
public class ResumeStudioSkillController {
    private final ResumeStudioSkillService service;
    /** 只接受资源与版本，不接受调用方指定身份或模型授权。 */
    public record CheckRequest(long resumeId, Integer resumeVersion, String skillId) { }

    @GetMapping("/skills")
    public Result<?> skills() {
        return Result.success(service.skills(SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/check")
    public Result<?> check(@RequestBody CheckRequest request) {
        if (request == null) throw new BusinessException(400, "缺少体检参数");
        return Result.success(service.check(SecurityUtils.getCurrentUserId(), request.resumeId(),
                request.resumeVersion(), request.skillId()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> error(BusinessException error) {
        int code = error.getCode();
        if (code < 400 || code > 599) code = 500;
        return ResponseEntity.status(code).body(Result.error(code, error.getMessage()));
    }
}
