package com.knowflow.controller;
import com.knowflow.common.*;import com.knowflow.exception.BusinessException;import com.knowflow.learning.feedback.ProductMockInterviewService;import jakarta.validation.Valid;import lombok.RequiredArgsConstructor;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/learning/mock-interviews") @RequiredArgsConstructor
@ConditionalOnProperty(name={"knowflow.product.interview-learning.enabled","knowflow.product.private-quiz.enabled","knowflow.product.tutor.enabled","knowflow.product.tutor-coaching.enabled"},havingValue="true")
public class ProductMockInterviewController {
 private final ProductMockInterviewService service;
 @GetMapping("/topics") public Result<?> topics(){return Result.success(service.topics(SecurityUtils.getCurrentUserId()));}
 @PostMapping public Result<?> start(@Valid @RequestBody ProductMockInterviewService.Start input){return Result.success(service.start(SecurityUtils.getCurrentUserId(),input));}
 @ExceptionHandler(BusinessException.class)public ResponseEntity<Result<?>> business(BusinessException e){int code=e.getCode();if(code<400||code>599)code=500;return ResponseEntity.status(code).body(Result.error(code,e.getMessage()));}
}
