package com.knowflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowflow.common.Result;
import com.knowflow.entity.LearningEvent;
import com.knowflow.service.LearningEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 学习行为事件接口（Learning Event System，Phase 1）。仅当前登录用户可读自己的事件。 */
@Tag(name = "学习事件接口")
@RestController
@RequestMapping("/api/learning/events")
@RequiredArgsConstructor
public class LearningEventController {

    private final LearningEventService learningEventService;

    @Operation(summary = "查询我的学习行为事件（掌握度引擎 / AI 教练 / 看板数据源）")
    @GetMapping
    public Result<IPage<LearningEvent>> list(
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(learningEventService.pageEvents(userId, eventType, current, size));
    }
}
