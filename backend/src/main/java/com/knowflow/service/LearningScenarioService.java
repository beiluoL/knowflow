package com.knowflow.service;

import com.knowflow.dto.ScenarioAdminDTO;
import com.knowflow.dto.ScenarioSubmitDTO;
import com.knowflow.vo.ScenarioAdminVO;
import com.knowflow.vo.ScenarioDetailVO;
import com.knowflow.vo.ScenarioSubmitResultVO;
import com.knowflow.vo.ScenarioSummaryVO;

import java.util.List;

/**
 * 场景练习服务（Architecture-4.0-4 Scenario Lab，Practice 节点）。
 * <p>
 * 职责边界：内容只读（列表/详情）+ 确定性关键词覆盖评分 + SCENARIO_COMPLETED 事件落库。
 * 不接入 AI 语义评分、不改写掌握度（避免伪造联动）；内容维护走 SQL 种子/后台管理（后续）。
 */
public interface LearningScenarioService {

    /**
     * 上架场景列表（status=1），可按知识点与难度过滤；按难度升序、id 倒序。
     */
    List<ScenarioSummaryVO> listPublished(Long knowledgeId, Integer difficulty);

    /**
     * 场景详情（含要点名称清单；不含命中关键词）。不存在或已下架 → 404。
     */
    ScenarioDetailVO detail(Long id);

    /**
     * 提交作答：确定性要点关键词覆盖评分 → SCENARIO_COMPLETED 事件落库 → 反馈结果。
     */
    ScenarioSubmitResultVO submit(Long userId, Long scenarioId, ScenarioSubmitDTO dto);

    // ==================== 后台管理（ADMIN，Architecture-4.0-6） ====================

    /**
     * 管理端全部场景（含下架；逻辑删除不出现），可按关键词 / 状态过滤，按 id 倒序。
     */
    List<ScenarioAdminVO> adminList(String keyword, Integer status);

    /**
     * 管理端场景详情（含 keywords）。不存在或已删除 → 404。
     */
    ScenarioAdminVO adminDetail(Long id);

    /**
     * 管理端新建场景（keyPoints 结构化校验后序列化入库）。
     */
    ScenarioAdminVO adminCreate(ScenarioAdminDTO dto);

    /**
     * 管理端覆盖式更新场景（title/questionText 不可置空；状态/难度/要点均可改）。
     */
    ScenarioAdminVO adminUpdate(Long id, ScenarioAdminDTO dto);

    /**
     * 管理端删除场景（逻辑删除）。
     */
    void adminRemove(Long id);
}
