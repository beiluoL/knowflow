package com.knowflow.controller;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.seed.ContentStudioThreadPoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequiredArgsConstructor
@ConditionalOnProperty(name="knowflow.content-studio.qa.enabled",havingValue="true")
@RequestMapping("/api/learning/content-studio/qa/threadpool")
public class ContentStudioThreadPoolQaController {
 private final ContentStudioThreadPoolService service;
 @GetMapping public Result<Map<String,Object>> dryRun(){return Result.success(service.dryRun(SecurityUtils.getCurrentUserId()));}
 @PostMapping public Result<Map<String,Object>> apply(@RequestBody Map<String,String> request){return Result.success(service.apply(SecurityUtils.getCurrentUserId(),request.get("expectedPlanHash")));}
 @PostMapping("/rollback") public Result<Map<String,Object>> rollback(){return Result.success(service.rollback(SecurityUtils.getCurrentUserId()));}
}
