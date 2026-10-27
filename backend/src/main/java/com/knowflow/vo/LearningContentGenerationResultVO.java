package com.knowflow.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 学习内容生成结果（Architecture-4.4，瞬态 Draft，不落任何库）。
 *
 * <p>Knowledge（kg_entity）→ Draft：闪卡 Draft / 费曼 Draft / 场景 Draft 全部只是「AI 建议怎么学」，
 * 默认状态 DRAFT；用户逐条 接受/编辑/忽略 后才可能成为正式学习内容。字段口径：
 * <ul>
 *   <li>场景 Draft 只含 {@code pointLabels}（考察点名称），keywords 由服务端持有（draftToken 换取），不下发前端；</li>
 *   <li>闪卡按现有 LearningFlashcard 模型收敛为 front/back/difficulty（模型无 hint / knowledge_id 列，见报告 Limitations）；</li>
 *   <li>费曼 Draft 生成的是「题目/引导」而非用户的答案，用户须自己在费曼页完成表达。</li>
 * </ul>
 */
@Data
public class LearningContentGenerationResultVO {

    /** 来源知识单元 ID */
    private Long knowledgeId;

    /** 来源知识单元名称 */
    private String knowledgeName;

    /** Prompt 版本（可追踪、可演进） */
    private String promptVersion;

    private String provider;

    private String model;

    /** 生成耗时（毫秒） */
    private Long processingTimeMs;

    private Long generatedAt;

    private List<String> warnings = new ArrayList<>();

    /** 闪卡 Draft（默认 5 张，上限 10） */
    private List<FlashcardDraft> flashcards = new ArrayList<>();

    /** 费曼 Draft（一次一个表达主题） */
    private FeynmanDraft feynman;

    /** 场景 Draft（一次一个场景） */
    private ScenarioDraft scenario;

    @Data
    public static class FlashcardDraft {
        private String front;
        private String back;
        /** 1 简单 / 2 中等 / 3 困难 */
        private Integer difficulty;
    }

    @Data
    public static class FeynmanDraft {
        /** 表达主题（短主语，如「JVM GC」） */
        private String topic;
        /** 费曼练习的题目/引导（不是标准答案） */
        private String question;
        private List<String> hints = new ArrayList<>();
        /** 表达时建议避免直接堆砌的术语 */
        private List<String> avoidTerms = new ArrayList<>();
        private Integer difficulty;
    }

    @Data
    public static class ScenarioDraft {
        private String title;
        private String contextText;
        private String questionText;
        private Integer difficulty;
        /** 考察点名称（keywords 不下发） */
        private List<String> pointLabels = new ArrayList<>();
        /** 接受场景时回传的一次性令牌（换取服务端缓存的 keywords） */
        private String draftToken;
    }
}
