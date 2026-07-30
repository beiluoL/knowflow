package com.knowflow.dto;

import lombok.Data;

/**
 * AI 生成闪卡请求：
 * - 可基于单篇文档（docId）
 * - 或基于整个知识库（categoryId），会聚合知识库下所有文档正文
 */
@Data
public class FlashcardGenerateDTO {

    /** 知识库/分类 ID（与 docId 二选一，优先 docId） */
    private Long categoryId;

    /** 文档 ID（与 categoryId 二选一） */
    private Long docId;

    /** 生成张数（默认 10，最大 30） */
    private Integer count;

    /** 生成难度偏好 1/2/3，0 表示混合（默认 0） */
    private Integer difficultyPreference;
}
