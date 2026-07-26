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
