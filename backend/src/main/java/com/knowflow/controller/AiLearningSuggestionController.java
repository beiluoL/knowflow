package com.knowflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.Result;
import com.knowflow.entity.AiLearningSuggestion;
import com.knowflow.entity.DocReadProgress;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.entity.LearningMistake;
import com.knowflow.entity.UserCheckIn;
import com.knowflow.mapper.AiLearningSuggestionMapper;
import com.knowflow.mapper.DocReadProgressMapper;
import com.knowflow.mapper.LearningFlashcardMapper;
import com.knowflow.mapper.LearningMistakeMapper;
import com.knowflow.mapper.UserCheckInMapper;
import com.knowflow.service.AiService;
import com.knowflow.vo.AiLearningSuggestionVO;
import com.knowflow.vo.AiLearningSuggestionVO.SuggestionItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * AI 智能学习建议接口。
 * 根据用户学习数据由大模型生成 4 条个性化学习建议，按用户+周期维度缓存。
 */
@Slf4j
@Tag(name = "AI 智能学习建议接口")
@RestController
@RequestMapping("/api/learning/suggestions")
@RequiredArgsConstructor
public class AiLearningSuggestionController {

    private final AiLearningSuggestionMapper suggestionMapper;
    private final AiService aiService;
    private final UserCheckInMapper checkInMapper;
    private final LearningFlashcardMapper flashcardMapper;
    private final LearningMistakeMapper mistakeMapper;
    private final DocReadProgressMapper readProgressMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 获取学习建议：先查缓存（同 user_id + period），命中则返回；未命中则生成并缓存 */
    @Operation(summary = "获取学习建议（带缓存）")
    @GetMapping
    public Result<AiLearningSuggestionVO> get(@RequestParam(required = false, defaultValue = "week") String period,
                                              Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        String normalizedPeriod = normalizePeriod(period);
        AiLearningSuggestion cached = suggestionMapper.selectOne(new LambdaQueryWrapper<AiLearningSuggestion>()
                .eq(AiLearningSuggestion::getUserId, userId)
                .eq(AiLearningSuggestion::getPeriod, normalizedPeriod)
                .orderByDesc(AiLearningSuggestion::getCreateTime)
                .last("LIMIT 1"));
        if (cached != null) {
            return Result.success(toVO(cached));
        }
        AiLearningSuggestion fresh = generateSuggestions(userId, normalizedPeriod);
        return Result.success(toVO(fresh));
    }

    /** 重新生成建议：删除旧缓存，重新生成 */
    @Operation(summary = "重新生成学习建议")
    @PostMapping("/regenerate")
    public Result<AiLearningSuggestionVO> regenerate(@RequestParam(required = false, defaultValue = "week") String period,
                                                     Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        String normalizedPeriod = normalizePeriod(period);
        // 逻辑删除旧缓存
        suggestionMapper.delete(new LambdaQueryWrapper<AiLearningSuggestion>()
                .eq(AiLearningSuggestion::getUserId, userId)
                .eq(AiLearningSuggestion::getPeriod, normalizedPeriod));
        AiLearningSuggestion fresh = generateSuggestions(userId, normalizedPeriod);
        return Result.success(toVO(fresh));
    }

    /** 生成学习建议并缓存 */
    private AiLearningSuggestion generateSuggestions(Long userId, String period) {
        List<SuggestionItem> items = callAiForSuggestions(userId, period);
        AiLearningSuggestion entity = new AiLearningSuggestion();
        entity.setUserId(userId);
        entity.setPeriod(period);
        try {
            entity.setSuggestions(objectMapper.writeValueAsString(items));
        } catch (Exception e) {
            log.warn("学习建议序列化失败: {}", e.getMessage());
            entity.setSuggestions("[]");
        }
        suggestionMapper.insert(entity);
        return entity;
    }

