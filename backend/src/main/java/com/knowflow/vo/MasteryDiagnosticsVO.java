package com.knowflow.vo;

import lombok.Data;

/**
 * 掌握度引擎可观察性诊断（Knowledge Mastery Engine，Phase 2-B）。
 * 用于确认「事件已落库但知识点无映射」的情况（引擎优雅跳过，不阻断业务）。
 */
@Data
public class MasteryDiagnosticsVO {

    /** 映射总数。 */
    private Long totalMappings;

    /** ACCEPTED 映射数（实际进入 Mastery）。 */
    private Long acceptedMappings;

    /** PENDING 映射数（待 AI/人工确认）。 */
    private Long pendingMappings;

    /** REJECTED 映射数。 */
    private Long rejectedMappings;

    /** 学习事件中「无已接受映射」的不同资源数（引擎跳过的事件）。 */
    private Long unmappedResources;

    /** 说明。 */
    private String note;
}
