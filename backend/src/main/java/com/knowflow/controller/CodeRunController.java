package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.CodeAssistRequest;
import com.knowflow.dto.CodeAssistResult;
import com.knowflow.dto.CodeAssessRequest;
import com.knowflow.dto.CodeAssessResult;
import com.knowflow.dto.CodeDebugRequest;
import com.knowflow.dto.CodeDebugResult;
import com.knowflow.dto.CodeRunRequest;
import com.knowflow.dto.CodeRunResult;
import com.knowflow.exception.BusinessException;
import com.knowflow.service.AiService;
import com.knowflow.service.CodeAssessService;
import com.knowflow.service.CodeDebugService;
import com.knowflow.service.CodeExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 代码在线运行沙箱接口（SC1-IDE-01）与 AI 编程助手内联接口（SC1-AI-01）。
 * <p>需要登录（JWT）。后端基于系统运行时真实执行用户代码，支持 Python/Java/JavaScript/C++；
 * AI 助手携带当前代码上下文解释运行错误或回答编程问题。
 */
@Slf4j
@Tag(name = "代码运行沙箱")
@RestController
@RequestMapping("/api/code")
@RequiredArgsConstructor
public class CodeRunController {

    private final CodeExecutionService codeExecutionService;
    private final AiService aiService;
    private final CodeAssessService codeAssessService;
    private final CodeDebugService codeDebugService;

    @Operation(summary = "在线运行代码（Python/Java/JavaScript/C++ 真实执行；支持工作区模式）")
    @PostMapping("/run")
    public Result<CodeRunResult> run(@RequestBody CodeRunRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        CodeRunResult result = codeExecutionService.execute(request, userId);
        return Result.success(result);
    }

    @Operation(summary = "AI 编程助手内联协助（解释错误 / 回答编程问题）")
    @PostMapping("/ai-assist")
    public Result<CodeAssistResult> aiAssist(@RequestBody CodeAssistRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        CodeAssistResult result = new CodeAssistResult();
        try {
            String system = buildAssistantSystemPrompt();
            String user = buildAssistantUserPrompt(request);
            String answer = aiService.complete(system, user, null, userId);
            result.setConfigured(true);
            result.setAnswer(answer);
        } catch (BusinessException e) {
            // 密钥无效 / 未配置：优雅降级，不抛 500，由前端展示引导
            result.setConfigured(false);
            result.setAnswer("AI 编程助手当前未启用：" + e.getMessage());
        }
        return Result.success(result);
    }

    private String buildAssistantSystemPrompt() {
        return "你是一个集成在在线代码编辑器中的资深编程助手，服务于编程学习平台 knowflow。\n"
                + "你的职责是：当用户代码运行出错时，解释错误原因并给出具体修改建议与修正后的关键代码片段；"
                + "当用户提出编程问题时，结合其当前代码给出清晰、准确、可操作的解答。\n"
                + "要求：\n"
                + "1. 用简体中文回答，语气友好、面向学习者，适当说明原理；\n"
                + "2. 涉及代码修改时，给出最小可运行的修正片段（可用 markdown 代码块，并标注语言）；\n"
                + "3. 不输出与问题无关的长篇大论，聚焦用户当前代码与问题；\n"
                + "4. 若用户代码存在多处问题，优先指出导致当前错误的最关键问题。";
    }

    @Operation(summary = "自动化代码评估（SC1-AI-02）：动态测试用例 + 静态检查 + AI 能力报告")
    @PostMapping("/assess")
    public Result<CodeAssessResult> assess(@RequestBody CodeAssessRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        CodeAssessResult result = codeAssessService.assess(request, userId);
        return Result.success(result);
    }

    @Operation(summary = "轻量在线调试运行（推进 2.1）：Python 逐行追踪 + 全语言错误行号定位")
    @PostMapping("/debug")
    public Result<CodeDebugResult> debug(@RequestBody CodeDebugRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        CodeDebugResult result = codeDebugService.debug(request, userId);
        return Result.success(result);
    }

    private String buildAssistantUserPrompt(CodeAssistRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("【编程语言】").append(req.getLanguage() == null ? "未知" : req.getLanguage()).append("\n");
        sb.append("【当前代码】\n```").append(req.getLanguage() == null ? "" : req.getLanguage()).append("\n")
                .append(req.getCode() == null ? "" : req.getCode()).append("\n```\n");
        if (req.getError() != null && !req.getError().isBlank()) {
            sb.append("【运行错误 / 标准错误】\n").append(req.getError()).append("\n");
        }
        if (req.getOutput() != null && !req.getOutput().isBlank()) {
            sb.append("【标准输出】\n").append(req.getOutput()).append("\n");
        }
        if (req.getQuestion() != null && !req.getQuestion().isBlank()) {
            sb.append("【用户问题】\n").append(req.getQuestion()).append("\n");
        } else if (req.getError() != null && !req.getError().isBlank()) {
            sb.append("【任务】请解释上述错误产生的原因，并给出具体修改建议与修正后的关键代码片段。\n");
        } else {
            sb.append("【任务】请就上述代码给出改进建议或回答用户可能的疑问。\n");
        }
        return sb.toString();
    }
}
