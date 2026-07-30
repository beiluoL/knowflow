package com.knowflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 个性化学习路径推荐视图对象。
 * 由 AI 根据用户学习历史、当前水平和目标生成。
 */
@Data
public class PersonalizedPathVO {

    /** 缓存记录ID（ai_personalized_path.id），用于「采用/删除/历史」操作，AI 首次生成后回填。 */
    private Long id;

    /** 推荐标题。 */
    private String title;

    /** 推荐理由。 */
    private String reason;

    /** 适合的难度级别。 */
    private String level;

    /** 预计总时长（分钟）。 */
    private Integer totalDuration;

    /** 每日建议学习时长（分钟）。 */
    private Integer dailyDuration;

    /** 学习目标列表。 */
    private List<String> goals;

    /** 推荐章节列表。 */
    private List<RecommendChapter> chapters;

    /** AI 生成的学习建议。 */
    private String advice;

    /** 推荐关联的已有路径 ID（可为空）。采用落地后指向真实 learning_path.id。 */
    private Long relatedPathId;

    /** 创建时间（用于历史列表展示，格式化字符串）。 */
    private String createTime;

    @Data
    public static class RecommendChapter {
        /** 章节标题。 */
        private String title;
        /** 章节内容摘要。 */
        private String content;
        /** 预计时长（分钟）。 */
        private Integer duration;
        /** 排序。 */
        private Integer sortOrder;
        /** 章节学习重点。 */
        private String focus;
    }
}
