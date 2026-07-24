package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LearningPathVO {

    private Long id;

    private String title;

    private String description;

    private String cover;

    private String level;

    private Integer chapterCount;

    private Integer totalDuration;

    private Integer enrolledCount;

    private Integer sortOrder;

    private Integer status;

    private LocalDateTime createTime;
}
