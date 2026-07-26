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
