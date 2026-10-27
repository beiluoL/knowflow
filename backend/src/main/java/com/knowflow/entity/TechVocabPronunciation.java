package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 词条发音变体。
 * 单词/短语使用 WORD_US / WORD_UK；缩写单独保存 ABBR 与 FULL_FORM 读法，
 * readingText 必须经人工审核，不能由字母串自动推断读法。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tech_vocab_pronunciation")
public class TechVocabPronunciation extends BaseEntity {

    /** 所属词条 ID */
    private Long termId;

    /** WORD_US / WORD_UK / ABBR / FULL_FORM */
    private String variant;

    /** 经审核朗读文本 */
    private String readingText;

    /** en-US / en-GB 等 */
    private String locale;

    /** LETTER_BY_LETTER / SPOKEN_WORD / MIXED */
    private String readingMode;

    /** 已授权音频地址，可为空 */
    private String audioUrl;

    /** 音频提供方 */
    private String audioProvider;

    /** 音频许可说明 */
    private String licenseNote;

    /** VERIFIED / PENDING / UNAVAILABLE */
    private String verificationStatus;
}
