package com.knowflow.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学习路径视图对象，返回路径概况、章节数与 enrollment 等信息。
 */
@Data
public class LearningPathVO {

    private Long id;

    private String title;

    private String description;

    private String cover;

    /** 适合的难度级别描述 */
    private String level;

    private Integer chapterCount;

    /** 总时长（分钟） */
    private Integer totalDuration;

    private Integer enrolledCount;

    /** 当前登录用户是否已报名该路径（用于详情页报名按钮状态）。 */
    private Boolean enrolled;

    /** 当前登录用户的学习进度比例（0~1）；未报名时为 null。 */
    private BigDecimal progress;

    private Integer sortOrder;

    /** 路径状态编码（具体以枚举为准） */
    private Integer status;

    private LocalDateTime createTime;
}
