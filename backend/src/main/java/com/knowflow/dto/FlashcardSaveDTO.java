package com.knowflow.dto;

import lombok.Data;

/**
 * 用户闪卡创建/更新请求。
 * 字段说明：path/chapter/category/doc 都是可选；front/back 必填。
 */
@Data
public class FlashcardSaveDTO {

    private Long pathId;

    private Long chapterId;

    /** 知识库/分类 ID */
    private Long categoryId;

    /** 来源文档 ID */
    private Long docId;

    /** 正面（问题/概念），必填 */
    private String front;

    /** 背面（答案/解释），必填 */
    private String back;

    /** 自定义分类标签 */
    private String category;

    /** 难度 1简单 / 2中等 / 3困难 */
    private Integer difficulty;

    /** 逗号分隔的自定义标签 */
    private String tags;
}
