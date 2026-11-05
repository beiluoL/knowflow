package com.knowflow.learning.controller;



import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.learning.service.ChapterFavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 章节收藏接口。
 *
 * <p>收藏/取消收藏为 toggle 语义，重复请求按顺序切换状态，不是幂等新增。
 * 业务与数据访问全部下沉到 {@link ChapterFavoriteService}；本类只负责
 * 接收请求、解析当前用户、返回统一响应。</p>
 */
@Tag(name = "章节收藏接口")
@RestController
@RequestMapping("/api/learning/chapters")
@RequiredArgsConstructor
public class ChapterFavoriteController {

    private final ChapterFavoriteService chapterFavoriteService;

    /**
     * 收藏 / 取消收藏章节（toggle）。
     *
     * @param id 章节 ID
     * @return {@code favorited} 表示操作后的收藏状态
     */
    @Operation(summary = "收藏 / 取消收藏章节")
    @PostMapping("/{id}/favorite")
    public Result<Map<String, Object>> toggleFavorite(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        boolean favorited = chapterFavoriteService.toggle(userId, id);
        return Result.success(Map.of("favorited", favorited));
    }

    /**
     * 查询当前用户是否已收藏某章节。
     *
     * @param id 章节 ID
     * @return {@code favorited} 当前收藏状态
     */
    @Operation(summary = "查询是否已收藏")
    @GetMapping("/{id}/favorite")
    public Result<Map<String, Object>> isFavorited(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        boolean favorited = chapterFavoriteService.isFavorited(userId, id);
        return Result.success(Map.of("favorited", favorited));
    }

    /**
     * 查询当前用户收藏的所有章节 ID 列表（按收藏时间倒序）。
     *
     * @return 章节 ID 列表
     */
    @Operation(summary = "查询收藏的章节ID列表")
    @GetMapping("/favorites")
    public Result<List<Long>> favoriteList() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(chapterFavoriteService.listFavoriteChapterIds(userId));
    }
}
