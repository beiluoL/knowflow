package com.knowflow.vo;

import lombok.Data;

/**
 * Phase 3H：题目讲解结果。
 *
 * <p>mode=AI：content 为模型生成的个性化讲解（前端需标注 AI）；
 * mode=BASIC：AI 不可用/关闭，content 回退为题库已有解析；题库也没有解析时 hasExplanation=false，
 * 前端必须明确展示“暂无解析”，禁止编造。
 */
@Data
public class QuizExplainVO {

    private Long questionId;

    /** AI / BASIC。 */
    private String mode;

    /** BASIC 时的失败分类（AI_UNCONFIGURED / AI_TIMEOUT …），可空。 */
    private String failureKind;

    /** 讲解正文：AI 生成文本，或已有标准解析（可能为 null）。 */
    private String content;

    /** 标准答案（简答题为参考答案）。 */
    private String answer;

    /** 题库是否存在标准解析；false 时前端显式展示“暂无解析”。 */
    private boolean hasExplanation;

    /** 已有标准解析原文（BASIC 模式即 content；AI 模式也附带以便对照）。 */
    private String explanation;

    private String topicCode;

    private String questionType;
}
