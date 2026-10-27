package com.knowflow.controller;
import com.knowflow.common.*;import com.knowflow.exception.BusinessException;import com.knowflow.learning.adaptive.EvidenceReportService;import lombok.RequiredArgsConstructor;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/learning/evidence-report") @RequiredArgsConstructor @ConditionalOnProperty(name={"knowflow.product.adaptive-learning.enabled","knowflow.product.interview-learning.enabled","knowflow.product.private-quiz.enabled","knowflow.product.tutor.enabled","knowflow.product.tutor-coaching.enabled"},havingValue="true")
public class EvidenceReportController {
 private final EvidenceReportService service;
 @GetMapping public Result<?> report(@RequestParam(required=false)Long knowledgeId){return Result.success(service.report(SecurityUtils.getCurrentUserId(),knowledgeId));}
 @ExceptionHandler(BusinessException.class)public ResponseEntity<Result<?>> business(BusinessException e){int c=e.getCode();if(c<400||c>599)c=500;return ResponseEntity.status(c).body(Result.error(c,e.getMessage()));}
}
