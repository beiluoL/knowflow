package com.knowflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 代码调试运行结果（轻量在线调试器）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeDebugResult {

    /** 状态：SUCCESS / ERROR / TIMEOUT */
    private String status;

    /** 逐行执行轨迹（Python 支持；其他语言为空） */
    private List<TraceStep> trace;

    /** 出错行号（1-based，用户代码视角）；无则 null */
    private Integer errorLine;

    /** 错误信息（含追踪，已尽量精确定位） */
    private String error;

    /** 标准输出 */
    private String output;

    /** 耗时（毫秒） */
    private Long timeUsedMs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TraceStep {
        /** 用户代码行号（1-based） */
        private int line;
        /** 事件：line / call / return */
        private String event;
        /** 该步骤后的局部变量快照（JSON 文本） */
        private String vars;
    }
}
