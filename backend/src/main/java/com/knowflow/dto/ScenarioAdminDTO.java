package com.knowflow.dto;

import lombok.Data;

import java.util.List;

/**
 * 场景内容管理入参（后台 ADMIN，Architecture-4.0-6）。
 * <p>
 * keyPoints 为结构化清单（label + keywords[]），服务端负责与 learning_scenario.key_points
 * JSON 互转；keywords 允许管理端查看/维护（服务端私有字段，仅管理后台可见）。
 */
@Data
public class ScenarioAdminDTO {

    /** 场景标题（必填） */
    private String title;

    /** 情景背景描述（可选） */
    private String contextText;

    /** 要用户回答的问题（必填） */
    private String questionText;

    /** 要点清单（必填，至少 1 条；label 必填 + keywords 至少 1 个） */
    private List<ScenarioKeyPoint> keyPoints;

    /** 难度 1-3，默认 1 */
    private Integer difficulty;

    /** 关联知识点 ID（逻辑外键 kg_entity.id，可空） */
    private Long knowledgeId;

    /** 1 上架 / 0 下架，默认 1 */
    private Integer status;
}
