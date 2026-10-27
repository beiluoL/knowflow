package com.knowflow.controller;

import com.knowflow.common.PageResult;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.TechVocabDtos;
import com.knowflow.service.TechVocabExtractionService;
import com.knowflow.service.TechVocabReviewService;
import com.knowflow.service.TechVocabService;
import com.knowflow.vo.TechVocabVOs;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 技术英语 P0 公开接口。
 *
 * 权限：
 * - 词库检索 / 详情 / 发音 / 筛选匿名可读（只返回 PUBLISHED 词条）；
 * - 我的收藏与学习状态需要登录，用户 ID 一律取自认证上下文。
 */
@Tag(name = "技术英语")
@RestController
@RequestMapping("/api/tech-english")
@RequiredArgsConstructor
public class TechEnglishController {

    private final TechVocabService techVocabService;
    private final TechVocabReviewService reviewService;
    private final TechVocabExtractionService extractionService;

    @Operation(summary = "搜索/筛选词条（匿名可读）")
    @GetMapping("/terms")
    public Result<PageResult<TechVocabVOs.TermSummary>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String domain,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(techVocabService.search(q, type, domain, pageNum, pageSize));
    }

    @Operation(summary = "词条详情（匿名可读，登录时带出个人状态）")
    @GetMapping("/terms/{id}")
    public Result<TechVocabVOs.TermDetail> detail(@PathVariable Long id) {
        return Result.success(techVocabService.detail(id, SecurityUtils.getCurrentUserIdNullable()));
    }

    @Operation(summary = "词条发音变体（匿名可读）")
    @GetMapping("/terms/{id}/pronunciations")
    public Result<List<TechVocabVOs.PronunciationVO>> pronunciations(@PathVariable Long id) {
        return Result.success(techVocabService.pronunciations(id));
    }

    @Operation(summary = "词库筛选统计（匿名可读；登录时带出我的收藏/学习计数）")
    @GetMapping("/filters")
    public Result<TechVocabVOs.FiltersVO> filters() {
        return Result.success(techVocabService.filters(SecurityUtils.getCurrentUserIdNullable()));
    }

    /* ============================ V3 专题学习 ============================ */

    @Operation(summary = "技术方向专题概览（匿名可读；登录时带出已学/到期计数）")
    @GetMapping("/topics")
    public Result<List<TechVocabVOs.TopicVO>> topics() {
        return Result.success(reviewService.topics(SecurityUtils.getCurrentUserIdNullable()));
    }

    @Operation(summary = "专题内词条与个人学习状态（匿名可读，登录时带出个人状态）")
    @GetMapping("/topics/{domain}")
    public Result<TechVocabVOs.TopicDetailVO> topic(@PathVariable String domain) {
        return Result.success(reviewService.topic(SecurityUtils.getCurrentUserIdNullable(), domain));
    }

    @Operation(summary = "我的词条（收藏 / 学习状态，需登录）")
    @GetMapping("/me/terms")
    public Result<PageResult<TechVocabVOs.MyTermVO>> myTerms(
            @RequestParam(required = false) Boolean favorite,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(techVocabService.myTerms(userId, favorite, status, pageNum, pageSize));
    }

    @Operation(summary = "幂等更新收藏 / 学习状态（需登录）")
    @PutMapping("/me/terms/{id}")
    public Result<TechVocabVOs.UserStateVO> updateState(@PathVariable Long id,
                                                        @RequestBody TechVocabDtos.UserStateInput input) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(techVocabService.updateState(userId, id, input));
    }

    /* ============================ P1 词汇训练 ============================ */

    @Operation(summary = "加入复习（幂等，同一词条只创建一张复习卡）")
    @PostMapping("/me/terms/{id}/review-card")
    public Result<TechVocabVOs.AddReviewResultVO> addToReview(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(reviewService.addToReview(userId, id));
    }

    @Operation(summary = "今日词汇复习队列（到期优先，新词补充）")
    @GetMapping("/me/review-queue")
    public Result<TechVocabVOs.ReviewQueueVO> reviewQueue(
            @RequestParam(required = false) Integer limit) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(reviewService.queue(userId, limit));
    }

    @Operation(summary = "提交词汇复习评分（复用既有 FSRS 调度与幂等协议）")
    @PostMapping("/me/terms/{id}/review")
    public Result<TechVocabVOs.ReviewReceiptVO> review(@PathVariable Long id,
                                                       @RequestBody TechVocabDtos.ReviewInput input) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(reviewService.review(userId, id, input));
    }

    @Operation(summary = "个人技术英语学习看板")
    @GetMapping("/me/progress")
    public Result<TechVocabVOs.VocabProgressVO> progress() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(reviewService.progress(userId));
    }

    /* ============================ P2 划词与提词 ============================ */

    @Operation(summary = "划词查询（匿名可读，只返回已发布词条）")
    @GetMapping("/lookup")
    public Result<TechVocabVOs.LookupResultVO> lookup(
            @RequestParam String q,
            @RequestParam(required = false) String contextDomain) {
        return Result.success(techVocabService.lookup(q, contextDomain,
                SecurityUtils.getCurrentUserIdNullable()));
    }

    @Operation(summary = "提取专业英语候选词条（需登录；未配置模型时如实降级为规则提取）")
    @PostMapping("/me/candidates/extract")
    public Result<TechVocabVOs.ExtractionResultVO> extract(@RequestBody TechVocabDtos.ExtractInput input) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(extractionService.extract(userId, input));
    }

    @Operation(summary = "保存个人候选词条（幂等去重，需登录）")
    @PostMapping("/me/candidates")
    public Result<TechVocabVOs.CandidateSaveResultVO> saveCandidates(
            @RequestBody TechVocabDtos.CandidateSaveRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(extractionService.saveCandidates(userId, request));
    }

    @Operation(summary = "我的候选词条（需登录；默认全部状态）")
    @GetMapping("/me/candidates")
    public Result<List<TechVocabVOs.CandidateVO>> myCandidates(
            @RequestParam(required = false) String status) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(extractionService.myCandidates(userId, status));
    }
}
