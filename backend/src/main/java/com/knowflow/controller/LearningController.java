package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.dto.FlashcardGenerateDTO;
import com.knowflow.dto.FlashcardSaveDTO;
import com.knowflow.entity.LearningTask;
import com.knowflow.service.LearningService;
import com.knowflow.vo.DailyActivityVO;
import com.knowflow.vo.FlashcardVO;
import com.knowflow.vo.LearningChapterVO;
import com.knowflow.vo.LearningPathVO;
import com.knowflow.vo.LearningTaskVO;
import com.knowflow.vo.MasteryDistributionVO;
import com.knowflow.vo.PersonalizedPathVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    @Operation(summary = "学习活跃度热力图（按日期聚合）")
    @GetMapping("/stats/daily-activity")
    public Result<List<DailyActivityVO>> dailyActivity(@RequestParam(defaultValue = "120") int days,
                                                      Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(learningService.getDailyActivity(userId, days));
    }

    @Operation(summary = "掌握分布看板")
    @GetMapping("/stats/mastery")
    public Result<MasteryDistributionVO> mastery(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(learningService.getMasteryDistribution(userId));
    }

    // ============================================================
    // 用户级「我的闪卡」
    // ============================================================

    @Operation(summary = "我的闪卡列表")
    @GetMapping("/my/flashcards")
    public Result<List<FlashcardVO>> myFlashcards(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String category,
                                                  @RequestParam(required = false) Integer difficulty,
                                                  @RequestParam(required = false) Long categoryId,
                                                  @RequestParam(required = false) String sourceType,
                                                  Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(learningService.listMyFlashcards(userId, keyword, category, difficulty, categoryId, sourceType));
    }

    @Operation(summary = "我的闪卡详情")
    @GetMapping("/my/flashcards/{id}")
    public Result<FlashcardVO> getMyFlashcard(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(learningService.getMyFlashcard(id, userId));
    }

    @Operation(summary = "新增闪卡")
    @PostMapping("/my/flashcards")
    public Result<FlashcardVO> createMyFlashcard(@RequestBody FlashcardSaveDTO dto, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(learningService.createMyFlashcard(userId, dto));
    }

    @Operation(summary = "修改闪卡")
    @PutMapping("/my/flashcards/{id}")
    public Result<Void> updateMyFlashcard(@PathVariable Long id, @RequestBody FlashcardSaveDTO dto,
                                          Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        learningService.updateMyFlashcard(id, userId, dto);
        return Result.success();
    }

    @Operation(summary = "删除单张闪卡")
    @DeleteMapping("/my/flashcards/{id}")
    public Result<Void> deleteMyFlashcard(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        learningService.deleteMyFlashcard(id, userId);
        return Result.success();
    }

    @Operation(summary = "批量删除闪卡")
    @DeleteMapping("/my/flashcards")
    public Result<Void> deleteMyFlashcards(@RequestBody Map<String, List<Long>> body, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        learningService.deleteMyFlashcards(body.get("ids"), userId);
        return Result.success();
    }

    @Operation(summary = "AI 生成闪卡（基于文档或知识库）")
    @PostMapping("/my/flashcards/generate")
    public Result<List<FlashcardVO>> generateMyFlashcards(@RequestBody FlashcardGenerateDTO dto,
                                                          Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(learningService.generateMyFlashcards(userId, dto));
    }

    @Operation(summary = "导入闪卡")
    @PostMapping("/my/flashcards/import")
    public Result<Map<String, Integer>> importMyFlashcards(@RequestBody List<FlashcardSaveDTO> cards,
                                                           Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        int inserted = learningService.importMyFlashcards(userId, cards);
        return Result.success(Map.of("inserted", inserted));
    }

    @Operation(summary = "导出全部闪卡")
    @GetMapping("/my/flashcards/export")
    public Result<List<FlashcardVO>> exportMyFlashcards(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(learningService.exportMyFlashcards(userId));
    }

    // ============================================================
    // 个性化学习路径
    // ============================================================

    @Operation(summary = "AI 生成个性化学习路径（优先读缓存，未命中则 AI 生成并持久化）")
    @PostMapping("/personalized-path")
    public Result<PersonalizedPathVO> personalizedPath(@RequestBody Map<String, Object> body,
                                                        Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        String goal = (String) body.getOrDefault("goal", "");
        String level = (String) body.getOrDefault("level", "入门");
        Integer dailyMinutes = body.get("dailyMinutes") != null
                ? ((Number) body.get("dailyMinutes")).intValue() : 30;
        return Result.success(learningService.generatePersonalizedPath(userId, goal, level, dailyMinutes));
    }

    @Operation(summary = "重新生成个性化学习路径（删除旧缓存，AI 重新生成并持久化）")
    @PostMapping("/personalized-path/regenerate")
    public Result<PersonalizedPathVO> regeneratePersonalizedPath(@RequestBody Map<String, Object> body,
                                                                  Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        String goal = (String) body.getOrDefault("goal", "");
        String level = (String) body.getOrDefault("level", "入门");
        Integer dailyMinutes = body.get("dailyMinutes") != null
                ? ((Number) body.get("dailyMinutes")).intValue() : 30;
        return Result.success(learningService.regeneratePersonalizedPath(userId, goal, level, dailyMinutes));
    }

    @Operation(summary = "我的个性化路径历史（按创建时间倒序）")
    @GetMapping("/personalized-paths")
    public Result<List<PersonalizedPathVO>> personalizedPaths(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(learningService.listPersonalizedPaths(userId));
    }

    @Operation(summary = "采用个性化路径（落地为真实学习路径并自动报名）")
    @PostMapping("/personalized-path/{id}/adopt")
    public Result<Map<String, Long>> adoptPersonalizedPath(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Long pathId = learningService.adoptPersonalizedPath(userId, id);
        return Result.success(Map.of("pathId", pathId));
    }

    @Operation(summary = "删除我的一条个性化路径推荐")
    @DeleteMapping("/personalized-path/{id}")
    public Result<Void> deletePersonalizedPath(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        learningService.deletePersonalizedPath(userId, id);
        return Result.success();
    }
}
