package com.knowflow.dto;

import lombok.Data;

/**
 * 费曼故事（知识输出）新增/编辑入参。
 */
@Data
public class WbStoryDTO {

    /** 来源收集箱条目ID */
    private Long captureId;

    /** 来源康奈尔笔记ID */
    private Long noteId;

    /** 归属知识库/分类ID */
    private Long categoryId;

    /** 故事标题 */
    private String title;

    /** 假想听众：CHILD/NEWBIE/PEER/INTERVIEWER */
    private String audience;

    /** 核心类比/隐喻 */
    private String metaphor;

    /** 故事正文（Markdown 叙事体） */
    private String content;

    /** 讲述卡点记录 */
    private String gapNote;

    /** 状态：DRAFT/DONE/PUBLISHED */
    private String status;

    /** 自评讲清程度：0~100 */
    private Integer clarityScore;
}
