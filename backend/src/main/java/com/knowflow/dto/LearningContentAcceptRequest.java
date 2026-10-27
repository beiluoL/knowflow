package com.knowflow.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 学习内容接受请求（Architecture-4.4）。
 *
 * <p>用户在 Draft 审核页确认/编辑后回传；服务端做确定性校验 + 去重后再写入既有正式实体
 * （learning_flashcard / learning_scenario），绝不旁路新建「xxx2」表或模型。
 * 费曼 Draft 无持久化实体（现有费曼为事件式表达），由前端预填跳转表达，不经过本端点。</p>
 *
 * <p>场景 keywords 由服务端在生成时持有（draftToken 一次性换取），本请求只携带用户可见
 * 字段的编辑值，keywords 全程不下发前端（延续 Scenario 安全设计）。</p>
 */
@Data
public class LearningContentAcceptRequest {

    /** 知识单元 ID（kg_entity.id），必填 */
    private Long knowledgeId;

    /** 学习节点 ID（learning_chapter.id），可选：接受闪卡时挂到该章节 */
    private Long learningNodeId;

    /** 接受的闪卡列表（可空）：每张都必须通过必填/长度/批内去重/与用户既有卡去重 */
    private List<FlashcardItem> flashcards = new ArrayList<>();

    /** 接受的场景 Draft（可空）：凭 draftToken 换服务端缓存的 keywords */
    private ScenarioItem scenario;

    @Data
    public static class FlashcardItem {
        /** 正面问题（必填，≤200 字符） */
        private String front;
        /** 背面答案（必填，≤2000 字符） */
        private String back;
        /** 难度 1~3（空则默认 2） */
        private Integer difficulty;
    }

    @Data
    public static class ScenarioItem {
        /** 生成时返回的一次性令牌（必填） */
        private String draftToken;
        /** 用户可编辑的标题（空则沿用生成值） */
        private String title;
        /** 用户可编辑的情景背景（空则沿用生成值） */
        private String contextText;
        /** 用户可编辑的问题（空则沿用生成值） */
        private String questionText;
        /** 用户可编辑的难度 1~3（空则沿用生成值） */
        private Integer difficulty;
    }
}
