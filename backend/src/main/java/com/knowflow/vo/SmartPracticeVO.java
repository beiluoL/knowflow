package com.knowflow.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Phase 3H：确定性智能练习推荐结果（规则引擎 {@code rule-engine-v1}，不依赖 AI）。
 *
 * <p>每道题带可解释的 reason（DUE_REVIEW/HIGH_FREQ_MISTAKE/WEAK_TOPIC/TARGET_TOPIC/
 * NEW_UNANSWERED/GENERAL_POOL），reasonCounts 给出构成；题库不足时 sufficient=false
 * 与 message 明确提示，items 仍包含已选出的题目，系统不重复凑数、不偷降难度。
 */
@Data
public class SmartPracticeVO {

    private String ruleVersion;
    private String bankVersion;
    private boolean sufficient;
    private String message;
    private Map<String, Integer> reasonCounts;
    private List<Item> items;

    @Data
    public static class Item {
        private Long id;
        private String title;
        private String content;
        private String questionType;
        private List<String> options;
        private String answer;
        private String explanation;
        private Integer difficulty;
        private String tags;
        private String topicCode;
        /** 选题原因（SelectionReason 枚举值）。 */
        private String reason;
    }
}
