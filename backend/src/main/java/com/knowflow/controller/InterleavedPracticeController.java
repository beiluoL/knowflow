package com.knowflow.controller;
import com.knowflow.common.*;import com.knowflow.exception.BusinessException;import com.knowflow.learning.adaptive.InterleavedPracticeService;import lombok.RequiredArgsConstructor;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/learning/interleaved-practice") @RequiredArgsConstructor @ConditionalOnProperty(name={"knowflow.product.adaptive-learning.enabled","knowflow.product.private-quiz.enabled"},havingValue="true")
public class InterleavedPracticeController {
 private final InterleavedPracticeService service;private long user(){return SecurityUtils.getCurrentUserId();}
 @GetMapping("/pool")public Result<?> pool(){return Result.success(service.pool(user()));}
 @GetMapping public Result<?> history(){return Result.success(service.history(user()));}
 @PostMapping public Result<?> start(@RequestBody InterleavedPracticeService.Start r){return Result.success(service.start(user(),r));}
 @GetMapping("/{id}")public Result<?> get(@PathVariable long id){return Result.success(service.get(user(),id));}
 @PostMapping("/{id}/submit")public Result<?> submit(@PathVariable long id,@RequestBody InterleavedPracticeService.Answers r){return Result.success(service.submit(user(),id,r));}
 @ExceptionHandler(BusinessException.class)public ResponseEntity<Result<?>> business(BusinessException e){int c=e.getCode();if(c<400||c>599)c=500;return ResponseEntity.status(c).body(Result.error(c,e.getMessage()));}
}
