package com.knowflow.controller;
import com.knowflow.common.*;import com.knowflow.exception.BusinessException;import com.knowflow.learning.feedback.ErrorAnnotationService;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import lombok.RequiredArgsConstructor;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController @RequestMapping("/api/learning/error-annotations") @RequiredArgsConstructor @ConditionalOnProperty(name={"knowflow.product.interview-learning.enabled","knowflow.product.private-quiz.enabled","knowflow.product.tutor.enabled","knowflow.product.tutor-coaching.enabled"},havingValue="true")
public class ErrorAnnotationController {
 private final ErrorAnnotationService annotations;private final com.knowflow.learning.feedback.InterviewRetestService retests;private long user(){return SecurityUtils.getCurrentUserId();}
 public record Practice(@NotNull @Positive Long microId){}
 @GetMapping public Result<?> list(@RequestParam String sourceType,@RequestParam long sourceId,@RequestParam(defaultValue="0")int questionIndex){return Result.success(annotations.list(user(),sourceType,sourceId,questionIndex));}
 @PostMapping public Result<?> create(@Valid @RequestBody ErrorAnnotationService.Create input){return Result.success(annotations.create(user(),input));}
 @GetMapping("/{id}") public Result<?> detail(@PathVariable long id){return Result.success(annotations.detail(id,user()));}
 @PostMapping("/{id}/corrections") public Result<?> correction(@PathVariable long id,@Valid @RequestBody ErrorAnnotationService.Correction input){return Result.success(annotations.correct(id,user(),input));}
 @GetMapping("/{id}/practice") public Result<?> practice(@PathVariable long id){return Result.success(annotations.practice(id,user()));}
 @PostMapping("/{id}/practice") public Result<?> link(@PathVariable long id,@Valid @RequestBody Practice input){return Result.success(annotations.linkPractice(id,user(),input.microId()));}
 public record RetestStart(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,80}") String requestId){}
 @GetMapping("/interview/{turn}/retests") public Result<?> retests(@PathVariable long turn){return Result.success(retests.list(user(),turn));}
 @PostMapping("/interview/{turn}/retests") public Result<?> plan(@PathVariable long turn){return Result.success(retests.create(user(),turn));}
 @GetMapping("/retests/{id}") public Result<?> planDetail(@PathVariable long id){return Result.success(retests.detail(user(),id));}
 @PostMapping("/retests/{id}/practice") public Result<?> planPractice(@PathVariable long id,@Valid @RequestBody Practice input){return Result.success(retests.linkPractice(user(),id,input.microId()));}
 @PostMapping("/retests/{id}/start") public Result<?> startRetest(@PathVariable long id,@Valid @RequestBody RetestStart input){return Result.success(retests.start(user(),id,input.requestId()));}
 @PostMapping("/retests/{id}/repeat") public Result<?> repeatRetest(@PathVariable long id,@Valid @RequestBody RetestStart input){return Result.success(retests.repeat(user(),id,input.requestId()));}
 @PostMapping("/retests/{id}/finish") public Result<?> finishRetest(@PathVariable long id){return Result.success(retests.finish(user(),id));}
 @ExceptionHandler(BusinessException.class) public ResponseEntity<Result<?>> business(BusinessException e){int code=e.getCode();if(code<400||code>599)code=500;return ResponseEntity.status(code).body(Result.error(code,e.getMessage()));}
}
