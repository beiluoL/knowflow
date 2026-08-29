package com.knowflow.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 资源 → 知识点映射回填服务（Knowledge Mastery Engine，Phase 2-B）。
 * <p>
 * 一次性 / 可重复地将存量学习资源链接到知识点（幂等、不修改任何原业务表），
 * 并触发当前用户掌握度重算。架构见 docs/开发计划/Phase2_知识掌握度引擎设计.md §10。
 * </p>
 */
@Slf4j
@Service
public class ResourceKnowledgeBackfillService {

    private final ResourceKnowledgeService resourceKnowledgeService;
    private final KnowledgeMasteryService knowledgeMasteryService;

    public ResourceKnowledgeBackfillService(ResourceKnowledgeService resourceKnowledgeService,
                                            KnowledgeMasteryService knowledgeMasteryService) {
        this.resourceKnowledgeService = resourceKnowledgeService;
        this.knowledgeMasteryService = knowledgeMasteryService;
    }

    /** 全量构建所有学习资源的映射（不重算掌握度）。 */
    public void backfillAll() {
        resourceKnowledgeService.buildAllMappings();
    }

    /** 构建映射并重算指定用户的全部相关知识点。 */
    public void backfillUser(Long userId) {
        resourceKnowledgeService.buildAllMappings();
        knowledgeMasteryService.recalculateUser(userId);
    }
}
