package com.knowflow.controller;

import com.knowflow.common.PageResult;
import com.knowflow.common.Result;
import com.knowflow.dto.DocQueryDTO;
import com.knowflow.dto.ReadProgressDTO;
import com.knowflow.entity.DocDocument;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.service.DocService;
import com.knowflow.vo.DocDetailVO;
import com.knowflow.vo.DocVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 文档管理 REST 接口，提供文档查询、收藏、阅读进度等能力（部分接口允许匿名访问）。 */
@Tag(name = "文档接口")
@RestController
@RequestMapping("/api/docs")
@RequiredArgsConstructor
public class DocController {

    private final DocService docService;

    @Operation(summary = "文档列表")
    @GetMapping
    public Result<PageResult<DocVO>> list(DocQueryDTO dto) {
        return Result.success(docService.getDocPage(dto));
    }

    @Operation(summary = "文档详情")
    @GetMapping("/{id}")
    public Result<DocDetailVO> detail(@PathVariable Long id, Authentication authentication) {
        Long userId = authentication != null ? (Long) authentication.getPrincipal() : null;
        return Result.success(docService.getDocDetail(id, userId));
    }

    @Operation(summary = "收藏/取消收藏")
    @PostMapping("/{id}/favorite")
    public Result<Void> toggleFavorite(@PathVariable Long id, Authentication authentication) {
        if (authentication == null) {
            return Result.error(401, "请先登录");
        }
        Long userId = (Long) authentication.getPrincipal();
        docService.toggleFavorite(id, userId);
        return Result.success();
    }

    @Operation(summary = "我的收藏列表")
    @GetMapping("/favorites")
    public Result<List<DocVO>> favorites(Authentication authentication) {
        Long userId = authentication != null ? (Long) authentication.getPrincipal() : null;
        return Result.success(docService.getFavoriteList(userId));
    }

    @Operation(summary = "最近阅读")
    @GetMapping("/recent")
    public Result<List<DocVO>> recent(Authentication authentication) {
        Long userId = authentication != null ? (Long) authentication.getPrincipal() : null;
        return Result.success(docService.getRecentReadList(userId));
    }

    @Operation(summary = "推荐文档")
    @GetMapping("/recommend")
    public Result<List<DocVO>> recommend(Authentication authentication) {
        Long userId = authentication != null ? (Long) authentication.getPrincipal() : null;
        return Result.success(docService.getRecommendList(userId));
    }

    @Operation(summary = "更新阅读进度")
    @PostMapping("/progress")
    public Result<Void> updateProgress(@RequestBody @Valid ReadProgressDTO dto, Authentication authentication) {
        if (authentication == null) {
            return Result.error(401, "请先登录");
        }
        Long userId = (Long) authentication.getPrincipal();
        docService.updateReadProgress(dto, userId);
        return Result.success();
    }

    @Operation(summary = "上传/创建文档")
    @PostMapping
    public Result<DocDetailVO> create(@RequestBody DocDocument doc, Authentication authentication) {
        if (authentication == null) {
            return Result.error(401, "请先登录");
        }
        docService.saveDoc(doc);
        return Result.success(docService.getDocDetail(doc.getId(), (Long) authentication.getPrincipal()));
    }

    @Operation(summary = "更新文档")
    @PutMapping("/{id}")
    public Result<DocDetailVO> update(@PathVariable Long id, @RequestBody DocDocument doc, Authentication authentication) {
        if (authentication == null) {
            return Result.error(401, "请先登录");
        }
        doc.setId(id);
        docService.updateDoc(doc);
        return Result.success(docService.getDocDetail(id, (Long) authentication.getPrincipal()));
    }

    @Operation(summary = "AI 生成文档摘要")
    @PostMapping("/{id}/ai-summary")
    public Result<String> generateSummary(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(docService.generateAISummary(id));
    }

    @Operation(summary = "AI 基于文档生成闪卡")
    @PostMapping("/{id}/ai-flashcards")
    public Result<List<LearningFlashcard>> generateFlashcards(@PathVariable Long id,
                                                              @RequestParam(required = false) Long pathId,
                                                              @RequestParam(required = false) Long chapterId,
                                                              Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(docService.generateFlashcards(id, pathId, chapterId));
    }
}
