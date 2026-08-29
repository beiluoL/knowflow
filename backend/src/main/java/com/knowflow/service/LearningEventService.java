package com.knowflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowflow.common.LearningEventType;
import com.knowflow.entity.LearningEvent;

import java.util.Map;

/**
 * 学习行为事件服务（Learning Event System，Phase 1）。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>只做“追加写入”，不替换任何原有业务记录；</li>
 *   <li>{@code record} 使用独立事务（REQUIRES_NEW），即便主业务流程回滚，事件也会落库；</li>
 *   <li>事件写入失败仅告警、绝不向外抛出，保证不影响任何已有业务；</li>
 *   <li>userId 为空时（匿名）自动跳过，不写事件。</li>
 * </ul>
 */
public interface LearningEventService {

    /**
     * 记录一条学习行为事件。
     *
     * @param userId       用户ID（可为 null，内部回退到当前登录用户；仍为 null 则跳过）
     * @param eventType    事件类型（见 LearningEventType）
     * @param resourceType 资源类型（DOC/CHAPTER/QUIZ/CODE/...）
     * @param resourceId   关联资源ID（可为 null）
     * @param metadata     扩展信息（可为 null，序列化为 JSON 存入 metadata 列）
     */
    void record(Long userId, String eventType, String resourceType, Long resourceId, Map<String, Object> metadata);

    void record(Long userId, LearningEventType eventType, String resourceType, Long resourceId, Map<String, Object> metadata);

    void record(Long userId, String eventType, String resourceType, Long resourceId);

    /** 分页查询指定用户的学习事件，供掌握度引擎 / AI 教练 / 看板消费。 */
    IPage<LearningEvent> pageEvents(Long userId, String eventType, int current, int size);
}
