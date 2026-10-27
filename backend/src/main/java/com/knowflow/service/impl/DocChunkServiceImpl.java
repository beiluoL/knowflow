package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.entity.DocChunk;
import com.knowflow.entity.DocDocument;
import com.knowflow.mapper.DocChunkMapper;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.service.DocChunkService;
import com.knowflow.service.EmbeddingService;
import com.knowflow.service.KbMemberService;
import com.knowflow.service.CategoryService;
import com.knowflow.entity.DocCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文档分块服务实现：分块策略为按段落（\n\n）切分，每块上限约 500 字符；
 * 通过 EmbeddingService 生成向量并支持余弦相似度检索。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocChunkServiceImpl implements DocChunkService {

    private final DocChunkMapper docChunkMapper;
    private final EmbeddingService embeddingService;
    private final DocDocumentMapper docDocumentMapper;
    private final KbMemberService kbMemberService;
    private final CategoryService categoryService;
    private final com.knowflow.security.DocumentAccessPolicy access;

    /** 单块最大字符数 */
    private static final int MAX_CHUNK_SIZE = 500;

    /** 语义召回的相似度下限，过滤明显不相关的分块 */
    private static final double MIN_SIMILARITY = 0.3;

    /** 语义召回的文档数上限 */
    private static final int MAX_SEMANTIC_DOCS = 50;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void indexDocument(Long docId, String content) {
        if (docId == null || content == null || content.isBlank()) return;

        // 先删旧分块
        deleteChunks(docId);

        // 按段落切分
        String[] paragraphs = content.split("\n\n+");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;
            if (current.length() + trimmed.length() > MAX_CHUNK_SIZE && current.length() > 0) {
                chunks.add(current.toString());
                current = new StringBuilder();
            }
            if (current.length() > 0) current.append("\n\n");
            current.append(trimmed);
        }
        if (current.length() > 0) chunks.add(current.toString());
        if (chunks.isEmpty()) return;

        // 批量向量化：一次请求提交多块，避免逐块串行调用导致大目录导入极慢。
        // 服务不可用时跳过向量化，分块仍然入库以保证关键词检索可用。
        List<List<Float>> vectors = embeddingService.isAvailable()
                ? embeddingService.embedBatch(chunks)
                : List.of();

        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            List<Float> vec = i < vectors.size() ? vectors.get(i) : null;
            String embeddingStr = vec != null && !vec.isEmpty()
                    ? vec.stream().map(String::valueOf).collect(Collectors.joining(","))
                    : null;

            DocChunk chunk = new DocChunk();
            chunk.setDocId(docId);
            chunk.setChunkIndex(i);
            chunk.setContent(chunkText);
            chunk.setCharCount(chunkText.length());
            chunk.setEmbedding(embeddingStr);
            docChunkMapper.insert(chunk);
        }
        log.info("文档分块完成：docId={}, chunks={}, 向量化={}", docId, chunks.size(),
                embeddingService.isAvailable() ? "已启用" : "已跳过");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteChunks(Long docId) {
        docChunkMapper.delete(new LambdaQueryWrapper<DocChunk>()
                .eq(DocChunk::getDocId, docId));
    }

    @Override
    @Transactional
    public List<String> searchSimilar(String query, int limit, Long userId) {
        return searchSimilarTraced(query, limit, userId, step -> { }).chunks;
    }

    @Override
    @Transactional(readOnly = true)
    public SearchOutcome searchSimilarTraced(String query, int limit, Long userId,
                                             java.util.function.Consumer<com.knowflow.ai.trace.RagTraceStep> onStep) {
        long overallStart = System.currentTimeMillis();
        com.knowflow.ai.trace.RagTrace trace = new com.knowflow.ai.trace.RagTrace();
        trace.setTraceId("rag-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        if (query == null || query.isBlank() || userId == null) {
            trace.setStrategy("none");
            trace.setDurationMs(System.currentTimeMillis() - overallStart);
            return new SearchOutcome(List.of(), trace);
        }

        long permissionStart = System.currentTimeMillis();
        List<Long> accessibleDocIds = docDocumentMapper.selectList(
                        access.scope(new LambdaQueryWrapper<DocDocument>(), userId).eq(DocDocument::getStatus, 1))
                .stream()
                .map(DocDocument::getId)
                .filter(Objects::nonNull)
                .toList();
        trace.setAccessibleDocCount(accessibleDocIds.size());
        emitStep(trace, onStep, step("permission_filter", "权限过滤",
                accessibleDocIds.isEmpty() ? "empty" : "success",
                "按当前用户可见范围过滤知识库文档", permissionStart, accessibleDocIds.size()));
        if (accessibleDocIds.isEmpty()) {
            trace.setStrategy("permission_denied");
            trace.setEvidenceSufficient(false);
            trace.setDurationMs(System.currentTimeMillis() - overallStart);
            return new SearchOutcome(List.of(), trace);
        }

        long searchStart = System.currentTimeMillis();
        List<Float> queryVec = embeddingService.embed(query);
        List<String> results;
        if (queryVec == null || queryVec.isEmpty()) {
            // 向量检索真实不可用 → 关键词降级（DocChunkService 现有行为）
            log.info("query embedding 不可用，降级为分块关键词检索");
            results = searchByKeyword(query, limit, accessibleDocIds);
            trace.setStrategy("keyword");
            emitStep(trace, onStep, step("keyword_search", "关键词检索",
                    results.isEmpty() ? "empty" : "success",
                    "向量检索不可用，按关键词匹配已授权文档片段", searchStart, results.size()));
        } else {
            List<DocChunk> allChunks = docChunkMapper.selectList(
                    new LambdaQueryWrapper<DocChunk>()
                            .isNotNull(DocChunk::getEmbedding)
                            .ne(DocChunk::getEmbedding, "")
                            .in(DocChunk::getDocId, accessibleDocIds)
                            .orderByAsc(DocChunk::getDocId)
                            .orderByAsc(DocChunk::getChunkIndex));
            if (allChunks.isEmpty()) {
                results = List.of();
            } else {
                int size = Math.min(Math.max(limit, 1), 20);
                results = allChunks.parallelStream()
                        .map(chunk -> {
                            List<Float> chunkVec = parseVector(chunk.getEmbedding());
                            if (chunkVec == null) return null;
                            double sim = embeddingService.cosineSimilarity(queryVec, chunkVec);
                            return new Object[]{chunk, sim};
                        })
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparingDouble(o -> - (Double) o[1]))
                        .limit(size)
                        .map(o -> ((DocChunk) o[0]).getContent())
                        .collect(Collectors.toList());
            }
            trace.setStrategy("vector");
            emitStep(trace, onStep, step("vector_search", "向量检索",
                    results.isEmpty() ? "empty" : "success",
                    "按余弦相似度召回已授权文档片段", searchStart, results.size()));
        }

        trace.setResultCount(results.size());
        trace.setEvidenceSufficient(!results.isEmpty());
        long evidenceStart = System.currentTimeMillis();
        emitStep(trace, onStep, step("evidence", "证据判断",
                results.isEmpty() ? "empty" : "success",
                results.isEmpty() ? "未检索到可用资料，回答不会引用知识库内容"
                        : "召回 " + results.size() + " 个片段，可作为回答引用上下文",
                evidenceStart, results.size()));
        trace.setDurationMs(System.currentTimeMillis() - overallStart);
        return new SearchOutcome(results, trace);
    }

    private static com.knowflow.ai.trace.RagTraceStep step(String type, String label, String status,
                                                           String detail, long startedAt, int resultCount) {
        com.knowflow.ai.trace.RagTraceStep step = new com.knowflow.ai.trace.RagTraceStep();
        step.setStepId(type);
        step.setType(type);
        step.setLabel(label);
        step.setStatus(status);
        step.setDetail(detail);
        step.setDurationMs(Math.max(0L, System.currentTimeMillis() - startedAt));
        step.setResultCount(resultCount);
        return step;
    }

    /** 记录并推送单个检索步骤：先写入 trace（供落库历史回放），推送失败不影响检索本身。 */
    private static void emitStep(com.knowflow.ai.trace.RagTrace trace,
                                 java.util.function.Consumer<com.knowflow.ai.trace.RagTraceStep> onStep,
                                 com.knowflow.ai.trace.RagTraceStep step) {
        if (trace != null) {
            trace.addStep(step);
        }
        if (onStep != null) {
            try {
                onStep.accept(step);
            } catch (Exception ignored) {
                // 推送失败（客户端断开）不影响检索本身
            }
        }
    }

    private Long resolveRootCategoryId(DocCategory category, Map<Long, DocCategory> categories) {
        DocCategory current = category;
        Set<Long> visited = new java.util.HashSet<>();
        while (current.getParentId() != null && current.getParentId() != 0
                && visited.add(current.getId())) {
            DocCategory parent = categories.get(current.getParentId());
            if (parent == null) break;
            current = parent;
        }
        return current.getId();
    }

    private List<String> searchByKeyword(String query, int limit, List<Long> accessibleDocIds) {
        int size = Math.min(Math.max(limit, 1), 20);
        return docChunkMapper.selectList(new LambdaQueryWrapper<DocChunk>()
                        .like(DocChunk::getContent, query.trim())
                        .in(DocChunk::getDocId, accessibleDocIds)
                        .orderByAsc(DocChunk::getDocId)
                        .orderByAsc(DocChunk::getChunkIndex)
                        .last("LIMIT " + size))
                .stream()
                .map(DocChunk::getContent)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public Map<Long, Double> searchSimilarDocIds(String query, int limit) {
        List<Long> accessible=docDocumentMapper.selectList(access.scope(new LambdaQueryWrapper<DocDocument>(),access.actor()).eq(DocDocument::getStatus,1)).stream().map(DocDocument::getId).toList();
        if(accessible.isEmpty()) return Collections.emptyMap();
        if (query == null || query.isBlank() || !embeddingService.isAvailable()) {
            return Collections.emptyMap();
        }
        List<Float> queryVec = embeddingService.embed(query);
        if (queryVec == null || queryVec.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DocChunk> allChunks = docChunkMapper.selectList(
                new LambdaQueryWrapper<DocChunk>()
                        .in(DocChunk::getDocId,accessible)
                        .isNotNull(DocChunk::getEmbedding)
                        .ne(DocChunk::getEmbedding, ""));
        if (allChunks.isEmpty()) {
            return Collections.emptyMap();
        }

        // 同一文档取分块最高分，避免长文档凭分块数量刷屏
        Map<Long, Double> docScores = new HashMap<>();
        for (DocChunk chunk : allChunks) {
            List<Float> chunkVec = parseVector(chunk.getEmbedding());
            if (chunkVec == null) continue;
            double sim = embeddingService.cosineSimilarity(queryVec, chunkVec);
            if (sim < MIN_SIMILARITY) continue;
            docScores.merge(chunk.getDocId(), sim, Math::max);
        }

        int size = Math.min(Math.max(limit, 1), MAX_SEMANTIC_DOCS);
        return docScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(size)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    /** 解析逗号分隔的浮点数字符串为 List<Float> */
    private List<Float> parseVector(String embedding) {
        if (embedding == null || embedding.isBlank()) return null;
        try {
            String[] parts = embedding.split(",");
            List<Float> result = new ArrayList<>(parts.length);
            for (String p : parts) {
                result.add(Float.parseFloat(p.trim()));
            }
            return result;
        } catch (Exception e) {
            log.warn("解析 embedding 向量失败: {}", e.getMessage());
            return null;
        }
    }
}
