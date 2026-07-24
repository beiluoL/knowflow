package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

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

    private Integer difficulty;

    private Integer status;

    private Boolean favorite;

    private java.math.BigDecimal readProgress;

    private LocalDateTime createTime;
}
