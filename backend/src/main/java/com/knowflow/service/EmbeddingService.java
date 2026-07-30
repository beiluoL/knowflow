package com.knowflow.service;

import java.util.List;

/**
 * Embedding 向量服务：将文本转换为向量表示并支持余弦相似度检索。
 */
public interface EmbeddingService {

    /**
     * 将单段文本转换为向量。
     *
     * @param text 输入文本
     * @return 浮点数向量
     */
    List<Float> embed(String text);

    /**
     * 计算两个向量的余弦相似度。
     */
    double cosineSimilarity(List<Float> a, List<Float> b);
}
