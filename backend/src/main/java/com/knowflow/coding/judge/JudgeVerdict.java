package com.knowflow.coding.judge;

import java.util.List;
import java.util.Map;

/** 判题结论。score=null 表示未完成判题（平台失败/服务不可用），任何调用方不得据此记 0 分。 */
public record JudgeVerdict(
        String status,
        Integer score,
        int passedCount,
        int totalCount,
        Integer executionTimeMs,
        Integer memoryUsedKb,
        String compileOutput,
        List<CaseResult> cases,
        String runnerMode,
        Map<String, Object> detail) {

    public boolean platformFailure() {
        return JudgeStatus.isPlatformFailure(status);
    }
}
