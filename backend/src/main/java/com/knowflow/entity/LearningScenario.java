package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 场景练习实体（Architecture-4.0-4 Scenario Lab）。
 * <p>
 * 内容表（全用户共享，不做 userId 隔离）：一个真实业务场景 + 一个开放式问题 +
 * 覆盖清单 key_points（JSON：[{{@code label, keywords[]}}]）。
 * 关键词仅服务端用于提交后的确定性覆盖评分，不下发给前端（避免提前泄露答题口径）。
 * 用户作答记录沉淀为 learning_event（SCENARIO_COMPLETED），掌握度联动留待后续引擎接入。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_scenario")
public class LearningScenario extends BaseEntity {

    /** 场景标题，如「线上 Full GC 频繁排查」 */
    private String title;

    /** 情景背景描述（Markdown 纯文本） */
    private String contextText;

    /** 要用户回答的开放式问题 */
    private String questionText;

    /** 要点清单 JSON：[{label, keywords[]}] */
    private String keyPoints;

    /** 难度 1-3 */
    private Integer difficulty;

    /** 关联知识点 ID（逻辑外键 kg_entity.id，可空） */
    private Long knowledgeId;

    /** 1 上架 / 0 下架 */
    private Integer status;
}
