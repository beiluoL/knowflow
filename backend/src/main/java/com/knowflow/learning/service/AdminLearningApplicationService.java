package com.knowflow.learning.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.PageResult;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.knowledge.entity.DocCategory;
import com.knowflow.knowledge.entity.DocDocument;
import com.knowflow.learning.entity.LearningChapter;
import com.knowflow.flashcards.entity.LearningFlashcard;
import com.knowflow.learning.entity.LearningPath;
import com.knowflow.exception.BusinessException;
import com.knowflow.knowledge.mapper.DocCategoryMapper;
import com.knowflow.knowledge.mapper.DocDocumentMapper;
import com.knowflow.learning.mapper.LearningChapterMapper;
import com.knowflow.flashcards.mapper.LearningFlashcardMapper;
import com.knowflow.learning.mapper.LearningPathMapper;
import com.knowflow.ai.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@org.springframework.stereotype.Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
/** 管理学习目录及生成用例，生成内容写入保留专用事务。 */
public class AdminLearningApplicationService {

    private final LearningPathMapper pathMapper;
    private final LearningChapterMapper chapterMapper;
    private final LearningFlashcardMapper flashcardMapper;
    private final DocDocumentMapper docMapper;
    private final DocCategoryMapper categoryMapper;
    private final AiService aiService;
    private final com.knowflow.learning.service.AdminLearningWriteService learningWriteService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 从 SecurityContext 获取当前用户 ID；未登录或 principal 异常时抛出 401。 */
    private Long getCurrentUserId() {
        return SecurityUtils.getCurrentUserId();
    }

    // ===== 学习路径 =====
    public Result<List<LearningPath>> listPaths() {
        return Result.success(pathMapper.selectList(null));
    }

    public Result<LearningPath> addPath(LearningPath path) {
        pathMapper.insert(path);
        return Result.success(path);
    }

    public Result<Void> updatePath(Long id, LearningPath path) {
        path.setId(id);
        pathMapper.updateById(path);
        return Result.success();
    }

    public Result<Void> deletePath(Long id) {
        pathMapper.deleteById(id);
        return Result.success();
    }

    // ===== 章节 =====
    public Result<List<LearningChapter>> listChapters(Long pathId) {
        QueryWrapper<LearningChapter> wrapper = new QueryWrapper<>();
        if (pathId != null) wrapper.eq("path_id", pathId);
        wrapper.orderByAsc("sort_order");
        return Result.success(chapterMapper.selectList(wrapper));
    }

    public Result<LearningChapter> addChapter(LearningChapter chapter) {
        chapterMapper.insert(chapter);
        return Result.success(chapter);
    }

    public Result<Void> updateChapter(Long id, LearningChapter chapter) {
        chapter.setId(id);
        chapterMapper.updateById(chapter);
        return Result.success();
    }

    public Result<Void> deleteChapter(Long id) {
        chapterMapper.deleteById(id);
        return Result.success();
    }

    // ===== 闪卡 =====
    public Result<PageResult<LearningFlashcard>> listFlashcards(
            Long pathId,
            Long chapterId,
            Integer pageNum,
            Integer pageSize) {
        Page<LearningFlashcard> page = new Page<>(com.knowflow.common.PageQuery.normalizePageNum(pageNum), com.knowflow.common.PageQuery.normalizePageSize(pageSize));
        QueryWrapper<LearningFlashcard> wrapper = new QueryWrapper<>();
        if (pathId != null) wrapper.eq("path_id", pathId);
        if (chapterId != null) wrapper.eq("chapter_id", chapterId);
        wrapper.orderByDesc("create_time");
        Page<LearningFlashcard> result = flashcardMapper.selectPage(page, wrapper);
        return Result.success(PageResult.of(result));
    }

