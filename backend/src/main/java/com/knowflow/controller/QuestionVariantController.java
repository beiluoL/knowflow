package com.knowflow.controller;
import com.knowflow.common.*;import com.knowflow.exception.BusinessException;import com.knowflow.learning.feedback.QuestionVariantService;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import lombok.RequiredArgsConstructor;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController @RequestMapping("/api/learning/question-variants") @RequiredArgsConstructor
@ConditionalOnProperty(name={"knowflow.product.interview-learning.enabled","knowflow.product.private-quiz.enabled","knowflow.product.tutor.enabled","knowflow.product.tutor-coaching.enabled"},havingValue="true")
public class QuestionVariantController {
 private final QuestionVariantService service;private long user(){return SecurityUtils.getCurrentUserId();}
 public record Begin(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,80}")String requestId){}
 public record Submit(@NotNull @Positive Long attemptId,@NotNull @Size(min=2,max=2)List<@NotNull @Size(max=10)String> answers){}
 @GetMapping public Result<?> list(@RequestParam String sourceType,@RequestParam long sourceId,@RequestParam(defaultValue="0")int questionIndex){return Result.success(service.list(user(),sourceType,sourceId,questionIndex));}
 @PostMapping public Result<?> generate(@Valid @RequestBody QuestionVariantService.Generate input){return Result.success(service.generate(user(),input));}
 @GetMapping("/{id}")public Result<?> detail(@PathVariable long id){return Result.success(service.detail(user(),id));}
 @PostMapping("/{id}/confirm")public Result<?> confirm(@PathVariable long id,@Valid @RequestBody QuestionVariantService.Confirm input){return Result.success(service.confirm(user(),id,input));}
 @PostMapping("/{id}/attempts")public Result<?> begin(@PathVariable long id,@Valid @RequestBody Begin input){return Result.success(service.begin(user(),id,input.requestId()));}
 @PostMapping("/{id}/submit")public Result<?> submit(@PathVariable long id,@Valid @RequestBody Submit input){return Result.success(service.submit(user(),id,input.attemptId(),input.answers()));}
 @ExceptionHandler(BusinessException.class)public ResponseEntity<Result<?>> business(BusinessException e){int code=e.getCode();if(code<400||code>599)code=500;return ResponseEntity.status(code).body(Result.error(code,e.getMessage()));}
}
