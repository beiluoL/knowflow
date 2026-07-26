package com.knowflow.vo;

import lombok.Data;

/**
 * 学习章节视图对象，封装章节内容、关联文档卡片及完成状态。
 */
@Data
public class LearningChapterVO {

    private Long id;

    private Long pathId;

    private String title;

    private String content;

    private Integer sortOrder;

    /** 章节时长（分钟） */
    private Integer duration;

    /** 关联文档ID，逗号分隔 */
    private String docIds;

    /** 关联闪卡ID，逗号分隔 */
    private String flashcardIds;

    /** 当前用户是否已完成该章节 */
    private Boolean completed;
}
