package com.knowflow.controller;
import com.knowflow.common.*;
import com.knowflow.learning.actions.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/chat/learning-actions") @RequiredArgsConstructor
public class ChatLearningActionController {
 private final ChatLearningActionService actions;
 @PostMapping public Result<PlanModels.ChatResponse> handle(@RequestBody PlanModels.ChatRequest request){return Result.success(actions.handle(SecurityUtils.getCurrentUserId(),request));}
}
