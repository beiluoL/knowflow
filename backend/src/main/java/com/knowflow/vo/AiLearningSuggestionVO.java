package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 学习建议 VO。
 */
@Data
public class AiLearningSuggestionVO {
    private Long id;
    private String period;
    /** 学习建议列表 */
    private List<SuggestionItem> suggestions;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 单条学习建议：标题、描述、图标、跳转路径 */
    @Data
    public static class SuggestionItem {
        private String title;
        private String desc;
        private String icon;
        private String path;
    }
}
