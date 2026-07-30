package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 学习路径下的章节实体，承载具体内容并关联文档与闪卡。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_chapter")
public class LearningChapter extends BaseEntity {

    /** 归属学习路径 ID（逻辑外键 learning_path.id） */
    private Long pathId;

    /** 章节标题 */
    private String title;

    /** 章节正文内容 */
    private String content;

    /** 排序序号（同一路径内升序排列） */
    private Integer sortOrder;

    /** 预计学习时长（分钟） */
    private Integer duration;

    /** 关联文档 ID 列表，逗号分隔。 */
    private String docIds;

    /** 关联闪卡 ID 列表，逗号分隔。 */
    private String flashcardIds;

    /** 前置章节 ID 列表（逗号分隔），全部完成后该章节才可学习。 */
    private String prerequisiteChapterIds;
}
