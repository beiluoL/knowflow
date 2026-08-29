package com.knowflow.service;

import com.knowflow.entity.LearningEvent;
import com.knowflow.vo.KnowledgeMasteryDetailVO;
import com.knowflow.vo.KnowledgeMasteryVO;
import com.knowflow.vo.MasteryDiagnosticsVO;

import java.util.List;

/**
 * 知识点掌握度服务（Knowledge Mastery Engine，Phase 2-B）。
 * <p>
 * 职责：事件触发后的（user, knowledge）重算与落盘、查询、薄弱/需复习筛选、
 * 可解释详情、诊断与全量重算。算法在 {@code MasteryEngine}，聚合在 {@code ResourceKnowledgeService}。
 * </p>
 */
public interface KnowledgeMasteryService {

    /** 由 LearningEventServiceImpl.record() 在事件落库后触发（失败仅告警，不阻断业务）。 */
    void processEvent(LearningEvent event);

    /** 重算并落盘单个（user, knowledge）掌握度（独立事务，幂等整行重算）。 */
    void recalc(Long userId, Long knowledgeId);

    /** 构建映射后重算当前用户全部相关知识点。 */
    void recalculateUser(Long userId);

    /** 查询当前用户全部已掌握度知识点（按分类可在前端分组）。 */
    List<KnowledgeMasteryVO> listAll(Long userId);

    /** 查询薄弱知识点（status = WEAK）。 */
    List<KnowledgeMasteryVO> listWeak(Long userId);

    /** 查询需复习知识点（status = REVIEW_REQUIRED）。 */
    List<KnowledgeMasteryVO> listReviewRequired(Long userId);

    /** 知识点掌握度详情 + 可解释说明。 */
    KnowledgeMasteryDetailVO getDetail(Long userId, Long knowledgeId);

    /** 引擎可观察性诊断。 */
    MasteryDiagnosticsVO diagnostics(Long userId);
}
