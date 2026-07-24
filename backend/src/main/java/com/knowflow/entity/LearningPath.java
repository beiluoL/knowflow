package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_path")
public class LearningPath extends BaseEntity {

    private String title;

    private String description;

    private String cover;

    private String level;

    private Integer chapterCount;

    private Integer totalDuration;

    private Integer enrolledCount;

    private Integer sortOrder;

    private Integer status;
}
