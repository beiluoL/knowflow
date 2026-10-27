package com.knowflow.controller;

import com.knowflow.common.*;
import com.knowflow.learning.privatequiz.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.knowflow.learning.privatequiz.PrivateQuizRequests.*;

@RestController @RequestMapping("/api/learning/private-quizzes") @RequiredArgsConstructor
@ConditionalOnProperty(name="knowflow.product.private-quiz.enabled",havingValue="true")
public class PrivateQuizController {
 private final PrivateQuizService quizzes;
 private long uid(){return SecurityUtils.getCurrentUserId();}
 @GetMapping public Result<List<Map<String,Object>>> list(@RequestParam(required=false) Long noteId){return Result.success(quizzes.list(uid(),noteId));}
 @PostMapping("/preview") public Result<Map<String,Object>> preview(@Valid @RequestBody Preview input){return Result.success(quizzes.preview(uid(),input));}
 @GetMapping("/{id}") public Result<Map<String,Object>> detail(@PathVariable long id){return Result.success(quizzes.detail(uid(),id));}
 @PostMapping("/{id}/confirm") public Result<Map<String,Object>> confirm(@PathVariable long id,@Valid @RequestBody Confirm input){return Result.success(quizzes.confirm(uid(),id,input));}
 @PostMapping("/{id}/attempts") public Result<Map<String,Object>> begin(@PathVariable long id,@Valid @RequestBody Begin input){return Result.success(quizzes.begin(uid(),id,input));}
 @GetMapping("/{id}/history") public Result<List<Map<String,Object>>> history(@PathVariable long id){return Result.success(quizzes.history(uid(),id));}
 @GetMapping("/attempts/{id}") public Result<Map<String,Object>> attempt(@PathVariable long id){return Result.success(quizzes.getAttempt(uid(),id));}
 @PostMapping("/attempts/{id}/submit") public Result<Map<String,Object>> submit(@PathVariable long id,@Valid @RequestBody Submit input){return Result.success(quizzes.submit(uid(),id,input));}
}
