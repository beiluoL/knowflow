package com.knowflow.learning.controller;
import com.knowflow.common.*;
import com.knowflow.learning.actions.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.knowflow.learning.actions.PlanModels;
import com.knowflow.learning.service.actions.ChatLearningActionService;
/**
 * 将聊天学习意图交给现有学习动作服务处理。
 */
@RestController @RequestMapping("/api/chat/learning-actions") @RequiredArgsConstructor
public class ChatLearningActionController {
 private final ChatLearningActionService actions;
 @PostMapping public Result<PlanModels.ChatResponse> handle(@RequestBody PlanModels.ChatRequest request){return Result.success(actions.handle(SecurityUtils.getCurrentUserId(),request));}
}