    /** 调用 AI 生成 4 条学习建议，AI 不可用时降级返回默认建议 */
    @SuppressWarnings("unchecked")
    private List<SuggestionItem> callAiForSuggestions(Long userId, String period) {
        String userProfile = buildUserProfile(userId, period);
        String systemPrompt = "你是 KnowFlow 学习平台的 AI 学习顾问，擅长根据用户学习数据给出可执行的个性化学习建议。";
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("请根据以下用户学习数据，生成 4 条个性化学习建议。\n\n");
        userPrompt.append("周期：").append(period).append("\n");
        userPrompt.append("用户学习数据：\n").append(userProfile).append("\n\n");
        userPrompt.append("要求：\n");
        userPrompt.append("1. 返回严格 JSON 数组，元素格式：{\"title\":\"建议标题\",\"desc\":\"具体描述\",\"icon\":\"图标\",\"path\":\"跳转路径\"}\n");
        userPrompt.append("2. icon 只能从以下取值：refresh-cw / code / book-open / message-circle\n");
        userPrompt.append("3. path 只能从以下取值：/learning/flashcards / /learning/code-practice / /categories / /chat\n");
        userPrompt.append("4. 不要输出 JSON 以外的任何文字\n");

        String raw;
        try {
            raw = aiService.complete(systemPrompt, userPrompt.toString(), null, userId);
        } catch (Exception e) {
            log.warn("AI 学习建议生成失败，降级返回默认建议: {}", e.getMessage());
            return defaultSuggestions();
        }
        if (raw == null || raw.isBlank()) {
            return defaultSuggestions();
        }
        try {
            String json = raw.trim();
            int start = json.indexOf('[');
            int end = json.lastIndexOf(']');
            if (start < 0 || end <= start) {
                return defaultSuggestions();
            }
            json = json.substring(start, end + 1);
            List<Map<String, Object>> parsed = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            List<SuggestionItem> items = new ArrayList<>();
            for (Map<String, Object> m : parsed) {
                SuggestionItem item = new SuggestionItem();
                item.setTitle(asString(m.get("title")));
                item.setDesc(asString(m.get("desc")));
                item.setIcon(asString(m.get("icon")));
                item.setPath(asString(m.get("path")));
                if (item.getTitle() != null && !item.getTitle().isBlank()) {
                    items.add(item);
                }
            }
            if (items.isEmpty()) {
                return defaultSuggestions();
            }
            return items;
        } catch (Exception e) {
            log.warn("学习建议 JSON 解析失败: {}", e.getMessage());
            return defaultSuggestions();
        }
    }

    /** 聚合用户学习数据为提示文本 */
    private String buildUserProfile(Long userId, String period) {
        long checkinDays = checkInMapper.selectCount(new LambdaQueryWrapper<UserCheckIn>()
                .eq(UserCheckIn::getUserId, userId));
        long flashcardCount = flashcardMapper.selectCount(new LambdaQueryWrapper<LearningFlashcard>()
                .eq(LearningFlashcard::getUserId, userId));
        long mistakeCount = mistakeMapper.selectCount(new LambdaQueryWrapper<LearningMistake>()
                .eq(LearningMistake::getUserId, userId));
        List<DocReadProgress> reads = readProgressMapper.selectList(new LambdaQueryWrapper<DocReadProgress>()
                .eq(DocReadProgress::getUserId, userId));
        int docsRead = 0;
        for (DocReadProgress r : reads) {
            BigDecimal p = r.getProgress();
            if (p != null && p.compareTo(BigDecimal.ZERO) > 0) {
                docsRead++;
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("- 累计打卡天数：").append(checkinDays).append("\n");
        sb.append("- 创建闪卡数：").append(flashcardCount).append("\n");
        sb.append("- 错题数：").append(mistakeCount).append("\n");
        sb.append("- 阅读文档数：").append(docsRead).append("\n");
        return sb.toString();
    }

    /** AI 不可用时的静态降级建议 */
    private List<SuggestionItem> defaultSuggestions() {
        List<SuggestionItem> items = new ArrayList<>();
        items.add(buildItem("复习闪卡", "巩固记忆，回顾近期学过的卡片", "refresh-cw", "/learning/flashcards"));
        items.add(buildItem("代码练习", "动手实践加深理解，完成今日编程题", "code", "/learning/code-practice"));
        items.add(buildItem("浏览知识库", "拓展学习广度，发现新的学习资料", "book-open", "/categories"));
        items.add(buildItem("AI 答疑", "遇到疑问随时向 AI 提问，加速理解", "message-circle", "/chat"));
        return items;
    }

    private SuggestionItem buildItem(String title, String desc, String icon, String path) {
        SuggestionItem item = new SuggestionItem();
        item.setTitle(title);
        item.setDesc(desc);
        item.setIcon(icon);
        item.setPath(path);
        return item;
    }

    private String normalizePeriod(String period) {
        if (period == null || period.isBlank()) {
            return "week";
        }
        String p = period.toLowerCase();
        return "month".equals(p) ? "month" : "week";
    }

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private AiLearningSuggestionVO toVO(AiLearningSuggestion entity) {
        AiLearningSuggestionVO vo = new AiLearningSuggestionVO();
        vo.setId(entity.getId());
        vo.setPeriod(entity.getPeriod());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        List<SuggestionItem> items = Collections.emptyList();
        if (entity.getSuggestions() != null && !entity.getSuggestions().isBlank()) {
            try {
                items = objectMapper.readValue(entity.getSuggestions(),
                        new TypeReference<List<SuggestionItem>>() {});
            } catch (Exception e) {
                log.warn("学习建议反序列化失败: {}", e.getMessage());
            }
        }
        vo.setSuggestions(items);
        return vo;
    }
}
