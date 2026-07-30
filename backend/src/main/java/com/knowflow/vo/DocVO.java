package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档列表视图对象，返回文档概要、分类与统计信息。
 */
@Data
public class DocVO {

    private Long id;

    private String title;

    private String summary;

    private String cover;

    /** 原始文件名（上传型文档有值，含扩展名）。 */
    private String fileName;

    /** 原文件访问地址（上传型文档有值，纯文本创建型为 null）。列表页用于前端判断文档类型。 */
    private String fileUrl;

    /** 原始文件字节大小（上传型文档有值）。 */
    private Long fileSize;

    private Long categoryId;

    private String categoryName;

    private String tags;

    private Integer viewCount;

    private Integer readCount;

    private Integer favoriteCount;

    private Integer wordCount;

    /** 难度等级编码（具体以枚举为准） */
    private Integer difficulty;

    /** 文档状态编码（具体以枚举为准） */
    private Integer status;

    private LocalDateTime createTime;
}
