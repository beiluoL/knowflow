package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("doc_document")
/** 文档实体，知识库核心内容，承载正文、摘要与分类等信息。 */
public class DocDocument extends BaseEntity {

    private String title;

    private String content;

    private String summary;

    private String cover;

    private Long categoryId;

    /** 分类层级路径，逗号分隔的分类 ID 链。 */
    private String categoryPath;

    private String tags;

    private Integer viewCount;

    private Integer readCount;

    private Integer favoriteCount;

    private Integer wordCount;

    /** 难度等级，数值越大越难。 */
    private Integer difficulty;

    private Integer sortOrder;

    /** 文档状态，如 0 草稿 / 1 已发布。 */
    private Integer status;
}
