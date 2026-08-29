package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.entity.CodeQuestion;
import com.knowflow.entity.DocDocument;
import com.knowflow.entity.KgEntity;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.entity.LearningMistake;
import com.knowflow.entity.QuizQuestion;
import com.knowflow.entity.ResourceKnowledgeMapping;
import com.knowflow.entity.WbRecallSession;
import com.knowflow.entity.WbReviewCard;
import com.knowflow.mapper.CodeQuestionMapper;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.mapper.KgEntityMapper;
import com.knowflow.mapper.LearningFlashcardMapper;
import com.knowflow.mapper.LearningMistakeMapper;
import com.knowflow.mapper.QuizQuestionMapper;
import com.knowflow.mapper.ResourceKnowledgeMappingMapper;
import com.knowflow.mapper.WbRecallSessionMapper;
import com.knowflow.mapper.WbReviewCardMapper;
import com.knowflow.mastery.MasteryConfig;
import com.knowflow.service.ResourceKnowledgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 资源 → 知识点映射服务实现（Knowledge Mastery Engine，Phase 2-B）。
 * <p>关键词级最佳努力匹配（v1 不调 LLM）；架构已预留 AI 补链扩展点。</p>
 */
@Slf4j
@Service
public class ResourceKnowledgeServiceImpl implements ResourceKnowledgeService {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+");

    private final ResourceKnowledgeMappingMapper mappingMapper;
    private final KgEntityMapper kgEntityMapper;
    private final QuizQuestionMapper quizQuestionMapper;
    private final CodeQuestionMapper codeQuestionMapper;
    private final LearningMistakeMapper learningMistakeMapper;
    private final WbReviewCardMapper wbReviewCardMapper;
    private final WbRecallSessionMapper wbRecallSessionMapper;
    private final LearningFlashcardMapper learningFlashcardMapper;
    private final DocDocumentMapper docDocumentMapper;

    public ResourceKnowledgeServiceImpl(ResourceKnowledgeMappingMapper mappingMapper,
                                        KgEntityMapper kgEntityMapper,
                                        QuizQuestionMapper quizQuestionMapper,
                                        CodeQuestionMapper codeQuestionMapper,
                                        LearningMistakeMapper learningMistakeMapper,
                                        WbReviewCardMapper wbReviewCardMapper,
                                        WbRecallSessionMapper wbRecallSessionMapper,
                                        LearningFlashcardMapper learningFlashcardMapper,
                                        DocDocumentMapper docDocumentMapper) {
        this.mappingMapper = mappingMapper;
        this.kgEntityMapper = kgEntityMapper;
        this.quizQuestionMapper = quizQuestionMapper;
        this.codeQuestionMapper = codeQuestionMapper;
        this.learningMistakeMapper = learningMistakeMapper;
        this.wbReviewCardMapper = wbReviewCardMapper;
        this.wbRecallSessionMapper = wbRecallSessionMapper;
        this.learningFlashcardMapper = learningFlashcardMapper;
        this.docDocumentMapper = docDocumentMapper;
    }

    // ============================================================
    // 解析
    // ============================================================

    @Override
    public List<Long> resolveKnowledgeIds(String resourceType, Long resourceId) {
        if (resourceType == null || resourceId == null) {
            return new ArrayList<>();
        }
        List<ResourceKnowledgeMapping> mappings = mappingMapper.selectList(
                new LambdaQueryWrapper<ResourceKnowledgeMapping>()
                        .eq(ResourceKnowledgeMapping::getResourceType, resourceType)
                        .eq(ResourceKnowledgeMapping::getResourceId, resourceId)
                        .eq(ResourceKnowledgeMapping::getStatus, MasteryConfig.MAP_STATUS_ACCEPTED));
        List<Long> result = new ArrayList<>();
        for (ResourceKnowledgeMapping m : mappings) {
            result.add(m.getKnowledgeId());
        }
        return result;
    }

    // ============================================================
    // 单资源 AUTO 匹配
    // ============================================================

    @Override
    public void buildAutoMappingForResource(String resourceType, Long resourceId) {
        if (resourceType == null || resourceId == null) {
            return;
        }
        List<KgEntity> entities = loadLearnableEntities();
        if (entities.isEmpty()) {
            return;
        }
        ResourceText text = extractResourceText(resourceType, resourceId);
        if (text == null) {
            return;
        }
        Set<String> textTokens = tokenize(text.getText());
        Set<Long> existing = loadExistingKnowledgeIds(resourceType, resourceId);

        boolean matched = false;
        for (KgEntity e : entities) {
            double score = scoreEntity(e, text.getText(), textTokens);
            if (score >= MasteryConfig.AUTO_PENDING_THRESHOLD) {
                matched = true;
                insertMappingIfAbsent(resourceType, resourceId, e.getId(),
                        MasteryConfig.MAP_SOURCE_AUTO,
                        BigDecimal.valueOf(score),
                        score >= MasteryConfig.AUTO_THRESHOLD
                                ? MasteryConfig.MAP_STATUS_ACCEPTED : MasteryConfig.MAP_STATUS_PENDING,
                        existing);
            }
        }

        // 分类极低置信兜底：仅当无任何 AUTO 命中，且该分类下存在与资源标题共享词元的实体
        if (!matched && text.getCategoryId() != null) {
            Set<String> titleTokens = tokenize(text.getTitle());
            for (KgEntity e : entities) {
                if (e.getCategoryId() != null && e.getCategoryId().equals(text.getCategoryId())
                        && shareAnyToken(titleTokens, e)) {
                    insertMappingIfAbsent(resourceType, resourceId, e.getId(),
                            MasteryConfig.MAP_SOURCE_CATEGORY,
                            MasteryConfig.CATEGORY_FALLBACK_CONFIDENCE,
                            MasteryConfig.MAP_STATUS_PENDING,
                            existing);
                }
            }
        }
    }

