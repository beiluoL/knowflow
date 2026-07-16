package com.zhishiku.vo;

import lombok.Data;

@Data
public class LearningChapterVO {

    private Long id;

    private Long pathId;

    private String title;

    private String content;

    private Integer sortOrder;

    private Integer duration;

    private String docIds;

    private String flashcardIds;

    private Boolean completed;
}
