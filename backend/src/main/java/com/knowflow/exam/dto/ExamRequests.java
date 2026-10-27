package com.knowflow.exam.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 考试系统请求 DTO 集合（按场景分组的嵌套静态类）。
 * 所有身份信息（userId）一律从认证上下文取，不信任请求体内的用户字段。
 */
public final class ExamRequests {

    private ExamRequests() {
    }

    /** 智能规则组卷请求（确定性、不调用 LLM）。 */
    @Data
    public static class ComposeRequest {
        private String name;
        private String domain;
        /** 知识点 key 列表（对应 quiz_question.tags 首标签）；空=不限 */
        private List<String> topics;
        /** RANDOM / FREQUENCY / BALANCED / GRADIENT / MISTAKE / WEAK / SCENARIO */
        private String strategy;
        /** Default true for compatibility; false selects never-answered canonical identities. */
        private Boolean includeAnswered;
        private Long seed;
        /** 题型 → 题量；为空时按题量平均抽取 */
        private Map<String, Integer> typeDistribution;
        /** 期望难度 1/2/3；空=混合 */
        private List<Integer> difficulties;
        private Integer questionCount;
        private Integer totalScore;
        private Integer durationMinutes;
        private Integer passScore;
        /** PRACTICE / EXAM */
        private String mode;
        private Integer shuffleQuestion;
        private Integer shuffleOption;
        private Integer allowPause;
        private Integer allowChange;
        /** 多选评分：ALL_OR_NOTHING / PARTIAL_CREDIT */
        private String multiPolicy;
    }

    /** 按模板生成试卷（可覆盖模式/时长/乱序）。 */
    @Data
    public static class TemplateInstantiateRequest {
        private Long seed;
        private String mode;
        private Integer durationMinutes;
        private Integer shuffleQuestion;
        private Integer shuffleOption;
        private String multiPolicy;
    }

    /** AI 智能出卷请求（个人临时卷，不进共享题库）。 */
    @Data
    public static class AiComposeRequest {
        /** Preserve real-history exclusion when AI falls back to the published bank. */
        private Boolean includeAnswered;
        private String name;
        private String topic;
        private Integer count;
        /** 期望题型分布，可选 */
        private Map<String, Integer> typeDistribution;
        private List<Integer> difficulties;
        private Integer totalScore;
        private Integer durationMinutes;
        private Integer passScore;
        private String mode;
        private Integer shuffleQuestion;
        private Integer shuffleOption;
        private String multiPolicy;
        private String model;
        /** 随机种子（规则组卷复现用）。 */
        private Long seed;
        /**
         * Phase 3H：AI 不可用或候选题质量不足时，是否自动降级为确定性题库组卷。
         * 默认 1（开启）；显式传 0 时维持旧行为（直接报错，不组卷）。
         */
        private Integer fallbackToBank;
    }

    /** 自动保存草稿。 */
    @Data
    public static class SaveDraftRequest {
        private Long expectedRevision;
        /** paperQuestionId -> 用户答案 */
        private Map<Long, String> answers;
        /** 标记待检查的题号（ordinal） */
        private List<Integer> marked;
    }

    /** 交卷请求。 */
    @Data
    public static class SubmitRequest {
        private Long expectedRevision;
        /** paperQuestionId -> 用户答案 */
        private Map<Long, String> answers;
        private List<Integer> marked;
        /** 每题 paperQuestionId -> 耗时秒（可选） */
        private Map<Long, Integer> timeCosts;
        /** 前端版本标识，仅作审计 */
        private String clientVersion;
    }
}