    // ============================================================
    // 全量构建（回填）
    // ============================================================

    @Override
    public void buildAllMappings() {
        List<KgEntity> entities = loadLearnableEntities();
        if (entities.isEmpty()) {
            log.info("资源→知识点映射构建跳过：无可学习 kg_entity（可能文档尚未被 AI 抽取）");
            return;
        }
        buildFor(entities, MasteryConfig.RES_QUIZ, quizQuestionMapper.selectList(null));
        buildFor(entities, MasteryConfig.RES_CODE_QUESTION, codeQuestionMapper.selectList(null));
        buildFor(entities, MasteryConfig.RES_MISTAKE, learningMistakeMapper.selectList(null));
        buildFor(entities, MasteryConfig.RES_REVIEW_CARD, wbReviewCardMapper.selectList(null));
        buildFor(entities, MasteryConfig.RES_RECALL_SESSION, wbRecallSessionMapper.selectList(null));
        buildFor(entities, MasteryConfig.RES_FLASHCARD, learningFlashcardMapper.selectList(null));
        buildFor(entities, MasteryConfig.RES_DOC, docDocumentMapper.selectList(null));
        log.info("资源→知识点映射构建完成");
    }

    private void buildFor(List<KgEntity> entities, String resourceType, List<?> resources) {
        if (resources == null || resources.isEmpty()) {
            return;
        }
        Set<Long> allExisting = loadExistingResourceIds(resourceType);
        for (Object res : resources) {
            Long id = extractId(res);
            if (id == null || allExisting.contains(id)) {
                continue;
            }
            buildAutoMappingForResource(resourceType, id);
        }
    }

    // ============================================================
    // 内部工具
    // ============================================================

    private List<KgEntity> loadLearnableEntities() {
        List<String> types = new ArrayList<>();
        for (String t : MasteryConfig.LEARNABLE_TYPES) {
            types.add(t);
        }
        if (MasteryConfig.TOOL_ENABLED) {
            types.add("TOOL");
        }
        if (types.isEmpty()) {
            return new ArrayList<>();
        }
        return kgEntityMapper.selectList(new LambdaQueryWrapper<KgEntity>().in(KgEntity::getType, types));
    }

    private Set<Long> loadExistingKnowledgeIds(String resourceType, Long resourceId) {
        List<ResourceKnowledgeMapping> mappings = mappingMapper.selectList(
                new LambdaQueryWrapper<ResourceKnowledgeMapping>()
                        .eq(ResourceKnowledgeMapping::getResourceType, resourceType)
                        .eq(ResourceKnowledgeMapping::getResourceId, resourceId));
        Set<Long> set = new HashSet<>();
        for (ResourceKnowledgeMapping m : mappings) {
            set.add(m.getKnowledgeId());
        }
        return set;
    }

    private Set<Long> loadExistingResourceIds(String resourceType) {
        List<ResourceKnowledgeMapping> mappings = mappingMapper.selectList(
                new LambdaQueryWrapper<ResourceKnowledgeMapping>()
                        .eq(ResourceKnowledgeMapping::getResourceType, resourceType));
        Set<Long> set = new HashSet<>();
        for (ResourceKnowledgeMapping m : mappings) {
            set.add(m.getResourceId());
        }
        return set;
    }

    private void insertMappingIfAbsent(String resourceType, Long resourceId, Long knowledgeId,
                                       String source, BigDecimal confidence, String status,
                                       Set<Long> existing) {
        if (existing.contains(knowledgeId)) {
            return;
        }
        existing.add(knowledgeId);
        ResourceKnowledgeMapping m = new ResourceKnowledgeMapping();
        m.setResourceType(resourceType);
        m.setResourceId(resourceId);
        m.setKnowledgeId(knowledgeId);
        m.setSource(source);
        m.setConfidence(confidence);
        m.setStatus(status);
        mappingMapper.insert(m);
    }

