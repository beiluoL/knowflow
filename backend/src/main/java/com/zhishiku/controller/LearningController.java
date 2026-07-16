package com.zhishiku.controller;

import com.zhishiku.common.Result;
import com.zhishiku.service.LearningService;
import com.zhishiku.vo.FlashcardVO;
import com.zhishiku.vo.LearningChapterVO;
import com.zhishiku.vo.LearningPathVO;
import com.zhishiku.vo.LearningTaskVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @Operation(summary = "学习任务列表")
    @GetMapping("/tasks")
    public Result<List<LearningTaskVO>> tasks(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(learningService.getTaskList(userId));
    }
}
