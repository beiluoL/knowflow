package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学习行为事件（Learning Event System，Phase 1）。
 * <p>
 * 统一埋点所有学习行为，作为掌握度引擎 / AI 教练 / 学习计划的数据底座。
 * 仅追加记录，不替换任何原有业务表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_event")
public class LearningEvent extends BaseEntity {

    /** 所属用户ID（逻辑外键 sys_user.id） */
    private Long userId;

    /** 事件类型：见 LearningEventType */
    private String eventType;

    /** 资源类型：DOC / CHAPTER / QUIZ / CODE / REVIEW_CARD / RECALL / CHAT / KNOWLEDGE / PATH / CHECKIN */
    private String resourceType;

    /** 关联资源ID（可为空，如 CHECK_IN） */
    private Long resourceId;

    /** 事件扩展信息（JSON 字符串）：分数 / 耗时 / 章节 / 题目 / 连续天数等 */
    private String metadata;
}
