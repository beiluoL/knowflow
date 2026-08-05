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
     * 批量向量化：一次请求提交多段文本，显著降低导入大目录时的网络往返开销。
     * 返回列表与入参一一对应；单条失败或服务不可用时，对应位置返回空列表而非中断整批。
     *
     * @param texts 输入文本列表
     * @return 与入参等长的向量列表
     */
    List<List<Float>> embedBatch(List<String> texts);

    /**
     * embedding 服务是否可用（已配置有效 API Key）。
     * 供调用方在批量索引前快速短路，避免逐条无效请求。
     */
    boolean isAvailable();

    /**
     * 计算两个向量的余弦相似度。
     */
    double cosineSimilarity(List<Float> a, List<Float> b);
}
