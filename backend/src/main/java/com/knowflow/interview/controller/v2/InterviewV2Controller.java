package com.knowflow.interview.controller.v2;
import com.knowflow.common.*;
import com.knowflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.knowflow.interview.service.v2.InterviewV2Service;
/** 身份只取安全上下文；不信任请求携带的owner、角色或模型状态。 */
@RestController @RequestMapping("/api/interview/v2/sessions") @RequiredArgsConstructor
public class InterviewV2Controller {
 private final InterviewV2Service service;
 @PostMapping public Result<?> start(@RequestBody InterviewV2Service.Start req){return Result.success(service.start(SecurityUtils.getCurrentUserId(),req));}
 @GetMapping("/{id}") public Result<?> get(@PathVariable Long id){return Result.success(service.get(SecurityUtils.getCurrentUserId(),id));}
 @PostMapping("/{id}/answers") public Result<?> answer(@PathVariable Long id,@RequestBody InterviewV2Service.Answer req){return Result.success(service.answer(SecurityUtils.getCurrentUserId(),id,req));}
 @PostMapping("/{id}/pause") public Result<?> pause(@PathVariable Long id,@RequestBody InterviewV2Service.Command req){return Result.success(service.command(SecurityUtils.getCurrentUserId(),id,"pause",req));}
 @PostMapping("/{id}/resume") public Result<?> resume(@PathVariable Long id,@RequestBody InterviewV2Service.Command req){return Result.success(service.command(SecurityUtils.getCurrentUserId(),id,"resume",req));}
 @PostMapping("/{id}/finish") public Result<?> finish(@PathVariable Long id,@RequestBody InterviewV2Service.Command req){return Result.success(service.command(SecurityUtils.getCurrentUserId(),id,"finish",req));}
 public record Reevaluation(String requestId){}
 @GetMapping("/{id}/evaluations") public Result<?> evaluations(@PathVariable Long id){return Result.success(service.evaluationHistory(SecurityUtils.getCurrentUserId(),id));}
 @PostMapping("/{id}/turns/{turn}/evaluations") public Result<?> reevaluate(@PathVariable Long id,@PathVariable Long turn,@RequestBody Reevaluation body){return Result.success(service.reevaluate(SecurityUtils.getCurrentUserId(),id,turn,body==null?null:body.requestId()));}
 @ExceptionHandler(BusinessException.class) public ResponseEntity<Result<?>> business(BusinessException e){int code=e.getCode();if(code<400||code>599)code=500;return ResponseEntity.status(code).body(Result.error(code,e.getMessage()));}
}
