package com.knowflow.ai.trace;

import lombok.Data;

/**
 * 单条检索/执行步骤。
 * <p>仅由后端在真实执行到该阶段时产生；未执行的步骤不会出现，前端不得自行补全。</p>
 */
@Data
public class RagTraceStep {

    /** 步骤标识，如 permission_filter / vector_search / keyword_search / evidence。 */
    private String stepId;

    /** 步骤类型，供前端选择图标与分组。 */
    private String type;

    /** 展示名称（已脱敏，不含文档内容）。 */
    private String label;

    /** success / empty / skipped / error。 */
    private String status;

    /** 安全摘要，不包含文档正文、凭证或内部提示词。 */
    private String detail;

    /** 该步骤真实耗时（毫秒）。 */
    private Long durationMs;

    /** 该步骤真实产出条数（如召回片段数、可访问文档数）。 */
    private Integer resultCount;
}
