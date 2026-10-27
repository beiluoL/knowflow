package com.knowflow.interview.controller;

import com.knowflow.common.PageResult;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.interview.service.MockInterviewService;
import com.knowflow.interview.vo.InterviewVos.AnswerRequest;
import com.knowflow.interview.vo.InterviewVos.SessionVO;
import com.knowflow.interview.vo.InterviewVos.StartRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模拟面试（Phase 3H）。基础模式完整可用、零 AI 依赖；
 * 身份一律取 SecurityUtils，会话归属在服务层校验。
 */
@Tag(name = "模拟面试")
@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class MockInterviewController {

    private final MockInterviewService mockInterviewService;

    @Operation(summary = "面试方向目录（预置题库白名单）")
    @GetMapping("/tracks")
    public Result<?> tracks() {
        return Result.success(mockInterviewService.listTracks());
    }

    @GetMapping("/tracks/{code}/questions")
    public Result<?> questionTitles(@org.springframework.web.bind.annotation.PathVariable String code){
        return Result.success(mockInterviewService.listQuestions(SecurityUtils.getCurrentUserId(),code));
    }

    @Operation(summary = "开始一场基础模式模拟面试")
    @PostMapping("/sessions")
    public Result<SessionVO> start(@RequestBody StartRequest req) {
        return Result.success(mockInterviewService.start(SecurityUtils.getCurrentUserId(), req));
    }

    @Operation(summary = "本人面试会话详情（题目快照 + 作答进度）")
    @GetMapping("/sessions/{id}")
    public Result<SessionVO> detail(@PathVariable Long id) {
        return Result.success(mockInterviewService.getOwn(SecurityUtils.getCurrentUserId(), id));
    }

    @Operation(summary = "保存/更新某题作答与自评（幂等覆盖）")
    @PutMapping("/sessions/{id}/answers")
    public Result<SessionVO> saveAnswer(@PathVariable Long id, @RequestBody AnswerRequest req) {
        return Result.success(mockInterviewService.saveAnswer(SecurityUtils.getCurrentUserId(), id, req));
    }

    @Operation(summary = "结束面试并计算自评均分与实际用时")
    @PostMapping("/sessions/{id}/finish")
    public Result<SessionVO> finish(@PathVariable Long id) {
        return Result.success(mockInterviewService.finish(SecurityUtils.getCurrentUserId(), id));
    }

    @Operation(summary = "本人历史面试会话（分页）")
    @GetMapping("/sessions")
    public Result<PageResult<?>> mine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(mockInterviewService.mySessions(SecurityUtils.getCurrentUserId(), page, pageSize));
    }
}
