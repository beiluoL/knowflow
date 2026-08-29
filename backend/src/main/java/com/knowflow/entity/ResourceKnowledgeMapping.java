package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 资源 → 知识点映射（Knowledge Mastery Engine，Phase 2-B）。
 * <p>
 * 将学习资源（题目 / 代码 / 错题 / 复习卡 / 回忆会话 / 闪卡 / 文档）与知识点（kg_entity）关联。
 * 映射来源分三级：
 * <ul>
 *   <li>显式：MANUAL / AI / IMPORT，status=ACCEPTED 直接使用，绝不扩散到同分类其他知识点；</li>
 *   <li>AUTO：基于标题/内容/标签/分类的关键词最佳努力匹配，confidence 达标即 ACCEPTED；</li>
 *   <li>CATEGORY_FALLBACK：仅 category + 共享词元的极低置信兜底，默认 status=PENDING，不进入 Mastery。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resource_knowledge_mapping")
public class ResourceKnowledgeMapping extends BaseEntity {

    /** 资源类型：QUIZ / CODE_QUESTION / MISTAKE / REVIEW_CARD / RECALL_SESSION / FLASHCARD / DOC */
    private String resourceType;

    /** 关联资源ID（逻辑外键，按 resource_type 指向对应业务表） */
    private Long resourceId;

    /** 知识点ID（逻辑外键 kg_entity.id，仅可学习类型） */
    private Long knowledgeId;

    /** 映射来源：MANUAL / AI / IMPORT / AUTO / CATEGORY_FALLBACK */
    private String source;

    /** 置信度 0~1 */
    private BigDecimal confidence;

    /** 映射状态：ACCEPTED（进入 Mastery）/ PENDING（待确认）/ REJECTED */
    private String status;
}
