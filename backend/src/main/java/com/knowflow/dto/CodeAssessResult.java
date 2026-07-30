package com.knowflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 自动化代码评估结果（SC1-AI-02）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeAssessResult {

    /** 综合得分 0-100（动态评测 + 静态检查加权） */
    private int score;

    /** 能力评级：入门 / 进阶 / 熟练 */
    private String level;

    /** 通过的测试用例数 */
    private int passedTests;

    /** 测试用例总数 */
    private int totalTests;

    /** 是否全部用例通过 */
    private boolean passed;

    /** 静态代码检查发现的问题 */
    private List<StaticIssue> staticIssues;

    /** AI 生成的能力评估报告（未配置大模型时为 null） */
    private String aiReport;

    /** 服务端是否存在可用 AI 配置 */
    private boolean aiConfigured;

    /** 一句话总结 */
    private String summary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StaticIssue {
        /** 规则名，如 LONG_LINE / DEBUG_PRINT / TODO_LEFT / BRACE_UNBALANCED */
        private String rule;
        private String message;
        /** 问题所在行（0 表示无具体行） */
        private int line;
    }
}
