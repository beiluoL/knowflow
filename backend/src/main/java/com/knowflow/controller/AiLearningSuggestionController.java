package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.service.AiLearningSuggestionService;
import com.knowflow.vo.AiLearningSuggestionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** AI学习建议HTTP入口；身份来自认证上下文，业务由Service处理。 */
@Tag(name = "AI 智能学习建议接口")
@RestController
@RequestMapping("/api/learning/suggestions")
@RequiredArgsConstructor
public class AiLearningSuggestionController {
    private final AiLearningSuggestionService suggestionService;

    /** 获取当前用户的周期建议，沿用缓存与降级合同。 */
    @Operation(summary = "获取学习建议（带缓存）")
    @GetMapping
    public Result<AiLearningSuggestionVO> get(@RequestParam(required = false, defaultValue = "week") String period,
                                              Authentication authentication) {
        return Result.success(suggestionService.get(SecurityUtils.getCurrentUserId(), period));
    }

    /** 生成后原子替换当前用户的周期缓存。 */
    @Operation(summary = "重新生成学习建议")
    @PostMapping("/regenerate")
    public Result<AiLearningSuggestionVO> regenerate(@RequestParam(required = false, defaultValue = "week") String period,
                                                     Authentication authentication) {
        return Result.success(suggestionService.regenerate(SecurityUtils.getCurrentUserId(), period));
    }
}
