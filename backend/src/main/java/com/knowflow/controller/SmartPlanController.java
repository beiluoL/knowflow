package com.knowflow.controller;
import com.knowflow.common.*;import com.knowflow.exception.BusinessException;import com.knowflow.learning.actions.*;import lombok.RequiredArgsConstructor;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/learning/action-plans/smart") @RequiredArgsConstructor @ConditionalOnProperty(name="knowflow.product.adaptive-learning.enabled",havingValue="true")
public class SmartPlanController {
 private final DailyLearningPlanService service;private long user(){return SecurityUtils.getCurrentUserId();}
 @GetMapping("/tasks")public Result<?> tasks(PlanModels.Request request){return Result.success(service.smartTasks(user(),request));}
 @PostMapping("/tasks/{id}/settings")public Result<?> settings(@PathVariable Long id,@RequestBody DailyLearningPlanService.Settings input){service.smartSettings(user(),id,input);return Result.success();}
 @GetMapping("/history")public Result<?> history(PlanModels.Request request){return Result.success(service.smartHistory(user(),request));}
 private PlanModels.Request smart(PlanModels.Request r){r.setSmartReorder(true);r.setUseAi(false);return r;}
 @PostMapping("/preview")public Result<?> preview(@RequestBody PlanModels.Request r){return Result.success(service.previewChanges(user(),smart(r)));}
 @GetMapping("/{id}")public Result<?> get(@PathVariable String id){return Result.success(service.getSmart(user(),id));}
 @PostMapping("/{id}/confirm")public Result<?> confirm(@PathVariable String id,@RequestBody PlanModels.ConfirmRequest r){service.getSmart(user(),id);return Result.success(service.confirm(user(),id,r));}
 @PostMapping("/{id}/regenerate")public Result<?> regenerate(@PathVariable String id,@RequestBody PlanModels.Request r){service.getSmart(user(),id);return Result.success(service.regenerate(user(),id,smart(r)));}
 @DeleteMapping("/{id}")public Result<?> cancel(@PathVariable String id){service.getSmart(user(),id);service.cancel(user(),id);return Result.success();}
 @ExceptionHandler(BusinessException.class)public ResponseEntity<Result<?>> business(BusinessException e){int c=e.getCode();if(c<400||c>599)c=500;return ResponseEntity.status(c).body(Result.error(c,e.getMessage()));}
}
