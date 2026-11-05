package com.knowflow.coding.service;

import com.knowflow.coding.dto.CodeAssessRequest;
import com.knowflow.coding.dto.CodeAssessResult;

/** 自动化代码评估服务（SC1-AI-02）：动态评测 + 静态检查 + AI 报告。 */
public interface CodeAssessService {
    CodeAssessResult assess(CodeAssessRequest request, Long userId);
}
