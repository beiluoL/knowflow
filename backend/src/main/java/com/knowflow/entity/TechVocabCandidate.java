package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 技术英语个人候选词条（P2 AI / 规则提取结果）。
 *
 * 与公共词库 tech_vocab_term 严格分离：
 * - 候选只属于提交它的用户，默认 PENDING，永远不会被公共检索返回；
 * - 只有管理员审核通过后才在 tech_vocab_term 建立正式词条；
 * - AI 生成的释义与全称一律标记为待核验，不自动写成 VERIFIED。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tech_vocab_candidate")
public class TechVocabCandidate extends BaseEntity {

    /** 提交用户 */
    private Long userId;

    /** 候选英文主词 */
    private String headword;

    /** 归一化键：小写 + 空白折叠，用于个人候选去重 */
    private String normalizedKey;

    /** WORD / ACRONYM / PHRASE */
    private String termType;

    /** 建议领域编码 */
    private String domain;

    /** 建议英文全称（AI 结果需人工核验） */
    private String fullForm;

    /** 建议中文释义 */
    private String meaningCn;

    /** 建议中文解释 */
    private String definitionCn;

    /** 来源类型：DOCUMENT / CHAPTER / NOTE / TEXT */
    private String sourceType;

    /** 来源实体 ID，TEXT 来源为空 */
    private Long sourceId;

    /** 来源标题快照（来源失效后仍可辨认，但不会对外公开） */
    private String sourceTitle;

    /** 原文上下文片段，最高 500 字 */
    private String sourceContext;

    /** 置信度 0–100，仅作提示，不代表已核验 */
    private Integer confidence;

    /** 提取方式：RULE / AI */
    private String extractor;

    /** PENDING / APPROVED / REJECTED */
    private String reviewStatus;

    /** 与既有词库重复时记录的正式词条 ID */
    private Long duplicateTermId;

    /** 审核通过后生成的正式词条 ID */
    private Long termId;

    /** 审核备注（拒绝原因） */
    private String reviewNote;
}
