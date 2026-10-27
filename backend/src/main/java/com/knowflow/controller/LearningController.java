package com.knowflow.controller;

import com.knowflow.common.PageResult;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.exception.BusinessException;
import com.knowflow.dto.FlashcardGenerateDTO;
import com.knowflow.dto.FlashcardSaveDTO;
import com.knowflow.entity.LearningTask;
import com.knowflow.service.LearningService;
import com.knowflow.vo.CategoryMasteryVO;
import com.knowflow.vo.ChapterDagVO;
import com.knowflow.vo.ChapterKnowledgeVO;
import com.knowflow.vo.DailyActivityVO;
import com.knowflow.vo.FlashcardVO;
import com.knowflow.vo.LearningCertificateVO;
import com.knowflow.vo.LearningChapterVO;
import com.knowflow.vo.LearningAnalyticsVO;
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
    private final com.knowflow.learning.actions.LearningTaskProjection taskProjection;
    private final com.knowflow.service.TaskService realTasks;
    private final com.knowflow.service.LearningAnalyticsService learningAnalyticsService;

    /**
     * P1-A：当前登录用户的统一解析入口，取代各方法内联的 principal 强转
     * （{@code authentication.getPrincipal()} 强转成 {@code Long}）。
     *
     * <p>内联强转对已登录请求成立；但匿名 principal 是字符串 {@code "anonymousUser"}
     * （配置未 {@code anonymous().disable()}），强转会抛 {@code ClassCastException}，
     * 被 {@code GlobalExceptionHandler} 兜底处理成 <b>HTTP 500「系统内部错误」</b>。
     * 本方法改为显式区分「未登录」：复用既有 {@link SecurityUtils#getCurrentUserIdNullable()}
     * 做防御，未登录时抛 {@code BusinessException(401, ...)} —— 其 4xx 业务码会被
     * {@code GlobalExceptionHandler} 映射为对应 HTTP 状态码，语义与
     * {@code JwtAuthenticationEntryPoint} 的 401 契约一致。</p>
     *
     * <p>实现模式与 P1-A 第一批 {@code ChatController} 完全一致，不新建鉴权机制。</p>
     */
    private static Long currentUserId() {
        Long userId = SecurityUtils.getCurrentUserIdNullable();
        if (userId == null) {
            throw new BusinessException(401, "未登录或登录已失效");
        }
        return userId;
    }

    @Operation(summary = "学习路径列表")
    @GetMapping("/paths")
    public Result<List<LearningPathVO>> paths() {
        // 列表可匿名访问；已登录则带出报名状态与学习进度
        return Result.success(learningService.getPathList(SecurityUtils.getCurrentUserIdNullable()));
    }

    @Operation(summary = "学习路径详情")
    @GetMapping("/paths/{id}")
    public Result<LearningPathVO> pathDetail(@PathVariable Long id) {
        // 特殊语义（逐个判定，勿套用其他端点的 401）：本端点与 /paths 同属 SecurityConfig 的 permitAll 组，
        // 匿名可读；getPathDetail 已显式支持 userId == null（仅 enrolled 恒为 false）。
        // 故此处取可空 userId，修复原内联强转导致匿名访问 ClassCastException → 500 的问题。
        return Result.success(learningService.getPathDetail(id, SecurityUtils.getCurrentUserIdNullable()));
    }

    @Operation(summary = "报名学习路径")
    @PostMapping("/paths/{id}/enroll")
    public Result<Void> enrollPath(@PathVariable Long id) {
        learningService.enrollPath(id, currentUserId());
        return Result.success();
    }

    @Operation(summary = "章节列表")
    @GetMapping("/paths/{pathId}/chapters")
    public Result<List<LearningChapterVO>> chapters(@PathVariable Long pathId) {
        return Result.success(learningService.getChapterList(pathId, currentUserId()));
    }

    @Operation(summary = "章节依赖关系图(DAG)")
    @GetMapping("/paths/{pathId}/dag")
    public Result<ChapterDagVO> chapterDag(@PathVariable Long pathId) {
        return Result.success(learningService.getChapterDag(pathId, currentUserId()));
    }

    @Operation(summary = "章节详情")
    @GetMapping("/chapters/{id}")
    public Result<LearningChapterVO> chapterDetail(@PathVariable Long id) {
        return Result.success(learningService.getChapterDetail(id, currentUserId()));
    }

    /**
     * Architecture-4.2：解析章节对应的真实知识点（Knowledge Context 唯一服务端来源，§3-§6）。
     *
     * <p>推导链：{@code chapter.docIds → resource_knowledge_mapping(DOC, ACCEPTED) → kg_entity}。
     * 现有端点均无法回答「这一章对应哪些知识点」（/learning/mastery 无章节维度、
     * /knowledge/entity-graph 是 AI 抽取图谱口径且需按 docId 逐次调用），故新增本只读端点。</p>
     */
    @Operation(summary = "章节关联的真实知识点（docIds → 资源知识点映射 → kg_entity）")
    @GetMapping("/chapters/{id}/knowledge")
    public Result<ChapterKnowledgeVO> chapterKnowledge(@PathVariable Long id, Authentication authentication) {
        // 知识点映射为全局内容数据，但接口处于 /api/learning/** 之下需登录（SecurityConfig JWT 校验）；
        // authentication 仅用于强制鉴权，不做用户级过滤（避免为每个用户重复同一份映射结果）。
        if (authentication == null) {
            throw new BusinessException(401, "未登录");
        }
        return Result.success(learningService.getChapterKnowledge(id));
    }

    public record ReadingPositionRequest(String anchor, Long expectedRevision, String expectedContentHash, String presentationHash) {}

    @Operation(summary = "保存本人的章节阅读位置，不改变完成或掌握状态")
    @PostMapping("/chapters/{id}/reading-position")
    public Result<LearningChapterVO> saveReadingPosition(@PathVariable Long id, @RequestBody ReadingPositionRequest input) {
        return Result.success(learningService.saveReadingPosition(id, currentUserId(), input.anchor(), input.expectedRevision(), input.expectedContentHash(), input.presentationHash()));
    }

    @Operation(summary = "完成章节")
    @PostMapping("/chapters/{id}/complete")
    public Result<Void> completeChapter(@PathVariable Long id) {
        learningService.completeChapter(id, currentUserId());
        return Result.success();
    }

    @Operation(summary = "上报章节视频观看进度（L-FORM-01），返回当前累计进度")
    @PostMapping("/chapters/{id}/video-progress")
    public Result<java.math.BigDecimal> updateVideoProgress(@PathVariable Long id,
                                                            @RequestParam java.math.BigDecimal progress) {
        return Result.success(learningService.updateVideoProgress(id, currentUserId(), progress));
    }

    /**
     * 闪卡列表（**用户私有**）。
     *
     * <p>P0-1：该端点在 {@code SecurityConfig} 中的 {@code permitAll} 已移除，
     * 且 Service 查询**无条件**按当前登录用户过滤 —— 匿名访问返回 401，
     * 登录用户只能看到自己的闪卡（{@code pathId}/{@code chapterId} 仅在本人卡片范围内过滤）。</p>
     *
     * <p>用户 ID 只来自安全上下文（{@link #currentUserId()}），**不接受**前端传入，
     * 避免调用方用参数决定返回谁的数据。</p>
     */
    @Operation(summary = "闪卡列表（当前用户私有）")
    @GetMapping("/flashcards")
    public Result<List<FlashcardVO>> flashcards(@RequestParam(required = false) Long pathId,
                                                 @RequestParam(required = false) Long chapterId) {
        return Result.success(learningService.getFlashcardList(currentUserId(), pathId, chapterId));
    }

    @Operation(summary = "当前用户待复习闪卡（新卡 + 已到期，最多 100 张）")
    @GetMapping("/flashcards/due")
    public Result<List<FlashcardVO>> dueFlashcards() {
        return Result.success(learningService.listDueFlashcards(currentUserId()));
    }

    @Operation(summary = "复习闪卡（SM-2 间隔重复）")
    @PostMapping("/flashcards/{id}/review")
    public Result<Void> reviewFlashcard(@PathVariable Long id,
                                        @RequestParam Integer quality,
                                        @RequestParam(required = false) Long knowledgeId) {
        learningService.reviewFlashcard(id, currentUserId(), quality, knowledgeId);
        return Result.success();
    }

    @Operation(summary = "学习任务列表")
    @GetMapping("/tasks")
    public Result<List<LearningTaskVO>> tasks(@RequestParam(defaultValue="Asia/Shanghai") String zoneId) {
        Long user=currentUserId();
        return Result.success(taskProjection.append(learningService.getTaskList(user),user,zoneId));
    }

    @Operation(summary = "创建学习任务")
    @PostMapping("/tasks")
    public Result<Void> createTask(@RequestBody LearningTask task) {
        // 归属一律取当前登录用户：Service 内部同样强制 setUserId，请求体里的 userId 不生效。
        learningService.createTask(task, currentUserId());
        return Result.success();
    }

    @Operation(summary = "更新任务状态")
    @PutMapping("/tasks/{id}/status")
    public Result<Void> updateTaskStatus(@PathVariable Long id, @RequestParam Integer status, @RequestParam(defaultValue="LEARNING") String source) {
        if ("TASK".equals(source)) realTasks.setStatus(currentUserId(),id,status);
        else if ("LEARNING".equals(source)) learningService.updateTaskStatus(id, currentUserId(), status);
        else throw new BusinessException(400,"无效任务来源");
        return Result.success();
    }

    @Operation(summary = "删除学习任务")
    @DeleteMapping("/tasks/{id}")
    public Result<Void> deleteTask(@PathVariable Long id,@RequestParam(defaultValue="LEARNING") String source) {
        if ("TASK".equals(source)) realTasks.deleteTask(currentUserId(),id);
        else if ("LEARNING".equals(source)) learningService.deleteTask(id, currentUserId());
        else throw new BusinessException(400,"无效任务来源");
        return Result.success();
    }

    @Operation(summary = "学习活跃度热力图（按日期聚合）")
    @GetMapping("/stats/daily-activity")
    public Result<List<DailyActivityVO>> dailyActivity(@RequestParam(defaultValue = "120") int days) {
        return Result.success(learningService.getDailyActivity(currentUserId(), days));
    }

    @Operation(summary = "掌握分布看板")
    @GetMapping("/stats/mastery")
    public Result<MasteryDistributionVO> mastery() {
        return Result.success(learningService.getMasteryDistribution(currentUserId()));
    }

    // ============================================================
    // 用户级「我的闪卡」
    // ============================================================

    @Operation(summary = "我的闪卡列表")
    @GetMapping("/my/flashcards")
    public Result<PageResult<FlashcardVO>> myFlashcards(@RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String category,
                                                        @RequestParam(required = false) Integer difficulty,
                                                        @RequestParam(required = false) Long categoryId,
                                                        @RequestParam(required = false) String sourceType,
                                                        @RequestParam(defaultValue = "1") Integer pageNum,
                                                        @RequestParam(defaultValue = "12") Integer pageSize) {
        return Result.success(learningService.pageMyFlashcards(
                currentUserId(), keyword, category, difficulty, categoryId, sourceType, pageNum, pageSize));
    }

    @Operation(summary = "我的闪卡详情")
    @GetMapping("/my/flashcards/{id}")
    public Result<FlashcardVO> getMyFlashcard(@PathVariable Long id) {
        return Result.success(learningService.getMyFlashcard(id, currentUserId()));
    }

    @Operation(summary = "新增闪卡")
    @PostMapping("/my/flashcards")
    public Result<FlashcardVO> createMyFlashcard(@RequestBody FlashcardSaveDTO dto) {
        return Result.success(learningService.createMyFlashcard(currentUserId(), dto));
    }

    @Operation(summary = "修改闪卡")
    @PutMapping("/my/flashcards/{id}")
    public Result<Void> updateMyFlashcard(@PathVariable Long id, @RequestBody FlashcardSaveDTO dto) {
        learningService.updateMyFlashcard(id, currentUserId(), dto);
        return Result.success();
    }

    @Operation(summary = "删除单张闪卡")
    @DeleteMapping("/my/flashcards/{id}")
    public Result<Void> deleteMyFlashcard(@PathVariable Long id) {
        learningService.deleteMyFlashcard(id, currentUserId());
        return Result.success();
    }

    @Operation(summary = "批量删除闪卡")
    @DeleteMapping("/my/flashcards")
    public Result<Void> deleteMyFlashcards(@RequestBody Map<String, List<Long>> body) {
        learningService.deleteMyFlashcards(body.get("ids"), currentUserId());
        return Result.success();
    }

    @Operation(summary = "AI 生成闪卡（基于文档或知识库）")
    @PostMapping("/my/flashcards/generate")
    public Result<List<FlashcardVO>> generateMyFlashcards(@RequestBody FlashcardGenerateDTO dto) {
        return Result.success(learningService.generateMyFlashcards(currentUserId(), dto));
    }

    @Operation(summary = "导入闪卡")
    @PostMapping("/my/flashcards/import")
    public Result<Map<String, Integer>> importMyFlashcards(@RequestBody List<FlashcardSaveDTO> cards) {
        int inserted = learningService.importMyFlashcards(currentUserId(), cards);
        return Result.success(Map.of("inserted", inserted));
    }

    @Operation(summary = "导出全部闪卡")
    @GetMapping("/my/flashcards/export")
    public Result<List<FlashcardVO>> exportMyFlashcards() {
        return Result.success(learningService.exportMyFlashcards(currentUserId()));
    }

    // ============================================================
    // 个性化学习路径
    // ============================================================

    @Operation(summary = "AI 生成个性化学习路径（优先读缓存，未命中则 AI 生成并持久化）")
    @PostMapping("/personalized-path")
    public Result<PersonalizedPathVO> personalizedPath(@RequestBody Map<String, Object> body) {
        String goal = (String) body.getOrDefault("goal", "");
        String level = (String) body.getOrDefault("level", "入门");
        Integer dailyMinutes = body.get("dailyMinutes") != null
                ? ((Number) body.get("dailyMinutes")).intValue() : 30;
        return Result.success(learningService.generatePersonalizedPath(currentUserId(), goal, level, dailyMinutes));
    }

    @Operation(summary = "重新生成个性化学习路径（删除旧缓存，AI 重新生成并持久化）")
    @PostMapping("/personalized-path/regenerate")
    public Result<PersonalizedPathVO> regeneratePersonalizedPath(@RequestBody Map<String, Object> body) {
        String goal = (String) body.getOrDefault("goal", "");
        String level = (String) body.getOrDefault("level", "入门");
        Integer dailyMinutes = body.get("dailyMinutes") != null
                ? ((Number) body.get("dailyMinutes")).intValue() : 30;
        return Result.success(learningService.regeneratePersonalizedPath(currentUserId(), goal, level, dailyMinutes));
    }

    @Operation(summary = "我的个性化路径历史（按创建时间倒序）")
    @GetMapping("/personalized-paths")
    public Result<List<PersonalizedPathVO>> personalizedPaths() {
        return Result.success(learningService.listPersonalizedPaths(currentUserId()));
    }

    @Operation(summary = "采用个性化路径（落地为真实学习路径并自动报名）")
    @PostMapping("/personalized-path/{id}/adopt")
    public Result<Map<String, Long>> adoptPersonalizedPath(@PathVariable Long id) {
        Long pathId = learningService.adoptPersonalizedPath(currentUserId(), id);
        return Result.success(Map.of("pathId", pathId));
    }

    @Operation(summary = "删除我的一条个性化路径推荐")
    @DeleteMapping("/personalized-path/{id}")
    public Result<Void> deletePersonalizedPath(@PathVariable Long id) {
        learningService.deletePersonalizedPath(currentUserId(), id);
        return Result.success();
    }

    @Operation(summary = "分类维度掌握度画像（含薄弱项标记）")
    @GetMapping("/category-mastery")
    public Result<List<CategoryMasteryVO>> categoryMastery() {
        return Result.success(learningService.getCategoryMastery(currentUserId()));
    }

    // ============================================================
    // 数字证书（G-CERT-01：路径完成后自动颁发）
    // ============================================================

    @Operation(summary = "我的数字证书列表")
    @GetMapping("/certificates")
    public Result<List<LearningCertificateVO>> certificates() {
        return Result.success(learningService.listMyCertificates(currentUserId()));
    }

    @Operation(summary = "数字证书详情")
    @GetMapping("/certificates/{id}")
    public Result<LearningCertificateVO> certificateDetail(@PathVariable Long id) {
        return Result.success(learningService.getCertificateDetail(id, currentUserId()));
    }

    @Operation(summary = "验证数字证书（按验证码核验真伪，可匿名）")
    @GetMapping("/certificates/verify")
    public Result<LearningCertificateVO> verifyCertificate(@RequestParam String certNo) {
        return Result.success(learningService.verifyCertificate(certNo));
    }

    @Operation(summary = "Phase 3H 统一学习分析：练习正确率/题型知识点分布/代码判题分布/错题统计（确定性口径，无 AI 依赖）")
    @GetMapping("/analytics/overview")
    public Result<LearningAnalyticsVO> analyticsOverview(
            @RequestParam(required = false, defaultValue = "30") Integer windowDays) {
        return Result.success(learningAnalyticsService.overview(currentUserId(), windowDays));
    }
}
