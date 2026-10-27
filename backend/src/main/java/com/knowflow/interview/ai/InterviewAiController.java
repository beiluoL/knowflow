package com.knowflow.interview.ai;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/interview/sessions/{sessionId}/ai-turns")
public class InterviewAiController {
 private final InterviewAiService service;
 @PostMapping public Result<?> start(@PathVariable Long sessionId,@RequestBody InterviewAiService.StartRequest body){return Result.success(service.start(SecurityUtils.getCurrentUserId(),sessionId,body));}
 @GetMapping public Result<?> list(@PathVariable Long sessionId,@RequestParam String questionId){return Result.success(service.list(SecurityUtils.getCurrentUserId(),sessionId,questionId));}
 @PostMapping("/{id}/cancel") public Result<?> cancel(@PathVariable Long sessionId,@PathVariable Long id){return Result.success(service.cancel(SecurityUtils.getCurrentUserId(),sessionId,id));}
}