    public Result<LearningFlashcard> addFlashcard(LearningFlashcard flashcard) {
        // P0-1b：learning_flashcard.user_id 为 NOT NULL，而管理端表单不携带 owner，
        // 直接 insert 会撞 NOT NULL 约束。缺省归属当前登录管理员；
        // 显式传入 userId 时尊重传入值（保留「代指定用户建卡」的集成用法）。
        if (flashcard.getUserId() == null) {
            Long userId = getCurrentUserId();
            flashcard.setUserId(userId);
        }
        flashcardMapper.insert(flashcard);
        return Result.success(flashcard);
    }

    public Result<Void> updateFlashcard(Long id, LearningFlashcard flashcard) {
        flashcard.setId(id);
        flashcardMapper.updateById(flashcard);
        return Result.success();
    }

    public Result<Void> deleteFlashcard(Long id) {
        flashcardMapper.deleteById(id);
        return Result.success();
    }

    // ===== AI 自动生成 =====

    public Result<List<DocCategory>> listCategories() {
        QueryWrapper<DocCategory> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1).orderByAsc("sort_order");
        return Result.success(categoryMapper.selectList(wrapper));
    }

    public Result<List<DocDocument>> listDocsByCategory(
            Long categoryId,
            Integer limit) {
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

    public Result<LearningPath> aiGeneratePath(Map<String, Object> body) {
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
                + "      \"sortOrder\": 1,\n"
                + "      \"focus\": \"本章学习重点\"\n"
                + "    }\n"
                + "  ]\n"
                + "}\n"
                + "3. 章节数量为 " + chapterCount + " 个\n"
                + "4. 章节应循序渐进，从基础到进阶，难度逐步递进\n"
                + "5. 每个章节的 duration 单位为分钟\n"
                + "6. 章节内容描述应涵盖对应主题的核心知识点\n"
                + "7. 每个章节需标注学习重点（focus），帮助学生快速把握核心\n"
                + "8. 前 30% 章节侧重基础概念，中间 40% 侧重核心技能，后 30% 侧重实战应用\n";

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

            LearningPath path = learningWriteService.createGeneratedPath(generated, topic, level);

            return Result.success(path);
        } catch (Exception e) {
            log.error("AI 生成学习路径失败: {}", e.getMessage(), e);
            return Result.error("AI 生成失败：" + e.getMessage());
        }
    }

    public Result<LearningChapter> aiGenerateChapterContent(
            Long id,
            Map<String, Object> body) {
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
                } catch (NumberFormatException ignored) {
                    log.debug("Skipping malformed legacy document reference");
                }
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

    public Result<List<LearningFlashcard>> aiGenerateFlashcards(Long id) {
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
        // P0-1b：userId 原先只用于 AI 调用记账，未落到闪卡上 ——
        // learning_flashcard.user_id 为 NOT NULL，缺失会让整批插入失败。
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
                flashcard.setUserId(userId);
                flashcard.setPathId(id);
                Long chapterId = card.get("chapterId") != null ? ((Number) card.get("chapterId")).longValue() : null;
                flashcard.setChapterId(chapterId);
                flashcard.setFront((String) card.getOrDefault("front", ""));
                flashcard.setBack((String) card.getOrDefault("back", ""));
                flashcard.setCategory(card.get("category") != null ? (String) card.get("category") : path.getTitle());
                flashcard.setDifficulty(card.get("difficulty") != null ? ((Number) card.get("difficulty")).intValue() : 2);
                flashcard.setSourceType("AI_DOC");
                flashcard.setReviewCount(0);
                flashcard.setReviewInterval(0);
                created.add(flashcard);
            }

            return Result.success(learningWriteService.createGeneratedCards(created));
        } catch (Exception e) {
            log.error("AI 生成闪卡失败: {}", e.getMessage(), e);
            return Result.error("AI 生成失败：" + e.getMessage());
        }
    }

    public Result<List<LearningFlashcard>> aiGenerateFlashcardsFromKb(Map<String, Object> body) {
        Long categoryId = body.get("categoryId") != null ? ((Number) body.get("categoryId")).longValue() : null;
        Long docId = body.get("docId") != null ? ((Number) body.get("docId")).longValue() : null;
        int count = body.get("count") != null ? ((Number) body.get("count")).intValue() : 10;

        if (categoryId == null && docId == null) {
            return Result.error("请选择知识库或文档");
        }

        // 收集文档内容
        StringBuilder context = new StringBuilder();
        String sourceName = "";
        if (docId != null) {
            DocDocument doc = docMapper.selectById(docId);
            if (doc == null) return Result.error("文档不存在");
            sourceName = doc.getTitle();
            String content = doc.getContent();
            if (content != null && content.length() > 6000) content = content.substring(0, 6000);
            context.append("文档标题：").append(doc.getTitle()).append("\n\n").append(content);
        } else {
            DocCategory cat = categoryMapper.selectById(categoryId);
            if (cat == null) return Result.error("知识库不存在");
            sourceName = cat.getName();
            QueryWrapper<DocDocument> dw = new QueryWrapper<>();
            dw.eq("category_id", categoryId).eq("status", 1).orderByAsc("sort_order").last("LIMIT 10");
            List<DocDocument> docs = docMapper.selectList(dw);
            for (DocDocument doc : docs) {
                String c = doc.getContent();
                if (c != null && c.length() > 2000) c = c.substring(0, 2000);
                context.append("### ").append(doc.getTitle()).append("\n").append(c).append("\n\n");
            }
        }

        if (context.length() == 0) {
            return Result.error("所选内容为空，无法生成卡片");
        }

        String systemPrompt = "你是 KnowFlow 学习平台的知识卡片专家。请根据提供的知识内容，生成高质量的学习闪卡。\n"
                + "输出要求：\n"
                + "1. 必须输出合法 JSON 数组，不要包含 markdown 代码块标记\n"
                + "2. 生成 " + count + " 张闪卡\n"
                + "3. JSON 格式：\n"
                + "[\n"
                + "  {\n"
                + "    \"front\": \"问题（正面）\",\n"
                + "    \"back\": \"答案（背面）\",\n"
                + "    \"difficulty\": 2,\n"
                + "    \"category\": \"分类名\"\n"
                + "  }\n"
                + "]\n"
                + "4. difficulty: 1简单/2中等/3困难\n"
                + "5. 闪卡应覆盖核心知识点，正面是问题，背面是简洁清晰的答案\n";

        String userPrompt = "知识来源：" + sourceName + "\n\n知识内容：\n" + context;

        Long userId = getCurrentUserId();
        // P0-1b：同 aiGenerateFlashcards —— userId 必须落到闪卡上（user_id NOT NULL）。
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
                flashcard.setUserId(userId);
                flashcard.setDocId(docId);
                flashcard.setCategoryId(categoryId);
                flashcard.setFront((String) card.getOrDefault("front", ""));
                flashcard.setBack((String) card.getOrDefault("back", ""));
                flashcard.setCategory(card.get("category") != null ? (String) card.get("category") : sourceName);
                flashcard.setDifficulty(card.get("difficulty") != null ? ((Number) card.get("difficulty")).intValue() : 2);
                flashcard.setSourceType(docId != null ? "AI_DOC" : "AI_KB");
                flashcard.setReviewCount(0);
                flashcard.setReviewInterval(0);
                created.add(flashcard);
            }

            return Result.success(learningWriteService.createGeneratedCards(created));
        } catch (Exception e) {
            log.error("AI 生成知识卡片失败: {}", e.getMessage(), e);
            return Result.error("AI 生成失败：" + e.getMessage());
        }
    }

    public Result<Void> publishPath(Long id) {
        LearningPath path = pathMapper.selectById(id);
        if (path == null) {
            return Result.error("学习路径不存在");
        }
        path.setStatus(1);
        pathMapper.updateById(path);
        return Result.success();
    }

    public Result<Void> unpublishPath(Long id) {
        LearningPath path = pathMapper.selectById(id);
        if (path == null) {
            return Result.error("学习路径不存在");
        }
        path.setStatus(0);
        pathMapper.updateById(path);
        return Result.success();
    }
}
