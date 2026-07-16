package com.zhishiku.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_chapter")
public class LearningChapter extends BaseEntity {

    private Long pathId;

    private String title;

    private String content;

    private Integer sortOrder;

    private Integer duration;

    private String docIds;

    private String flashcardIds;
}
