package com.knowflow.coding.judge;

import java.util.List;
import java.util.Map;

/**
 * 逐用例结果。
 * 隐藏用例（hidden=true）禁止回传 input/expected，只能回 passed/weight 汇总信息。
 */
public record CaseResult(
        boolean hidden,
        Integer orderNo,
        String input,
        String expected,
        String actual,
        boolean passed,
        int timeMs,
        String status) {

    /** 对外视图：隐藏用例脱敏。 */
    public Map<String, Object> toView() {
        if (hidden) {
            return Map.of("hidden", true, "orderNo", orderNo == null ? 0 : orderNo,
                    "passed", passed, "status", status);
        }
        return Map.of("hidden", false, "orderNo", orderNo == null ? 0 : orderNo,
                "input", input == null ? "" : input,
                "expected", expected == null ? "" : expected,
                "actual", actual == null ? "" : actual,
                "passed", passed, "timeMs", timeMs, "status", status);
    }
}
