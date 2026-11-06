package com.knowflow.learning.controller.admin;

import com.knowflow.common.PageResult;
import com.knowflow.common.Result;
import com.knowflow.knowledge.entity.DocCategory;
import com.knowflow.knowledge.entity.DocDocument;
import com.knowflow.learning.entity.LearningChapter;
import com.knowflow.flashcards.entity.LearningFlashcard;
import com.knowflow.learning.entity.LearningPath;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import com.knowflow.learning.service.AdminLearningApplicationService;
/**
 * 提供管理端学习路径、章节及学习内容管理接口。
 */
@Slf4j
@Tag(name = "管理员学习管理")
@RestController
@RequestMapping("/api/admin/learning")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminLearningController {

    private final com.knowflow.learning.service.AdminLearningApplicationService applicationService;

    // ===== 学习路径 =====
    @Operation(summary = "学习路径列表")
    @GetMapping("/paths")
    public Result<List<LearningPath>> listPaths() {
        return applicationService.listPaths();
    }

    @Operation(summary = "新增学习路径")
    @PostMapping("/paths")
    public Result<LearningPath> addPath(@RequestBody LearningPath path) {
        return applicationService.addPath(path);
    }

    @Operation(summary = "更新学习路径")
    @PutMapping("/paths/{id}")
    public Result<Void> updatePath(@PathVariable Long id, @RequestBody LearningPath path) {
        return applicationService.updatePath(id, path);
    }

    @Operation(summary = "删除学习路径")
    @DeleteMapping("/paths/{id}")
    public Result<Void> deletePath(@PathVariable Long id) {
        return applicationService.deletePath(id);
    }

    // ===== 章节 =====
    @Operation(summary = "章节列表")
    @GetMapping("/chapters")
    public Result<List<LearningChapter>> listChapters(@RequestParam(required = false) Long pathId) {
        return applicationService.listChapters(pathId);
    }

    @Operation(summary = "新增章节")
    @PostMapping("/chapters")
    public Result<LearningChapter> addChapter(@RequestBody LearningChapter chapter) {
        return applicationService.addChapter(chapter);
    }

    @Operation(summary = "更新章节")
    @PutMapping("/chapters/{id}")
    public Result<Void> updateChapter(@PathVariable Long id, @RequestBody LearningChapter chapter) {
        return applicationService.updateChapter(id, chapter);
    }

    @Operation(summary = "删除章节")
    @DeleteMapping("/chapters/{id}")
    public Result<Void> deleteChapter(@PathVariable Long id) {
        return applicationService.deleteChapter(id);
    }

    // ===== 闪卡 =====
    @Operation(summary = "闪卡列表")
    @GetMapping("/flashcards")
    public Result<PageResult<LearningFlashcard>> listFlashcards(
            @RequestParam(required = false) Long pathId,
            @RequestParam(required = false) Long chapterId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return applicationService.listFlashcards(pathId, chapterId, pageNum, pageSize);
    }

    @Operation(summary = "新增闪卡")
    @PostMapping("/flashcards")
    public Result<LearningFlashcard> addFlashcard(@RequestBody LearningFlashcard flashcard) {
        return applicationService.addFlashcard(flashcard);
    }

    @Operation(summary = "更新闪卡")
    @PutMapping("/flashcards/{id}")
    public Result<Void> updateFlashcard(@PathVariable Long id, @RequestBody LearningFlashcard flashcard) {
        return applicationService.updateFlashcard(id, flashcard);
    }

    @Operation(summary = "删除闪卡")
    @DeleteMapping("/flashcards/{id}")
    public Result<Void> deleteFlashcard(@PathVariable Long id) {
        return applicationService.deleteFlashcard(id);
    }

    // ===== AI 自动生成 =====

    @Operation(summary = "获取知识库列表（用于AI生成时选择）")
    @GetMapping("/categories")
    public Result<List<DocCategory>> listCategories() {
        return applicationService.listCategories();
    }

    @Operation(summary = "获取指定知识库下的文档列表")
    @GetMapping("/docs")
    public Result<List<DocDocument>> listDocsByCategory(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "20") Integer limit) {
        return applicationService.listDocsByCategory(categoryId, limit);
    }

    @Operation(summary = "AI 自动生成学习路径（含章节）")
    @PostMapping("/ai/generate-path")
    public Result<LearningPath> aiGeneratePath(@RequestBody Map<String, Object> body) {
        return applicationService.aiGeneratePath(body);
    }

    @Operation(summary = "AI 为指定章节生成内容（支持指定文档ID）")
    @PostMapping("/chapters/{id}/ai-generate-content")
    public Result<LearningChapter> aiGenerateChapterContent(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        return applicationService.aiGenerateChapterContent(id, body);
    }

    @Operation(summary = "AI 为指定学习路径批量生成闪卡")
    @PostMapping("/paths/{id}/ai-generate-flashcards")
    public Result<List<LearningFlashcard>> aiGenerateFlashcards(@PathVariable Long id) {
        return applicationService.aiGenerateFlashcards(id);
    }

    @Operation(summary = "AI 根据知识库/文档生成知识卡片（不依赖学习路径）")
    @PostMapping("/flashcards/ai-generate")
    public Result<List<LearningFlashcard>> aiGenerateFlashcardsFromKb(@RequestBody Map<String, Object> body) {
        return applicationService.aiGenerateFlashcardsFromKb(body);
    }

    @Operation(summary = "发布学习路径")
    @PutMapping("/paths/{id}/publish")
    public Result<Void> publishPath(@PathVariable Long id) {
        return applicationService.publishPath(id);
    }

    @Operation(summary = "下架学习路径")
    @PutMapping("/paths/{id}/unpublish")
    public Result<Void> unpublishPath(@PathVariable Long id) {
        return applicationService.unpublishPath(id);
    }
}
