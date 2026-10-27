package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 技术英语公共词条。
 * 同一 headword 可按 domain + senseKey 保留多个义项（如并发死锁与数据库死锁）。
 * 公共内容与用户收藏/学习状态分表隔离。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tech_vocab_term")
public class TechVocabTerm extends BaseEntity {

    /** 英文主词、短语或缩写 */
    private String headword;

    /** 搜索归一化键：小写 + 空白折叠，仅用于检索/去重辅助，不判定含义 */
    private String normalizedKey;

    /** WORD / ACRONYM / PHRASE */
    private String termType;

    /** 同形异义义项键 */
    private String senseKey;

    /** 技术领域编码 */
    private String domain;

    /** 缩写全称 */
    private String fullForm;

    /** 对应 Java 类名 */
    private String className;

    /** 词性（单词适用） */
    private String partOfSpeech;

    /** 美式音标；无可靠来源时为空 */
    private String ipaUs;

    /** 英式音标；无可靠来源时为空 */
    private String ipaUk;

    /** 中文短释义 */
    private String meaningCn;

    /** 中文技术概念解释 */
    private String definitionCn;

    /** 英文专业解释 */
    private String definitionEn;

    /** 全称逐词拆解 JSON */
    private String wordBreakdown;

    /** 拆解说明：完整概念不是各单词含义的机械相加 */
    private String breakdownNote;

    /** 面试英文表达：一句可直接用于技术面试的回答 */
    private String interviewEn;

    /** 技术英文例句 */
    private String exampleEn;

    /** 技术英文例句中文解释 */
    private String exampleCn;

    /** 别名，逗号分隔，仅用于搜索 */
    private String aliases;

    /** DRAFT / REVIEWED / PUBLISHED / ARCHIVED */
    private String contentStatus;

    /** 定义、全称与读法来源 */
    private String referenceSource;

    /** 内容/音频许可说明 */
    private String licenseNote;

    /** 最近审核时间 */
    private LocalDateTime reviewedAt;
}
