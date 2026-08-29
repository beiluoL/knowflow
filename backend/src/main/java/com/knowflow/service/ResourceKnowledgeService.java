package com.knowflow.service;

import java.util.List;

/**
 * 资源 → 知识点映射服务（Knowledge Mastery Engine，Phase 2-B）。
 * <p>
 * 职责：
 * <ul>
 *   <li>将学习资源（题目/代码/错题/复习卡/回忆会话/闪卡/文档）解析为已接受的知识点ID；</li>
 *   <li>最佳努力 AUTO 关键词匹配（不调 LLM，可解释），写入 resource_knowledge_mapping；</li>
 *   <li>全量构建映射（回填 / 重算前置），幂等、不修改任何原业务表。</li>
 * </ul>
 * 映射优先级：显式(MANUAL/AI/IMPORT, ACCEPTED) &gt; AUTO(ACCEPTED) &gt; 分类极低置信兜底(PENDING)。
 * </p>
 */
public interface ResourceKnowledgeService {

    /**
     * 解析资源对应的「已接受(ACCEPTED)」知识点ID列表（支持多对多）。
     * 无映射或仅 PENDING/REJECTED 时返回空列表，由调用方跳过 mastery 计算。
     *
     * @param resourceType 资源类型（见 MasteryConfig.RES_*）
     * @param resourceId   资源ID
     * @return 知识点ID（kg_entity.id）列表
     */
    List<Long> resolveKnowledgeIds(String resourceType, Long resourceId);

    /**
     * 为单个资源执行最佳努力 AUTO 匹配并写入映射（幂等）。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源ID
     */
    void buildAutoMappingForResource(String resourceType, Long resourceId);

    /**
     * 全量构建所有学习资源的映射（一次性 / 可重复），幂等。
     * 扫描 quiz/code/mistake/review/recall/flashcard/doc 全量行，复用同一 AUTO 匹配逻辑。
     */
    void buildAllMappings();
}
