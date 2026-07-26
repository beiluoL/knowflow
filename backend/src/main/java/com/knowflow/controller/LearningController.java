package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.entity.LearningTask;
import com.knowflow.service.LearningService;
import com.knowflow.vo.FlashcardVO;
import com.knowflow.vo.LearningChapterVO;
import com.knowflow.vo.LearningPathVO;
import com.knowflow.vo.LearningTaskVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 学习中心 REST 接口，提供学习路径、章节、闪卡与任务能力。 */
@Tag(name = "学习接口")
@RestController
@RequestMapping("/api/learning")
@RequiredArgsConstructor
public class LearningController {

    private final LearningService learningService;

    @Operation(summary = "学习路径列表")
    @GetMapping("/paths")
    public Result<List<LearningPathVO>> paths() {
        return Result.success(learningService.getPathList());
    }

    @Operation(summary = "学习路径详情")
    @GetMapping("/paths/{id}")
    public Result<LearningPathVO> pathDetail(@PathVariable Long id) {
        return Result.success(learningService.getPathDetail(id));
    }

    @Operation(summary = "报名学习路径")
    @PostMapping("/paths/{id}/enroll")
    public Result<Void> enrollPath(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        learningService.enrollPath(id, userId);
        return Result.success();
    }

    @Operation(summary = "章节列表")
    @GetMapping("/paths/{pathId}/chapters")
    public Result<List<LearningChapterVO>> chapters(@PathVariable Long pathId, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(learningService.getChapterList(pathId, userId));
    }

    @Operation(summary = "章节详情")
    @GetMapping("/chapters/{id}")
    public Result<LearningChapterVO> chapterDetail(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(learningService.getChapterDetail(id, userId));
    }

    @Operation(summary = "完成章节")
    @PostMapping("/chapters/{id}/complete")
    public Result<Void> completeChapter(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        learningService.completeChapter(id, userId);
        return Result.success();
    }

    @Operation(summary = "闪卡列表")
    @GetMapping("/flashcards")
    public Result<List<FlashcardVO>> flashcards(@RequestParam(required = false) Long pathId,
                                                 @RequestParam(required = false) Long chapterId) {
        return Result.success(learningService.getFlashcardList(pathId, chapterId));
    }

    @Operation(summary = "复习闪卡（SM-2 间隔重复）")
    @PostMapping("/flashcards/{id}/review")
    public Result<Void> reviewFlashcard(@PathVariable Long id, @RequestParam Integer quality, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        learningService.reviewFlashcard(id, userId, quality);
        return Result.success();
    }

    @Operation(summary = "学习任务列表")
    @GetMapping("/tasks")
    public Result<List<LearningTaskVO>> tasks(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(learningService.getTaskList(userId));
    }

    @Operation(summary = "创建学习任务")
    @PostMapping("/tasks")
    public Result<Void> createTask(@RequestBody LearningTask task, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        learningService.createTask(task, userId);
        return Result.success();
    }

    @Operation(summary = "更新任务状态")
    @PutMapping("/tasks/{id}/status")
    public Result<Void> updateTaskStatus(@PathVariable Long id, @RequestParam Integer status, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        learningService.updateTaskStatus(id, userId, status);
        return Result.success();
    }

    @Operation(summary = "删除学习任务")
    @DeleteMapping("/tasks/{id}")
    public Result<Void> deleteTask(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        learningService.deleteTask(id, userId);
        return Result.success();
    }
}
