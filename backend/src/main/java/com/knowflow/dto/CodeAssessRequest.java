package com.knowflow.dto;

import lombok.Data;

/**
 * 自动化代码评估请求（SC1-AI-02）。
 * 结合动态测试用例执行、静态代码检查与 AI 综合能力评判，生成能力报告。
 */
@Data
public class CodeAssessRequest {
    /** 关联题目 ID（可选；提供则自动拉取该题目的测试用例） */
    private Long questionId;

    /** 语言标识：python / java / javascript / cpp / sql */
    private String language;

    /** 用户提交的源代码 */
    private String code;

    /** 直接传入测试用例（可选）；不传且提供 questionId 时由后端拉取 */
    private String testCasesJson;

    /** 可选超时覆盖（毫秒） */
    private Long timeLimitMs;
}
