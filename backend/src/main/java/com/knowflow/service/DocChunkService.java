package com.knowflow.service;

import java.util.List;

/**
 * 文档分块服务：文档切分、embedding 生成、向量相似度检索。
 */
public interface DocChunkService {

    /**
     * 为文档生成分块与 embedding。
     * 1. 将 content 按段落切块
     * 2. 调用 EmbeddingService 生成向量
     * 3. 存入 doc_chunk 表
     */
    void indexDocument(Long docId, String content);

    /**
     * 删除文档所有分块（文档内容更新时先删后建）。
     */
    void deleteChunks(Long docId);

    /**
     * 向量相似度检索：根据查询文本找到最相关的 K 个分块内容。
     *
     * @param query 用户问题
     * @param limit 返回条数
     * @return 分块内容列表（按相似度降序）
     */
    List<String> searchSimilar(String query, int limit);

    /**
     * 语义召回文档 id：按向量相似度返回文档 id 及其最高分块得分，
     * 供搜索页与关键词召回做混合排序（RRF 融合）。
     *
     * <p>同一文档的多个分块只保留最高分，避免长文档因分块多而占据结果集。
     * embedding 服务不可用时返回空 Map，调用方据此退化为纯关键词检索。
     *
     * @param query 查询文本
     * @param limit 返回文档数上限
     * @return docId -> 相似度得分，按得分降序的 LinkedHashMap
     */
    java.util.Map<Long, Double> searchSimilarDocIds(String query, int limit);
}
