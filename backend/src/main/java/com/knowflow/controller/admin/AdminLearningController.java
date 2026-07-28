package com.knowflow.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.PageResult;
import com.knowflow.common.Result;
import com.knowflow.entity.DocCategory;
import com.knowflow.entity.DocDocument;
import com.knowflow.entity.LearningChapter;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.entity.LearningPath;
import com.knowflow.mapper.DocCategoryMapper;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.mapper.LearningChapterMapper;
import com.knowflow.mapper.LearningFlashcardMapper;
import com.knowflow.mapper.LearningPathMapper;
import com.knowflow.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "管理员学习管理")
@RestController
@RequestMapping("/api/admin/learning")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminLearningController {

    private final LearningPathMapper pathMapper;
    private final LearningChapterMapper chapterMapper;
    private final LearningFlashcardMapper flashcardMapper;
    private final DocDocumentMapper docMapper;
    private final DocCategoryMapper categoryMapper;
    private final AiService aiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 从 SecurityContext 获取当前用户 ID，未登录时返回 null。 */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long) {
            return (Long) authentication.getPrincipal();
        }
        return null;
    }

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
        Page<LearningFlashcard> page = new Page<>(com.knowflow.common.PageQuery.normalizePageNum(pageNum), com.knowflow.common.PageQuery.normalizePageSize(pageSize));
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

    // ===== AI 自动生成 =====

    @Operation(summary = "获取知识库列表（用于AI生成时选择）")
    @GetMapping("/categories")
    public Result<List<DocCategory>> listCategories() {
        QueryWrapper<DocCategory> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1).orderByAsc("sort_order");
        return Result.success(categoryMapper.selectList(wrapper));
    }

    @Operation(summary = "获取指定知识库下的文档列表")
    @GetMapping("/docs")
    public Result<List<DocDocument>> listDocsByCategory(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "20") Integer limit) {
        QueryWrapper<DocDocument> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1);
        if (categoryId != null) {
            wrapper.eq("category_id", categoryId);
        }
        wrapper.orderByDesc("view_count").last("LIMIT " + Math.min(limit, 100));
        // 只返回必要字段，减少传输
        List<DocDocument> docs = docMapper.selectList(wrapper);
        for (DocDocument doc : docs) {
            doc.setContent(null); // 不返回完整内容
        }
        return Result.success(docs);
    }

    @Operation(summary = "AI 自动生成学习路径（含章节）")
    @PostMapping("/ai/generate-path")
    public Result<LearningPath> aiGeneratePath(@RequestBody Map<String, Object> body) {
        String topic = (String) body.getOrDefault("topic", "");
        String description = (String) body.getOrDefault("description", "");
        String level = (String) body.getOrDefault("level", "入门");
        Integer chapterCount = body.get("chapterCount") != null ? ((Number) body.get("chapterCount")).intValue() : 5;
        Long categoryId = body.get("categoryId") != null ? ((Number) body.get("categoryId")).longValue() : null;

        if (topic == null || topic.trim().isEmpty()) {
            return Result.error("请填写学习主题");
        }

        // 检索知识库文档（可选按分类筛选）
        QueryWrapper<DocDocument> docWrapper = new QueryWrapper<>();
        docWrapper.eq("status", 1);
        if (categoryId != null) {
            docWrapper.eq("category_id", categoryId);
        }
        docWrapper.orderByDesc("view_count").last("LIMIT 15");
        List<DocDocument> allDocs = docMapper.selectList(docWrapper);

        // 构建知识库摘要
        StringBuilder knowledgeBase = new StringBuilder();
        for (DocDocument doc : allDocs) {
            String summary = doc.getSummary() != null ? doc.getSummary() : "";
            String content = doc.getContent() != null ? doc.getContent() : "";
            String preview = content.length() > 300 ? content.substring(0, 300) : content;
            knowledgeBase.append(String.format("[文档%d] 标题：%s\n  分类ID：%s\n  摘要：%s\n  内容预览：%s\n\n",
                    doc.getId(), doc.getTitle(),
                    doc.getCategoryId() != null ? doc.getCategoryId() : "未分类",
                    summary, preview));
        }

        // 调用 AI 生成
        String systemPrompt = "你是 KnowFlow 学习平台的课程设计师。请根据用户提供的主题和知识库文档，设计一个结构化的学习路径。\n"
                + "输出要求：\n"
                + "1. 必须输出合法 JSON，不要包含 markdown 代码块标记\n"
                + "2. JSON 格式如下：\n"
                + "{\n"
                + "  \"title\": \"学习路径标题\",\n"
                + "  \"description\": \"学习路径描述（50-100字）\",\n"
                + "  \"level\": \"入门|进阶|高级\",\n"
                + "  \"chapters\": [\n"
                + "    {\n"
                + "      \"title\": \"章节标题\",\n"
                + "      \"content\": \"章节内容描述（100-200字）\",\n"
                + "      \"duration\": 30,\n"
                + "      \"sortOrder\": 1\n"
                + "    }\n"
                + "  ]\n"
                + "}\n"
                + "3. 章节数量为 " + chapterCount + " 个\n"
                + "4. 章节应循序渐进，从基础到进阶\n"
                + "5. 每个章节的 duration 单位为分钟\n"
                + "6. 章节内容描述应涵盖对应主题的核心知识点\n";

        String userPrompt = "学习主题：" + topic + "\n";
        if (!description.isEmpty()) {
            userPrompt += "补充说明：" + description + "\n";
        }
        userPrompt += "难度级别：" + level + "\n";
        if (categoryId != null) {
            userPrompt += "参考知识库分类ID：" + categoryId + "\n";
        }
        userPrompt += "\n";
        if (knowledgeBase.length() > 0) {
            userPrompt += "以下是知识库中的相关文档，请紧密参考这些内容设计学习路径和章节：\n" + knowledgeBase;
        } else {
            userPrompt += "注意：当前知识库暂无相关文档，请基于通用知识设计学习路径。\n";
        }

        Long userId = getCurrentUserId();
        try {
            String aiResponse = aiService.complete(systemPrompt, userPrompt, null, userId);
            // 清理可能的 markdown 代码块标记
            String json = aiResponse.trim();
            if (json.startsWith("```json")) json = json.substring(7);
            if (json.startsWith("```")) json = json.substring(3);
            if (json.endsWith("```")) json = json.substring(0, json.length() - 3);
            json = json.trim();

            Map<String, Object> generated = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

            // 创建学习路径
            LearningPath path = new LearningPath();
            path.setTitle((String) generated.getOrDefault("title", topic + "学习路径"));
            path.setDescription((String) generated.getOrDefault("description", ""));
            path.setLevel((String) generated.getOrDefault("level", level));
            path.setChapterCount(0);
            path.setTotalDuration(0);
            path.setEnrolledCount(0);
            path.setSortOrder(0);
            path.setStatus(0); // 默认草稿状态
            pathMapper.insert(path);

            // 创建章节
            List<Map<String, Object>> chapters = (List<Map<String, Object>>) generated.get("chapters");
            if (chapters != null) {
                int totalDuration = 0;
                int sortIdx = 1;
                for (Map<String, Object> ch : chapters) {
                    LearningChapter chapter = new LearningChapter();
                    chapter.setPathId(path.getId());
                    chapter.setTitle((String) ch.getOrDefault("title", "未命名章节"));
                    chapter.setContent((String) ch.getOrDefault("content", ""));
                    int sortOrder = ch.get("sortOrder") != null ? ((Number) ch.get("sortOrder")).intValue() : sortIdx;
                    chapter.setSortOrder(sortOrder);
                    chapter.setDuration(ch.get("duration") != null ? ((Number) ch.get("duration")).intValue() : 30);
                    chapter.setDocIds("");
                    chapter.setFlashcardIds("");
                    chapterMapper.insert(chapter);
                    totalDuration += chapter.getDuration() != null ? chapter.getDuration() : 0;
                    sortIdx++;
                }
                // 更新路径统计
                path.setChapterCount(chapters.size());
                path.setTotalDuration(totalDuration);
                pathMapper.updateById(path);
            }

            return Result.success(path);
        } catch (Exception e) {
            log.error("AI 生成学习路径失败: {}", e.getMessage(), e);
            return Result.error("AI 生成失败：" + e.getMessage());
        }
    }

    @Operation(summary = "AI 为指定章节生成内容（支持指定文档ID）")
    @PostMapping("/chapters/{id}/ai-generate-content")
    public Result<LearningChapter> aiGenerateChapterContent(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        LearningChapter chapter = chapterMapper.selectById(id);
        if (chapter == null) {
            return Result.error("章节不存在");
        }

        LearningPath path = pathMapper.selectById(chapter.getPathId());
        String pathTitle = path != null ? path.getTitle() : "";

        // 收集参考文档：优先使用请求中指定的 docIds，其次使用章节已关联的 docIds
        List<Long> refDocIds = new ArrayList<>();
        if (body != null && body.get("docIds") != null) {
            List<?> ids = (List<?>) body.get("docIds");
            for (Object docIdObj : ids) {
                if (docIdObj instanceof Number) {
                    refDocIds.add(((Number) docIdObj).longValue());
                }
            }
        }
        // 如果请求中未指定，则使用章节已关联的文档
        if (refDocIds.isEmpty() && chapter.getDocIds() != null && !chapter.getDocIds().isEmpty()) {
            for (String idStr : chapter.getDocIds().split(",")) {
                try {
                    refDocIds.add(Long.parseLong(idStr.trim()));
                } catch (NumberFormatException ignored) {}
            }
        }

        // 加载文档内容
        List<DocDocument> relatedDocs = new ArrayList<>();
        for (Long docId : refDocIds) {
            DocDocument doc = docMapper.selectById(docId);
            if (doc != null) relatedDocs.add(doc);
        }

        StringBuilder docContext = new StringBuilder();
        for (int i = 0; i < relatedDocs.size(); i++) {
            DocDocument doc = relatedDocs.get(i);
            String content = doc.getContent() != null ? doc.getContent() : "";
            String preview = content.length() > 2000 ? content.substring(0, 2000) : content;
            docContext.append(String.format("【文档%d】%s\n%s\n\n", i + 1, doc.getTitle(), preview));
        }

        String systemPrompt = "你是 KnowFlow 学习平台的资深讲师。请根据章节信息和参考文档，生成详细的学习内容。\n"
                + "输出要求：\n"
                + "1. 直接输出 Markdown 格式，不要包裹在代码块中\n"
                + "2. 内容结构：本章导读 → 核心知识点讲解（配代码示例）→ 实战练习 → 本章小结\n"
                + "3. 内容必须紧密结合参考文档，不凭空编造\n"
                + "4. 字数 800-2000 字，条理清晰，重点突出\n"
                + "5. 使用 ## / ### 等标题层级，代码块用 ```lang 包裹\n";

        String userPrompt = "学习路径：" + pathTitle + "\n"
                + "章节标题：" + chapter.getTitle() + "\n"
                + "章节描述：" + (chapter.getContent() != null && chapter.getContent().length() < 200
                    ? chapter.getContent() : "请根据文档内容生成详细章节") + "\n";
        if (docContext.length() > 0) {
            userPrompt += "\n参考文档（请主要基于这些内容生成）：\n" + docContext;
        } else {
            userPrompt += "\n注意：暂无关联参考文档，请基于通用知识生成学习内容。";
        }

        Long userId = getCurrentUserId();
        try {
            String content = aiService.complete(systemPrompt, userPrompt, null, userId);
            chapter.setContent(content);
            // 同步更新章节的 docIds（如果是新指定的文档）
            if (body != null && body.get("docIds") != null && !refDocIds.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Long did : refDocIds) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(did);
                }
                chapter.setDocIds(sb.toString());
            }
            chapterMapper.updateById(chapter);
            return Result.success(chapter);
        } catch (Exception e) {
            log.error("AI 生成章节内容失败: {}", e.getMessage(), e);
            return Result.error("AI 生成失败：" + e.getMessage());
        }
    }

    @Operation(summary = "AI 为指定学习路径批量生成闪卡")
    @PostMapping("/paths/{id}/ai-generate-flashcards")
    public Result<List<LearningFlashcard>> aiGenerateFlashcards(@PathVariable Long id) {
        LearningPath path = pathMapper.selectById(id);
        if (path == null) {
            return Result.error("学习路径不存在");
        }

        QueryWrapper<LearningChapter> cw = new QueryWrapper<>();
        cw.eq("path_id", id).orderByAsc("sort_order");
        List<LearningChapter> chapters = chapterMapper.selectList(cw);

        if (chapters.isEmpty()) {
            return Result.error("该路径下没有章节，请先添加章节");
        }

        StringBuilder chapterInfo = new StringBuilder();
        for (LearningChapter ch : chapters) {
            chapterInfo.append(String.format("章节%d：%s\n%s\n\n",
                    ch.getSortOrder() != null ? ch.getSortOrder() : 0,
                    ch.getTitle(),
                    ch.getContent() != null && ch.getContent().length() > 200
                            ? ch.getContent().substring(0, 200) : (ch.getContent() != null ? ch.getContent() : "")));
        }

        String systemPrompt = "你是 KnowFlow 学习平台的出题专家。请根据学习路径和章节内容，生成学习闪卡。\n"
                + "输出要求：\n"
                + "1. 必须输出合法 JSON 数组，不要包含 markdown 代码块标记\n"
                + "2. 每个章节生成 2-3 张闪卡\n"
                + "3. JSON 格式：\n"
                + "[\n"
                + "  {\n"
                + "    \"front\": \"问题（正面）\",\n"
                + "    \"back\": \"答案（背面）\",\n"
                + "    \"chapterId\": 1,\n"
                + "    \"difficulty\": 2,\n"
                + "    \"category\": \"分类名\"\n"
                + "  }\n"
                + "]\n"
                + "4. difficulty: 1简单/2中等/3困难\n";

        String userPrompt = "学习路径：" + path.getTitle() + "\n"
                + "难度级别：" + (path.getLevel() != null ? path.getLevel() : "入门") + "\n\n"
                + "章节列表：\n" + chapterInfo;

        Long userId = getCurrentUserId();
        try {
            String aiResponse = aiService.complete(systemPrompt, userPrompt, null, userId);
            String json = aiResponse.trim();
            if (json.startsWith("```json")) json = json.substring(7);
            if (json.startsWith("```")) json = json.substring(3);
            if (json.endsWith("```")) json = json.substring(0, json.length() - 3);
            json = json.trim();

            List<Map<String, Object>> cards = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            List<LearningFlashcard> created = new ArrayList<>();

            for (Map<String, Object> card : cards) {
                LearningFlashcard flashcard = new LearningFlashcard();
                flashcard.setPathId(id);
                Long chapterId = card.get("chapterId") != null ? ((Number) card.get("chapterId")).longValue() : null;
                flashcard.setChapterId(chapterId);
                flashcard.setFront((String) card.getOrDefault("front", ""));
                flashcard.setBack((String) card.getOrDefault("back", ""));
                flashcard.setCategory(card.get("category") != null ? (String) card.get("category") : path.getTitle());
                flashcard.setDifficulty(card.get("difficulty") != null ? ((Number) card.get("difficulty")).intValue() : 2);
                flashcard.setReviewCount(0);
                flashcard.setReviewInterval(0);
                flashcardMapper.insert(flashcard);
                created.add(flashcard);
            }

            return Result.success(created);
        } catch (Exception e) {
            log.error("AI 生成闪卡失败: {}", e.getMessage(), e);
            return Result.error("AI 生成失败：" + e.getMessage());
        }
    }

    @Operation(summary = "发布学习路径")
    @PutMapping("/paths/{id}/publish")
    public Result<Void> publishPath(@PathVariable Long id) {
        LearningPath path = pathMapper.selectById(id);
        if (path == null) {
            return Result.error("学习路径不存在");
        }
        path.setStatus(1);
        pathMapper.updateById(path);
        return Result.success();
    }

    @Operation(summary = "下架学习路径")
    @PutMapping("/paths/{id}/unpublish")
    public Result<Void> unpublishPath(@PathVariable Long id) {
        LearningPath path = pathMapper.selectById(id);
        if (path == null) {
            return Result.error("学习路径不存在");
        }
        path.setStatus(0);
        pathMapper.updateById(path);
        return Result.success();
    }
}
