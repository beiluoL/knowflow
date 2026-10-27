package com.knowflow.controller;
import com.knowflow.common.*;import com.knowflow.exception.BusinessException;import com.knowflow.learning.adaptive.GraphInsightService;import lombok.RequiredArgsConstructor;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/learning/graph-insights") @RequiredArgsConstructor @ConditionalOnProperty(name="knowflow.product.adaptive-learning.enabled",havingValue="true")
public class GraphInsightController {
 private final GraphInsightService service;
 @GetMapping("/{knowledgeId}")public Result<?> detail(@PathVariable long knowledgeId){if(knowledgeId<1)throw new BusinessException(400,"知识点编号无效");return Result.success(service.detail(SecurityUtils.getCurrentUserId(),knowledgeId));}
 @ExceptionHandler(BusinessException.class) public ResponseEntity<Result<?>> business(BusinessException e){int code=e.getCode();if(code<400||code>599)code=500;return ResponseEntity.status(code).body(Result.error(code,e.getMessage()));}
}
