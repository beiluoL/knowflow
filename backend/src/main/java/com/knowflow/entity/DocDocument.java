package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("doc_document")
public class DocDocument extends BaseEntity {

    private String title;

    private String content;

    private String summary;

    private String cover;

    private Long categoryId;

    private String categoryPath;

    private String tags;

    private Integer viewCount;

    private Integer readCount;

    private Integer favoriteCount;

    private Integer wordCount;

    private Integer difficulty;

    private Integer sortOrder;

    private Integer status;
}
