package com.knowflow.vo;

import com.knowflow.dto.ScenarioKeyPoint;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 场景管理 VO（后台 ADMIN 使用，含服务端私有 keywords 与上下架状态）。
 */
@Data
public class ScenarioAdminVO {

    private Long id;

    /** 场景标题 */
    private String title;

    /** 情景背景描述 */
    private String contextText;

    /** 要用户回答的问题 */
    private String questionText;

    /** 要点清单（含 keywords，仅管理端可见） */
    private List<ScenarioKeyPoint> keyPoints;

    /** 难度 1-3 */
    private Integer difficulty;

    /** 关联知识点 ID（可空） */
    private Long knowledgeId;

    /** 1 上架 / 0 下架 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
