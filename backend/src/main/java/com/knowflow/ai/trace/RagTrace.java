package com.knowflow.ai.trace;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次知识库检索的真实执行轨迹。
 * <p>只记录后端实际执行过的步骤与真实计数/耗时，用于聊天页面的「知识库检索」时间线。</p>
 */
@Data
public class RagTrace {

    private String traceId;

    /** 实际策略：vector（向量检索）/ keyword（关键词降级）/ permission_denied / disabled / none。 */
    private String strategy;

    /** 当前用户可见且状态正常的文档数。 */
    private int accessibleDocCount;

    /** 真实召回片段数。 */
    private int resultCount;

    /** 是否检索到可用于回答的上下文（证据是否充分）。 */
    private boolean evidenceSufficient;

    /** 整体检索耗时（毫秒）。 */
    private long durationMs;

    private List<RagTraceStep> steps = new ArrayList<>();

    public void addStep(RagTraceStep step) {
        steps.add(step);
    }
}
