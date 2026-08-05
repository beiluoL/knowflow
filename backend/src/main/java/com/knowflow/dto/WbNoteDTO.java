package com.knowflow.dto;

import lombok.Data;

/**
 * 康奈尔笔记（知识整理）新增/编辑入参。
 */
@Data
public class WbNoteDTO {

    /** 来源收集箱条目ID */
    private Long captureId;

    /** 归属知识库/分类ID */
    private Long categoryId;

    /** 笔记标题 */
    private String title;

    /** 康奈尔-线索栏 */
    private String cueColumn;

    /** 康奈尔-笔记栏 */
    private String noteColumn;

    /** 康奈尔-总结栏 */
    private String summaryColumn;

    /** 逗号分隔标签 */
    private String tags;

    /** 掌握度自评：0~100 */
    private Integer mastery;
}
