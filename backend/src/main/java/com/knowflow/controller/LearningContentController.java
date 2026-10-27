package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.LearningContentAcceptRequest;
import com.knowflow.dto.LearningContentGenerateRequest;
import com.knowflow.service.LearningContentService;
import com.knowflow.vo.LearningContentAcceptResultVO;
import com.knowflow.vo.LearningContentGenerationResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Architecture-4.4：AI 学习内容生成（Learning Content Generator）REST 接口。
 *
 * <p>最小 API（仅 2 个新增端点）：Knowledge → Draft 生成、审核后 Accept 落库。
 * <ul>
 *   <li>{@code POST /api/knowledge/content/generate}：AI 生成三类 Draft（零写库，默认 DRAFT）；</li>
 *   <li>{@code POST /api/knowledge/content/accept}：确定性校验 + 去重后写入既有
 *       learning_flashcard / learning_scenario（幂等，零学习事件、零掌握度改动）。</li>
 * </ul>
 * 费曼 Draft 无持久实体，由前端预填跳转现有费曼页表达（FEYNMAN_EXPRESSED 事件），不经过 accept。
 * 均为 POST、需登录（落在既有 {@code .anyRequest().authenticated()}，无额外放行）；AI Key 仅按当前
 * userId 在服务端路由，任何 Key 不下发前端。
 */
@Tag(name = "AI 学习内容生成（Architecture-4.4）")
@RestController
@RequestMapping("/api/knowledge/content")
@RequiredArgsConstructor
public class LearningContentController {

    private final LearningContentService learningContentService;

    @Operation(summary = "生成学习内容 Draft：Knowledge → 闪卡/费曼/场景（零写库）")
    @PostMapping("/generate")
    public Result<LearningContentGenerationResultVO> generate(
            @RequestBody LearningContentGenerateRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(learningContentService.generate(request, userId));
    }

    @Operation(summary = "接受审核后的内容：确定性去重后写入既有学习实体（幂等，零事件）")
    @PostMapping("/accept")
    public Result<LearningContentAcceptResultVO> accept(
            @RequestBody LearningContentAcceptRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(learningContentService.accept(request, userId));
    }
}
