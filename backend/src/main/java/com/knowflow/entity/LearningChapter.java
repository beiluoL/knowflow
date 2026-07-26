package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_chapter")
/** 学习路径下的章节实体，承载具体内容并关联文档与闪卡。 */
public class LearningChapter extends BaseEntity {

    private Long pathId;

    private String title;

    private String content;

    private Integer sortOrder;

    private Integer duration;

    /** 关联文档 ID 列表，逗号分隔。 */
    private String docIds;

    /** 关联闪卡 ID 列表，逗号分隔。 */
    private String flashcardIds;
}
