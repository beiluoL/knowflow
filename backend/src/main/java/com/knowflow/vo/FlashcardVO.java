package com.knowflow.vo;

import lombok.Data;

/**
 * 闪卡视图对象，封装前后内容与所属路径、章节及难度信息。
 */
@Data
public class FlashcardVO {

    private Long id;

    private Long pathId;

    private Long chapterId;

    private String front;

    private String back;

    private String category;

    /** 难度等级编码（具体以枚举为准） */
    private Integer difficulty;

    /** 已复习次数 */
    private Integer reviewCount;
}
