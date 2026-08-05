package com.knowflow.dto;

import lombok.Data;

/**
 * 收集箱（知识输入）新增/编辑入参。
 */
@Data
public class WbCaptureDTO {

    /** 标题/一句话摘要 */
    private String title;

    /** 正文内容（Markdown） */
    private String content;

    /** 来源：MANUAL/DOC/WEB/AI/IMPORT */
    private String sourceType;

    /** 来源链接 */
    private String sourceUrl;

    /** 来源文档ID */
    private Long docId;

    /** 归属知识库/分类ID */
    private Long categoryId;

    /** 逗号分隔标签 */
    private String tags;

    /** 标星：1 是 / 0 否 */
    private Integer starred;
}
