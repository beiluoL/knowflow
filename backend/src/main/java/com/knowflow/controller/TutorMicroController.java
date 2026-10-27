package com.knowflow.controller;
import com.knowflow.common.*;import com.knowflow.learning.tutor.TutorMicroService;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import lombok.RequiredArgsConstructor;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController @RequestMapping("/api/learning/micro-challenges") @RequiredArgsConstructor
@ConditionalOnProperty(name={"knowflow.product.tutor.enabled","knowflow.product.tutor-coaching.enabled","knowflow.product.private-quiz.enabled"},havingValue="true")
public class TutorMicroController {
 private final TutorMicroService micro;private long user(){return SecurityUtils.getCurrentUserId();}public record Answer(@NotNull @Size(max=10) String answer){}
 @GetMapping public Result<List<Map<String,Object>>> list(@RequestParam long noteId){return Result.success(micro.list(noteId,user()));}
 @PostMapping public Result<Map<String,Object>> start(@Valid @RequestBody TutorMicroService.Start input){return Result.success(micro.start(user(),input));}
 @GetMapping("/{id}") public Result<Map<String,Object>> detail(@PathVariable long id){return Result.success(micro.detail(id,user()));}
 @PostMapping("/{id}/begin") public Result<Map<String,Object>> begin(@PathVariable long id){return Result.success(micro.begin(id,user()));}
 @PutMapping("/{id}/draft") public Result<Map<String,Object>> draft(@PathVariable long id,@Valid @RequestBody Answer input){return Result.success(micro.draft(id,user(),input.answer()));}
 @PostMapping("/{id}/submit") public Result<Map<String,Object>> submit(@PathVariable long id,@Valid @RequestBody Answer input){return Result.success(micro.submit(id,user(),input.answer()));}
}
