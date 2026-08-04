package com.knowflow.agent.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.agent.ToolContext;
import com.knowflow.agent.tool.AgentTool;
import com.knowflow.agent.tool.ToolException;
import com.knowflow.agent.tool.ToolPermission;
import com.knowflow.agent.tool.ToolResult;
import com.knowflow.dto.CodeDebugRequest;
import com.knowflow.dto.CodeDebugResult;
import com.knowflow.service.AiService;
import com.knowflow.service.CodeDebugService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 内置工具：错误诊断与自动修复（SAFE，只读分析）。
 * <p>输入问题代码 + 语言（+ 可选报错），先用 {@link CodeDebugService} 真实运行定位出错行与运行时错误，
 * 再交由大模型给出根因分析与修复后的完整代码。本工具只返回诊断与建议，
 * 实际修复落盘由 Agent 经 {@code fs_write} 完成（复用写授权 / 二次确认）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiagnoseTool implements AgentTool {

    private final ObjectMapper objectMapper;
    private final CodeDebugService codeDebugService;
    private final AiService aiService;

    @Override
    public String name() {
        return "diagnose";
    }

    @Override
    public String description() {
        return "诊断代码错误并给出自动修复：真实运行定位出错行，结合报错由大模型分析根因并返回修复后的完整代码。只读分析。";
    }

    @Override
    public JsonNode parameters() {
        com.fasterxml.jackson.databind.node.ObjectNode root = objectMapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ObjectNode props = objectMapper.createObjectNode();
        props.putObject("code").put("type", "string").put("description", "需要诊断的代码");
        props.putObject("language").put("type", "string").put("description", "语言：python/java/javascript/typescript/cpp/go 等");
        props.putObject("error").put("type", "string").put("description", "可选，已知的报错信息；省略则工具会尝试真实运行以复现错误");
        props.putObject("timeLimitMs").put("type", "integer").put("description", "运行超时上限（毫秒），默认 10000");
        root.put("type", "object").set("properties", props);
        root.putArray("required").add("code").add("language");
        return root;
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    public ToolPermission permission() {
        return ToolPermission.SAFE;
    }

    @Override
    public ToolResult execute(JsonNode args, ToolContext ctx) throws ToolException {
        long start = System.currentTimeMillis();
        String code = args.has("code") ? args.get("code").asText() : null;
        String language = args.has("language") ? args.get("language").asText() : null;
        if (code == null || code.isBlank()) {
            return ToolResult.fail("缺少必填参数 code", System.currentTimeMillis() - start);
        }
        if (language == null || language.isBlank()) {
            return ToolResult.fail("缺少必填参数 language", System.currentTimeMillis() - start);
        }
        String providedError = args.has("error") ? args.get("error").asText() : null;
        Long timeLimit = args.has("timeLimitMs") ? args.get("timeLimitMs").asLong() : null;

        // 1) 真实运行定位错误行 / 运行时报错
        String runtimeError = providedError;
        Integer errorLine = null;
        String runtimeOutput = "";
        if (providedError == null || providedError.isBlank()) {
            try {
                CodeDebugRequest req = new CodeDebugRequest();
                req.setLanguage(language);
                req.setCode(code);
                req.setTimeLimitMs(timeLimit);
                CodeDebugResult res = codeDebugService.debug(req, ctx.getUserId());
                errorLine = res.getErrorLine();
                runtimeError = res.getError();
                runtimeOutput = res.getOutput();
            } catch (Exception e) {
                log.warn("[Diagnose] 运行定位失败，转为纯模型诊断：{}", e.getMessage());
            }
        }

        // 2) 交给大模型分析根因 + 修复
        StringBuilder sb = new StringBuilder();
        sb.append("请诊断以下").append(language).append("代码的错误，并给出修复后的完整代码。\n\n");
        sb.append("【原始代码】\n```").append(language).append("\n").append(code).append("\n```\n\n");
        if (errorLine != null) {
            sb.append("【已定位出错行】第 ").append(errorLine).append(" 行\n");
        }
        if (runtimeError != null && !runtimeError.isBlank()) {
            sb.append("【运行时报错】\n").append(runtimeError).append("\n");
        }
        if (runtimeOutput != null && !runtimeOutput.isBlank()) {
            sb.append("【运行输出】\n").append(runtimeOutput).append("\n");
        }
        String systemPrompt = """
                你是一名资深软件排错专家。请基于真实运行时报错与代码给出诊断。
                必须只输出一个 JSON 对象（不要 markdown 围栏），结构如下：
                {"rootCause":"根因分析","fixLine":行号或null,"fixedCode":"修复后的完整代码（保持原语言，可直接运行）","explanation":"修复说明"}""";

        String raw;
        try {
            raw = aiService.complete(systemPrompt, sb.toString(), null, ctx.getUserId());
        } catch (Exception e) {
            throw new ToolException("调用诊断模型失败：" + e.getMessage(), e);
        }
        if (raw == null || raw.isBlank()) {
            return ToolResult.fail("模型未返回诊断结果", System.currentTimeMillis() - start);
        }
        String json = extractJson(raw);
        return ToolResult.ok(json, System.currentTimeMillis() - start);
    }

    private String extractJson(String raw) {
        String s = raw.trim();
        int fence = s.indexOf("```");
        if (fence >= 0) {
            int start = s.indexOf('{', fence);
            int end = s.lastIndexOf('}');
            if (start >= 0 && end > start) return s.substring(start, end + 1);
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        return (start >= 0 && end > start) ? s.substring(start, end + 1) : s;
    }
}
