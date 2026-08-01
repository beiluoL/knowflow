package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.entity.ChapterFavorite;
import com.knowflow.mapper.ChapterFavoriteMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 章节收藏接口。
 * 收藏/取消收藏为 toggle 语义，同一章节重复收藏幂等。
 */
@Tag(name = "章节收藏接口")
@RestController
@RequestMapping("/api/learning/chapters")
@RequiredArgsConstructor
public class ChapterFavoriteController {

    private final ChapterFavoriteMapper favoriteMapper;

    /** 收藏 / 取消收藏（toggle） */
    @Operation(summary = "收藏 / 取消收藏章节")
    @PostMapping("/{id}/favorite")
    public Result<Map<String, Object>> toggleFavorite(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        LambdaQueryWrapper<ChapterFavorite> wrapper = new LambdaQueryWrapper<ChapterFavorite>()
                .eq(ChapterFavorite::getUserId, userId)
                .eq(ChapterFavorite::getChapterId, id);
        ChapterFavorite existing = favoriteMapper.selectOne(wrapper);
        boolean favorited;
        if (existing != null) {
            favoriteMapper.deleteById(existing.getId());
            favorited = false;
        } else {
            ChapterFavorite fav = new ChapterFavorite();
            fav.setUserId(userId);
            fav.setChapterId(id);
            favoriteMapper.insert(fav);
            favorited = true;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("favorited", favorited);
        return Result.success(result);
    }

    /** 查询当前用户是否已收藏某章节 */
    @Operation(summary = "查询是否已收藏")
    @GetMapping("/{id}/favorite")
    public Result<Map<String, Object>> isFavorited(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Long count = favoriteMapper.selectCount(new LambdaQueryWrapper<ChapterFavorite>()
                .eq(ChapterFavorite::getUserId, userId)
                .eq(ChapterFavorite::getChapterId, id));
        Map<String, Object> result = new HashMap<>();
        result.put("favorited", count != null && count > 0);
        return Result.success(result);
    }

    /** 查询当前用户收藏的所有章节ID列表 */
    @Operation(summary = "查询收藏的章节ID列表")
    @GetMapping("/favorites")
    public Result<List<Long>> favoriteList(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<ChapterFavorite> list = favoriteMapper.selectList(new LambdaQueryWrapper<ChapterFavorite>()
                .eq(ChapterFavorite::getUserId, userId)
                .orderByDesc(ChapterFavorite::getCreateTime));
        List<Long> chapterIds = list.stream().map(ChapterFavorite::getChapterId).collect(Collectors.toList());
        return Result.success(chapterIds);
    }
}
