package com.zhishiku.vo;

import lombok.Data;

@Data
public class FlashcardVO {

    private Long id;

    private Long pathId;

    private Long chapterId;

    private String front;

    private String back;

    private String category;

    private Integer difficulty;

    private Integer reviewCount;
}
