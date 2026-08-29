package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.service.KnowledgeMasteryService;
import com.knowflow.service.ResourceKnowledgeBackfillService;
import com.knowflow.vo.KnowledgeMasteryDetailVO;
import com.knowflow.vo.KnowledgeMasteryVO;
import com.knowflow.vo.MasteryDiagnosticsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识点掌握度接口（Knowledge Mastery Engine，Phase 2-B）。
 * 仅当前登录用户可读写自己的掌握度；兼容既有 /stats/mastery、/category-mastery。
 */
@Tag(name = "知识点掌握度接口")
@RestController
@RequestMapping("/api/learning/mastery")
@RequiredArgsConstructor
public class KnowledgeMasteryController {

    private final KnowledgeMasteryService knowledgeMasteryService;
    private final ResourceKnowledgeBackfillService backfillService;

    @Operation(summary = "我的知识点掌握度总览（按分类可在前端分组）")
    @GetMapping
    public Result<List<KnowledgeMasteryVO>> list() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(knowledgeMasteryService.listAll(userId));
    }

    @Operation(summary = "薄弱知识点（status = WEAK）")
    @GetMapping("/weak")
    public Result<List<KnowledgeMasteryVO>> weak() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(knowledgeMasteryService.listWeak(userId));
    }

    @Operation(summary = "需复习知识点（status = REVIEW_REQUIRED）")
    @GetMapping("/review-required")
    public Result<List<KnowledgeMasteryVO>> reviewRequired() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(knowledgeMasteryService.listReviewRequired(userId));
    }

    @Operation(summary = "知识点掌握度详情 + 可解释说明")
    @GetMapping("/{knowledgeId}")
    public Result<KnowledgeMasteryDetailVO> detail(@PathVariable Long knowledgeId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(knowledgeMasteryService.getDetail(userId, knowledgeId));
    }

    @Operation(summary = "掌握度引擎可观察性诊断（无映射事件数等）")
    @GetMapping("/diagnostics")
    public Result<MasteryDiagnosticsVO> diagnostics() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(knowledgeMasteryService.diagnostics(userId));
    }

    @Operation(summary = "构建资源→知识点映射并重算我的掌握度（算法升级 / 新抽取文档后调用）")
    @PostMapping("/recalculate")
    public Result<Void> recalculate() {
        Long userId = SecurityUtils.getCurrentUserId();
        backfillService.backfillUser(userId);
        return Result.success();
    }
}
