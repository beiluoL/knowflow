package com.knowflow.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 学习内容接受结果（Architecture-4.4）。
 *
 * <p>确定性落库（learning_flashcard / learning_scenario），全程零学习事件、零掌握度改动：
 * 只有用户真实学习行为（复习/作答/提交）才会产生 Evidence。</p>
 */
@Data
public class LearningContentAcceptResultVO {

    private Long knowledgeId;

    /** 实际新增闪卡数 */
    private Integer flashcardSaved = 0;

    /** 因与用户既有闪卡或批内重复而被跳过的数量（幂等保障） */
    private Integer flashcardDuplicates = 0;

    /** 新增闪卡 ID 列表 */
    private List<Long> flashcardIds = new ArrayList<>();

    /** 场景接受结果（仅当请求携带场景时） */
    private ScenarioAcceptResult scenario;

    private List<String> warnings = new ArrayList<>();

    @Data
    public static class ScenarioAcceptResult {
        /** 是否为新插入 */
        private Boolean saved = Boolean.FALSE;
        /** 是否命中既有相同场景（去重复用，非重复新增） */
        private Boolean existing = Boolean.FALSE;
        /** learning_scenario.id（新插入或既有） */
        private Long scenarioId;
        private String title;
    }
}
