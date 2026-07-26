package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_path")
/** 学习路径实体，聚合多个章节形成体系化课程，供用户订阅学习。 */
public class LearningPath extends BaseEntity {

    private String title;

    private String description;

    private String cover;

    /** 难度等级标识，如入门/进阶/高级。 */
    private String level;

    private Integer chapterCount;

    private Integer totalDuration;

    private Integer enrolledCount;

    private Integer sortOrder;

    /** 状态位，如 0 下架 / 1 已发布。 */
    private Integer status;
}
