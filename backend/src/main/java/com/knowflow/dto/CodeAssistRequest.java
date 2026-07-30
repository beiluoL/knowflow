package com.knowflow.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 编程助手内联请求（SC1-AI-01）。
 * 携带当前代码上下文与（可选的）运行错误/输出，AI 据此解释错误或回答编程问题。
 */
@Data
@NoArgsConstructor
public class CodeAssistRequest {
    /** 编程语言标识 */
    private String language;

    /** 当前编辑器中的源代码 */
    private String code;

    /** 最近一次运行的错误信息（如有） */
    private String error;

    /** 最近一次运行的标准输出（如有） */
    private String output;

    /** 用户自由提问内容（可空；为空且存在 error 时由后端要求“解释错误”） */
    private String question;
}
