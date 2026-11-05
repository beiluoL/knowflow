package com.knowflow.knowledge.vo;

import lombok.Data;

/**
 * 歧义点：标记为用户输入中与代码结构/语义/领域约定冲突或缺失的部分，前端高亮展示。
 */
@Data
public class AmbiguityVO {
    /** 问题分类：missing-file（缺文件）/ framework-mismatch（框架不匹配）/ lang-mismatch（语言不匹配）/ underspecified（描述不足）/ semantical（语义问题）。 */
    private String kind;
    /** 歧义点原文或引用 */
    private String point;
    /** 为什么认为是歧义 */
    private String reason;
    /** 结合领域知识库给出的可解释建议 */
    private String suggestion;
}
