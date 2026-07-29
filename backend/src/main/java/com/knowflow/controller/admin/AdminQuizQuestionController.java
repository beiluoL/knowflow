package com.knowflow.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.PageResult;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.entity.DocCategory;
import com.knowflow.entity.DocDocument;
import com.knowflow.entity.QuizQuestion;
import com.knowflow.mapper.DocCategoryMapper;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.mapper.QuizQuestionMapper;
import com.knowflow.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Tag(name = "管理端-题库管理")
@RestController
@RequestMapping("/api/admin/quiz-questions")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminQuizQuestionController {

    private final QuizQuestionMapper quizQuestionMapper;
    private final DocCategoryMapper docCategoryMapper;
    private final DocDocumentMapper docDocumentMapper;
    private final AiService aiService;
    private final ObjectMapper objectMapper;

    // ==================== CRUD ====================

    @Operation(summary = "题目列表（分页 + 筛选）")
    @GetMapping
    public Result<PageResult<QuizQuestion>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long categoryId) {

        QueryWrapper<QuizQuestion> qw = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like("title", keyword).or().like("content", keyword));
        }
        if (questionType != null && !questionType.isBlank()) {
            qw.eq("question_type", questionType);
        }
        if (difficulty != null) {
            qw.eq("difficulty", difficulty);
        }
        if (source != null && !source.isBlank()) {
            qw.eq("source", source);
        }
        if (status != null) {
            qw.eq("status", status);
        }
        if (categoryId != null) {
            qw.eq("category_id", categoryId);
        }
        qw.orderByDesc("create_time");

        Page<QuizQuestion> p = quizQuestionMapper.selectPage(
                new Page<>(page, pageSize), qw);

        return Result.success(PageResult.of(p));
    }

    @Operation(summary = "题目详情")
    @GetMapping("/{id}")
    public Result<QuizQuestion> detail(@PathVariable Long id) {
        QuizQuestion q = quizQuestionMapper.selectById(id);
        if (q == null) {
            return Result.error("题目不存在");
        }
        return Result.success(q);
    }

    @Operation(summary = "手动新增题目")
    @PostMapping
    public Result<QuizQuestion> create(@RequestBody QuizQuestion question) {
        if (question.getTitle() == null || question.getTitle().isBlank()) {
            return Result.error("题目标题不能为空");
        }
        if (question.getContent() == null || question.getContent().isBlank()) {
            return Result.error("题干内容不能为空");
        }
        if (question.getQuestionType() == null) {
            question.setQuestionType(QuizQuestion.TYPE_SINGLE_CHOICE);
        }
        if (question.getDifficulty() == null) {
            question.setDifficulty(2);
        }
        if (question.getSource() == null) {
            question.setSource(QuizQuestion.SOURCE_MANUAL);
        }
        if (question.getStatus() == null) {
            question.setStatus(1);
        }
        quizQuestionMapper.insert(question);
        return Result.success(question);
    }

    @Operation(summary = "编辑题目")
    @PutMapping("/{id}")
    public Result<QuizQuestion> update(@PathVariable Long id, @RequestBody QuizQuestion question) {
        QuizQuestion existing = quizQuestionMapper.selectById(id);
        if (existing == null) {
            return Result.error("题目不存在");
        }
        question.setId(id);
        quizQuestionMapper.updateById(question);
        return Result.success(question);
    }

    @Operation(summary = "删除题目")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        quizQuestionMapper.deleteById(id);
        return Result.success();
    }

    @Operation(summary = "发布题目")
    @PutMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        QuizQuestion q = quizQuestionMapper.selectById(id);
        if (q == null) return Result.error("题目不存在");
        q.setStatus(1);
        quizQuestionMapper.updateById(q);
        return Result.success();
    }

    @Operation(summary = "下架题目")
    @PutMapping("/{id}/unpublish")
    public Result<Void> unpublish(@PathVariable Long id) {
        QuizQuestion q = quizQuestionMapper.selectById(id);
        if (q == null) return Result.error("题目不存在");
        q.setStatus(0);
        quizQuestionMapper.updateById(q);
        return Result.success();
    }

    // ==================== AI 自动出题 ====================

    @Operation(summary = "AI 根据知识库/文档生成题目")
    @PostMapping("/ai-generate")
    public Result<List<QuizQuestion>> aiGenerate(@RequestBody Map<String, Object> body) {
        Long categoryId = body.get("categoryId") != null ? ((Number) body.get("categoryId")).longValue() : null;
        Long docId = body.get("docId") != null ? ((Number) body.get("docId")).longValue() : null;
        String questionType = body.get("questionType") != null ? (String) body.get("questionType") : QuizQuestion.TYPE_SINGLE_CHOICE;
        int count = body.get("count") != null ? ((Number) body.get("count")).intValue() : 5;

        if (categoryId == null && docId == null) {
            return Result.error("请选择知识库或文档");
        }

        // 收集文档内容作为上下文
        StringBuilder context = new StringBuilder();
        String sourceName = "";

        if (docId != null) {
            DocDocument doc = docDocumentMapper.selectById(docId);
            if (doc == null) return Result.error("文档不存在");
            sourceName = doc.getTitle();
            String content = doc.getContent();
            if (content != null && content.length() > 6000) {
                content = content.substring(0, 6000);
            }
            context.append("文档标题：").append(doc.getTitle()).append("\n\n").append(content);
        } else {
            DocCategory cat = docCategoryMapper.selectById(categoryId);
            if (cat == null) return Result.error("知识库不存在");
            sourceName = cat.getName();
            QueryWrapper<DocDocument> dw = new QueryWrapper<>();
            dw.eq("category_id", categoryId).eq("status", 1).orderByAsc("sort_order").last("LIMIT 10");
            List<DocDocument> docs = docDocumentMapper.selectList(dw);
            for (DocDocument doc : docs) {
                String c = doc.getContent();
                if (c != null && c.length() > 2000) c = c.substring(0, 2000);
                context.append("### ").append(doc.getTitle()).append("\n").append(c).append("\n\n");
            }
        }

        if (context.length() == 0) {
            return Result.error("所选内容为空，无法生成题目");
        }

        // 根据题型构造不同的 prompt
        String typeDesc = switch (questionType) {
            case QuizQuestion.TYPE_MULTIPLE_CHOICE -> "多项选择题（有多个正确选项）";
            case QuizQuestion.TYPE_FILL_BLANK -> "填空题（题干中用 ____ 标出空缺位置）";
            case QuizQuestion.TYPE_TRUE_FALSE -> "判断题（判断对错）";
            case QuizQuestion.TYPE_SHORT_ANSWER -> "简答题（开放性问题）";
            default -> "单项选择题（只有一个正确选项）";
        };

        String optionsFormat = questionType.equals(QuizQuestion.TYPE_FILL_BLANK)
                || questionType.equals(QuizQuestion.TYPE_SHORT_ANSWER)
                ? "（此题型不需要 options，设为 null）" : "options 为字符串数组，如 [\"选项A\",\"选项B\",\"选项C\",\"选项D\"]";

        String answerFormat = switch (questionType) {
            case QuizQuestion.TYPE_MULTIPLE_CHOICE -> "answer 为正确选项的索引，多个用逗号分隔，如 \"0,2\"";
            case QuizQuestion.TYPE_TRUE_FALSE -> "answer 为 \"true\" 或 \"false\"";
            case QuizQuestion.TYPE_FILL_BLANK -> "answer 为填空答案文本";
            case QuizQuestion.TYPE_SHORT_ANSWER -> "answer 为参考答案要点";
            default -> "answer 为正确选项的索引，如 \"0\"";
        };

        String systemPrompt = "你是 KnowFlow 学习平台的出题专家。请根据提供的知识内容，生成高质量的" + typeDesc + "。\n"
                + "输出要求：\n"
                + "1. 必须输出合法 JSON 数组，不要包含 markdown 代码块标记\n"
                + "2. 生成 " + count + " 道题目\n"
                + "3. JSON 格式：\n"
                + "[\n"
                + "  {\n"
                + "    \"title\": \"题目标题（简短概括）\",\n"
                + "    \"content\": \"题干内容（详细描述）\",\n"
                + "    \"questionType\": \"" + questionType + "\",\n"
                + "    \"options\": " + optionsFormat + ",\n"
                + "    \"answer\": \"正确答案\",\n"
                + "    \"explanation\": \"答案解析（说明为什么选这个答案）\",\n"
                + "    \"difficulty\": 2,\n"
                + "    \"tags\": \"标签1,标签2\"\n"
                + "  }\n"
                + "]\n"
                + "4. difficulty: 1简单/2中等/3困难\n"
                + "5. " + answerFormat + "\n"
                + "6. 题目应覆盖核心知识点，避免过于简单或过于偏门\n";

        String userPrompt = "知识来源：" + sourceName + "\n\n"
                + "知识内容：\n" + context;

        Long userId = SecurityUtils.getCurrentUserId();
        try {
            String aiResponse = aiService.complete(systemPrompt, userPrompt, null, userId);
            String json = aiResponse.trim();
            if (json.startsWith("```json")) json = json.substring(7);
            if (json.startsWith("```")) json = json.substring(3);
            if (json.endsWith("```")) json = json.substring(0, json.length() - 3);
            json = json.trim();

            List<Map<String, Object>> items = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            List<QuizQuestion> created = new ArrayList<>();

            for (Map<String, Object> item : items) {
                QuizQuestion q = new QuizQuestion();
                q.setTitle((String) item.getOrDefault("title", "未命名题目"));
                q.setContent((String) item.getOrDefault("content", ""));
                q.setQuestionType(questionType);
                // options 序列化为 JSON 字符串存储
                Object opts = item.get("options");
                if (opts != null) {
                    q.setOptions(opts instanceof String ? (String) opts : objectMapper.writeValueAsString(opts));
                }
                q.setAnswer(String.valueOf(item.getOrDefault("answer", "")));
                q.setExplanation((String) item.get("explanation"));
                q.setDifficulty(item.get("difficulty") != null ? ((Number) item.get("difficulty")).intValue() : 2);
                q.setCategoryId(categoryId);
                q.setDocId(docId);
                q.setTags((String) item.getOrDefault("tags", ""));
                q.setSource(QuizQuestion.SOURCE_AI);
                q.setStatus(1);
                q.setSortOrder(0);
                quizQuestionMapper.insert(q);
                created.add(q);
            }

            return Result.success(created);
        } catch (Exception e) {
            log.error("AI 生成题目失败: {}", e.getMessage(), e);
            return Result.error("AI 生成失败：" + e.getMessage());
        }
    }
}