    private double scoreEntity(KgEntity e, String text, Set<String> textTokens) {
        if (e.getName() == null || e.getName().isBlank()) {
            return 0.0;
        }
        double best = 0.0;
        String nameLower = e.getName().toLowerCase();
        String textLower = text.toLowerCase();
        if (nameLower.length() >= 2 && textLower.contains(nameLower)) {
            best = Math.max(best, 0.9);
        }
        Set<String> entTokens = tokenize(e.getName() + " " + (e.getDescription() == null ? "" : e.getDescription()));
        best = Math.max(best, jaccard(textTokens, entTokens));
        return best;
    }

    private boolean shareAnyToken(Set<String> titleTokens, KgEntity e) {
        if (titleTokens.isEmpty()) {
            return false;
        }
        Set<String> entTokens = tokenize(e.getName() + " " + (e.getDescription() == null ? "" : e.getDescription()));
        for (String t : entTokens) {
            if (titleTokens.contains(t)) {
                return true;
            }
        }
        return false;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        int inter = 0;
        for (String t : a) {
            if (b.contains(t)) {
                inter++;
            }
        }
        int union = a.size() + b.size() - inter;
        return union == 0 ? 0.0 : (double) inter / union;
    }

    private static Set<String> tokenize(String s) {
        Set<String> tokens = new HashSet<>();
        if (s == null || s.isBlank()) {
            return tokens;
        }
        for (String t : TOKEN_SPLIT.split(s.toLowerCase())) {
            if (t.length() >= 2) {
                tokens.add(t);
            }
        }
        return tokens;
    }

    private ResourceText extractResourceText(String resourceType, Long resourceId) {
        switch (resourceType) {
            case MasteryConfig.RES_QUIZ: {
                QuizQuestion q = quizQuestionMapper.selectById(resourceId);
                return q == null ? null
                        : new ResourceText(q.getTitle(), join(q.getTitle(), q.getContent(), q.getTags()), q.getCategoryId());
            }
            case MasteryConfig.RES_CODE_QUESTION: {
                CodeQuestion q = codeQuestionMapper.selectById(resourceId);
                return q == null ? null
                        : new ResourceText(q.getTitle(), join(q.getTitle(), q.getDescription(), q.getTags()), null);
            }
            case MasteryConfig.RES_MISTAKE: {
                LearningMistake m = learningMistakeMapper.selectById(resourceId);
                return m == null ? null
                        : new ResourceText(m.getQuestion(), join(m.getQuestion(), m.getCorrectAnswer(), m.getWrongAnswer()), null);
            }
            case MasteryConfig.RES_REVIEW_CARD: {
                WbReviewCard c = wbReviewCardMapper.selectById(resourceId);
                return c == null ? null
                        : new ResourceText(c.getFront(), join(c.getFront(), c.getBack()), c.getCategoryId());
            }
            case MasteryConfig.RES_RECALL_SESSION: {
                WbRecallSession s = wbRecallSessionMapper.selectById(resourceId);
                return s == null ? null
                        : new ResourceText(s.getTitle(), join(s.getTitle(), s.getSourceText()), null);
            }
            case MasteryConfig.RES_FLASHCARD: {
                LearningFlashcard f = learningFlashcardMapper.selectById(resourceId);
                return f == null ? null
                        : new ResourceText(f.getFront(), join(f.getFront(), f.getBack(), f.getTags()), f.getCategoryId());
            }
            case MasteryConfig.RES_DOC: {
                DocDocument d = docDocumentMapper.selectById(resourceId);
                return d == null ? null
                        : new ResourceText(d.getTitle(), join(d.getTitle(), d.getContent(), d.getSummary(), d.getTags()), d.getCategoryId());
            }
            default:
                return null;
        }
    }

    private static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                sb.append(p).append(" ");
            }
        }
        return sb.toString();
    }

    private Long extractId(Object res) {
        if (res instanceof QuizQuestion) {
            return ((QuizQuestion) res).getId();
        }
        if (res instanceof CodeQuestion) {
            return ((CodeQuestion) res).getId();
        }
        if (res instanceof LearningMistake) {
            return ((LearningMistake) res).getId();
        }
        if (res instanceof WbReviewCard) {
            return ((WbReviewCard) res).getId();
        }
        if (res instanceof WbRecallSession) {
            return ((WbRecallSession) res).getId();
        }
        if (res instanceof LearningFlashcard) {
            return ((LearningFlashcard) res).getId();
        }
        if (res instanceof DocDocument) {
            return ((DocDocument) res).getId();
        }
        return null;
    }

    /** 资源文本载体：标题（分类兜底用）+ 全文（匹配用）+ 分类ID。 */
    private static final class ResourceText {
        private final String title;
        private final String text;
        private final Long categoryId;

        ResourceText(String title, String text, Long categoryId) {
            this.title = title;
            this.text = text;
            this.categoryId = categoryId;
        }

        String getTitle() {
            return title;
        }

        String getText() {
            return text;
        }

        Long getCategoryId() {
            return categoryId;
        }
    }
}
