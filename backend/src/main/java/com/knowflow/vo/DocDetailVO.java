package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档详情视图对象，聚合文档内容、分类与作者及阅读进度等信息。
 */
@Data
public class DocDetailVO {

    private Long id;

    private String title;

    private String content;

    private String summary;

    private String cover;

    /** 原始文件名（上传型文档有值，含扩展名）。 */
    private String fileName;

    /** 原始文件访问路径（/uploads/...），可空；用于原文下载/预览。 */
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

    /** 当前登录用户是否已收藏该文档 */
    private Boolean favorite;

    /** 当前用户阅读进度，0-100（百分比） */
    private java.math.BigDecimal readProgress;

    private LocalDateTime createTime;
}
