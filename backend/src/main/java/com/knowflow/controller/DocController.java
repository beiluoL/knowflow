package com.knowflow.controller;

import com.knowflow.common.PageResult;
import com.knowflow.common.Result;
import com.knowflow.dto.DocQueryDTO;
import com.knowflow.dto.ReadProgressDTO;
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
}
