package com.knowflow.controller;
import com.knowflow.common.*;import com.knowflow.exception.BusinessException;import com.knowflow.learning.adaptive.AdaptivePathService;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import lombok.RequiredArgsConstructor;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/learning/adaptive-paths/{path}") @RequiredArgsConstructor @ConditionalOnProperty(name={"knowflow.product.adaptive-learning.enabled","knowflow.product.interview-learning.enabled","knowflow.product.private-quiz.enabled","knowflow.product.tutor.enabled","knowflow.product.tutor-coaching.enabled"},havingValue="true")
public class AdaptivePathController {
 private final AdaptivePathService service;private long user(){return SecurityUtils.getCurrentUserId();}
 public record Binding(@NotNull @Positive Long annotationId,@NotNull @Positive Long knowledgeId){}
 public record Choice(@NotNull Boolean accept){}
 @GetMapping public Result<?> detail(@PathVariable long path){return Result.success(service.detail(user(),path));}
 @PostMapping("/bindings")public Result<?> bind(@PathVariable long path,@Valid @RequestBody Binding input){return Result.success(service.bind(user(),path,input.annotationId(),input.knowledgeId()));}
 @PostMapping("/preview")public Result<?> preview(@PathVariable long path,@Valid @RequestBody AdaptivePathService.Preview input){return Result.success(service.preview(user(),path,input));}
 @PostMapping("/revisions/{id}/decision")public Result<?> confirm(@PathVariable long path,@PathVariable long id,@Valid @RequestBody Choice input){return Result.success(service.confirm(user(),path,id,input.accept()));}
 @PostMapping("/defer")public Result<?> defer(@PathVariable long path,@Valid @RequestBody AdaptivePathService.Decision input){return Result.success(service.defer(user(),path,input));}
 @ExceptionHandler(BusinessException.class)public ResponseEntity<Result<?>> business(BusinessException e){int code=e.getCode();if(code<400||code>599)code=500;return ResponseEntity.status(code).body(Result.error(code,e.getMessage()));}
}
