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
}
