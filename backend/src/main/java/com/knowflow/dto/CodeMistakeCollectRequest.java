package com.knowflow.dto;

import lombok.Data;

/**
 * 代码运行异常归集请求（SC1-AI-03）。
 * 前端在代码运行出错（编译错误 / 运行时错误 / 超时 / 逻辑异常）时，将语言、错误文本、
 * 代码片段与题目 ID 上报，后端自动提取错误类型并关联知识库形成错题。
 */
@Data
public class CodeMistakeCollectRequest {
    /** 语言标识：python / java / javascript / cpp / sql 等 */
    private String language;

    /** 运行错误文本（stderr 或错误描述） */
    private String error;

    /** 用户提交的源代码（用于错题留存上下文） */
    private String code;

    /** 关联题目 ID（沙箱自由练习时为 null） */
    private Long questionId;
}
