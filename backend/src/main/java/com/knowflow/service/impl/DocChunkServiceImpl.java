package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.entity.DocChunk;
import com.knowflow.mapper.DocChunkMapper;
import com.knowflow.service.DocChunkService;
import com.knowflow.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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

    /** 单块最大字符数 */
    private static final int MAX_CHUNK_SIZE = 500;

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

        // 逐块生成 embedding 并入库
        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            List<Float> vec = embeddingService.embed(chunkText);
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
        log.info("文档分块完成：docId={}, chunks={}", docId, chunks.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteChunks(Long docId) {
        docChunkMapper.delete(new LambdaQueryWrapper<DocChunk>()
                .eq(DocChunk::getDocId, docId));
    }

    @Override
    public List<String> searchSimilar(String query, int limit) {
        if (query == null || query.isBlank()) return List.of();

        // 1. 嵌入查询文本
        List<Float> queryVec = embeddingService.embed(query);
        if (queryVec == null || queryVec.isEmpty()) {
            log.warn("query embedding 为空，无法进行向量检索");
            return List.of();
        }

        // 2. 加载所有有 embedding 的分块
        List<DocChunk> allChunks = docChunkMapper.selectList(
                new LambdaQueryWrapper<DocChunk>()
                        .isNotNull(DocChunk::getEmbedding)
                        .ne(DocChunk::getEmbedding, "")
                        .orderByAsc(DocChunk::getDocId)
                        .orderByAsc(DocChunk::getChunkIndex));

        if (allChunks.isEmpty()) return List.of();

        // 3. 逐块计算余弦相似度
        int size = Math.min(Math.max(limit, 1), 20);
        return allChunks.parallelStream()
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
