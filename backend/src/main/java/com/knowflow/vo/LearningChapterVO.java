package com.knowflow.vo;

import lombok.Data;

/**
 * 学习章节视图对象，封装章节内容、关联文档卡片及完成状态。
 */
@Data
public class LearningChapterVO {

    /** 章节 ID */
    private Long id;

    /** 归属学习路径 ID */
    private Long pathId;

    /** 章节标题 */
    private String title;

    /** 章节正文内容 */
    private String content;

    /** 排序序号（同一路径内升序） */
    private Integer sortOrder;

    /** 章节时长（分钟） */
    private Integer duration;

    /** 关联文档ID，逗号分隔 */
    private String docIds;

    /** 关联闪卡ID，逗号分隔 */
    private String flashcardIds;

    /** 当前用户是否已完成该章节 */
    private Boolean completed;

    /** 当前用户是否可学习（前置章节全部完成） */
    private Boolean locked;

    /** 前置章节ID列表（逗号分隔） */
    private String prerequisiteChapterIds;
}
