package com.knowflow.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.common.PageResult;
import com.knowflow.common.Result;
import com.knowflow.entity.LearningChapter;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.entity.LearningPath;
import com.knowflow.mapper.LearningChapterMapper;
import com.knowflow.mapper.LearningFlashcardMapper;
import com.knowflow.mapper.LearningPathMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "管理员学习管理")
@RestController
@RequestMapping("/api/admin/learning")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminLearningController {

    private final LearningPathMapper pathMapper;
    private final LearningChapterMapper chapterMapper;
    private final LearningFlashcardMapper flashcardMapper;

    // ===== 学习路径 =====
    @Operation(summary = "学习路径列表")
    @GetMapping("/paths")
    public Result<List<LearningPath>> listPaths() {
        return Result.success(pathMapper.selectList(null));
    }

    @Operation(summary = "新增学习路径")
    @PostMapping("/paths")
    public Result<LearningPath> addPath(@RequestBody LearningPath path) {
        pathMapper.insert(path);
        return Result.success(path);
    }

    @Operation(summary = "更新学习路径")
    @PutMapping("/paths/{id}")
    public Result<Void> updatePath(@PathVariable Long id, @RequestBody LearningPath path) {
        path.setId(id);
        pathMapper.updateById(path);
        return Result.success();
    }

    @Operation(summary = "删除学习路径")
    @DeleteMapping("/paths/{id}")
    public Result<Void> deletePath(@PathVariable Long id) {
        pathMapper.deleteById(id);
        return Result.success();
    }

    // ===== 章节 =====
    @Operation(summary = "章节列表")
    @GetMapping("/chapters")
    public Result<List<LearningChapter>> listChapters(@RequestParam(required = false) Long pathId) {
        QueryWrapper<LearningChapter> wrapper = new QueryWrapper<>();
        if (pathId != null) wrapper.eq("path_id", pathId);
        wrapper.orderByAsc("sort_order");
        return Result.success(chapterMapper.selectList(wrapper));
    }

    @Operation(summary = "新增章节")
    @PostMapping("/chapters")
    public Result<LearningChapter> addChapter(@RequestBody LearningChapter chapter) {
        chapterMapper.insert(chapter);
        return Result.success(chapter);
    }

    @Operation(summary = "更新章节")
    @PutMapping("/chapters/{id}")
    public Result<Void> updateChapter(@PathVariable Long id, @RequestBody LearningChapter chapter) {
        chapter.setId(id);
        chapterMapper.updateById(chapter);
        return Result.success();
    }

    @Operation(summary = "删除章节")
    @DeleteMapping("/chapters/{id}")
    public Result<Void> deleteChapter(@PathVariable Long id) {
        chapterMapper.deleteById(id);
        return Result.success();
    }

    // ===== 闪卡 =====
    @Operation(summary = "闪卡列表")
    @GetMapping("/flashcards")
    public Result<PageResult<LearningFlashcard>> listFlashcards(
            @RequestParam(required = false) Long pathId,
            @RequestParam(required = false) Long chapterId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<LearningFlashcard> page = new Page<>(pageNum, Math.min(pageSize, 100));
        QueryWrapper<LearningFlashcard> wrapper = new QueryWrapper<>();
        if (pathId != null) wrapper.eq("path_id", pathId);
        if (chapterId != null) wrapper.eq("chapter_id", chapterId);
        wrapper.orderByDesc("create_time");
        Page<LearningFlashcard> result = flashcardMapper.selectPage(page, wrapper);
        return Result.success(PageResult.of(result));
    }

    @Operation(summary = "新增闪卡")
    @PostMapping("/flashcards")
    public Result<LearningFlashcard> addFlashcard(@RequestBody LearningFlashcard flashcard) {
        flashcardMapper.insert(flashcard);
        return Result.success(flashcard);
    }

    @Operation(summary = "更新闪卡")
    @PutMapping("/flashcards/{id}")
    public Result<Void> updateFlashcard(@PathVariable Long id, @RequestBody LearningFlashcard flashcard) {
        flashcard.setId(id);
        flashcardMapper.updateById(flashcard);
        return Result.success();
    }

    @Operation(summary = "删除闪卡")
    @DeleteMapping("/flashcards/{id}")
    public Result<Void> deleteFlashcard(@PathVariable Long id) {
        flashcardMapper.deleteById(id);
        return Result.success();
    }
}
