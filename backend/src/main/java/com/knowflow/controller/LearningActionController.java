package com.knowflow.controller;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.learning.actions.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/learning/action-plans") @RequiredArgsConstructor
public class LearningActionController {
 private final LearningDashboardService dashboard;
 private final DailyLearningPlanService plans;private final LearningContextService context;
 @GetMapping("/dashboard") public Result<?> dashboard(@RequestParam(defaultValue="60") int minutes,@RequestParam(defaultValue="Asia/Shanghai") String zoneId){return Result.success(dashboard.read(SecurityUtils.getCurrentUserId(),minutes,zoneId));}
 @PostMapping("/daily/preview") public Result<PlanModels.Draft> preview(@RequestBody PlanModels.Request r){return Result.success(plans.preview(SecurityUtils.getCurrentUserId(),r));}
 @PostMapping("/daily/changes") public Result<PlanModels.Draft> changes(@RequestBody PlanModels.Request r){return Result.success(plans.previewChanges(SecurityUtils.getCurrentUserId(),r));}
 @PostMapping("/context") public Result<PlanModels.Context> context(@RequestBody PlanModels.Request r){return Result.success(context.read(SecurityUtils.getCurrentUserId(),plans.normalize(r)));}
 @GetMapping("/tools") public Result<?> tools(){SecurityUtils.getCurrentUserId();return Result.success(LearningActionTools.schemas());}
 @GetMapping("/{id}") public Result<PlanModels.Draft> get(@PathVariable String id){return Result.success(plans.get(SecurityUtils.getCurrentUserId(),id));}
 @PostMapping("/{id}/regenerate") public Result<PlanModels.Draft> regenerate(@PathVariable String id,@RequestBody PlanModels.Request r){return Result.success(plans.regenerate(SecurityUtils.getCurrentUserId(),id,r));}
 @PostMapping("/{id}/confirm") public Result<PlanModels.Draft> confirm(@PathVariable String id,@RequestBody PlanModels.ConfirmRequest r){return Result.success(plans.confirm(SecurityUtils.getCurrentUserId(),id,r));}
 @DeleteMapping("/{id}") public Result<Void> cancel(@PathVariable String id){plans.cancel(SecurityUtils.getCurrentUserId(),id);return Result.success();}
}
