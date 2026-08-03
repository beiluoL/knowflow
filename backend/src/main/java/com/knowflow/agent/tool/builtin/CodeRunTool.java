package com.knowflow.agent.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.agent.ToolContext;
import com.knowflow.agent.tool.AgentTool;
import com.knowflow.agent.tool.ToolException;
import com.knowflow.agent.tool.ToolPermission;
import com.knowflow.agent.tool.ToolResult;
import com.knowflow.dto.CodeRunRequest;
import com.knowflow.dto.CodeRunResult;
import com.knowflow.service.CodeExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 内置工具：在受信任沙箱中执行用户代码（复用既有 CodeExecutionService）。
 * 支持 python / java / javascript / cpp，属于 SAFE 只读执行能力，可直接调用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeRunTool implements AgentTool {

    private final CodeExecutionService codeExecutionService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "code_run";
    }

    @Override
    public String description() {
        return "在沙箱中执行一段代码并返回运行结果（stdout/stderr/退出码）。"
                + "支持语言：python、java、javascript、cpp。可用于验证算法、运行示例、调试。";
    }

    @Override
    public JsonNode parameters() {
        com.fasterxml.jackson.databind.node.ObjectNode root = objectMapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ObjectNode props = objectMapper.createObjectNode();
        props.putObject("language").put("type", "string").put("description", "代码语言：python / java / javascript / cpp");
        props.putObject("code").put("type", "string").put("description", "要执行的源代码");
        root.put("type", "object").set("properties", props);
        root.putArray("required").add("language").add("code");
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
        try {
            String language = args.has("language") ? args.get("language").asText() : null;
            String code = args.has("code") ? args.get("code").asText() : null;
            if (language == null || language.isBlank() || code == null || code.isBlank()) {
                return ToolResult.fail("缺少必填参数 language 或 code", System.currentTimeMillis() - start);
            }
            CodeRunRequest req = new CodeRunRequest();
            req.setLanguage(language);
            req.setCode(code);
            if (args.has("stdin") && !args.get("stdin").isNull()) {
                req.setStdin(args.get("stdin").asText());
            }
            CodeRunResult res = codeExecutionService.execute(req, ctx.getUserId());
            String output = "status=" + res.getStatus() + " exitCode=" + res.getExitCode() + "\n"
                    + (res.getOutput() != null ? res.getOutput() : "")
                    + (res.getError() != null && !res.getError().isEmpty()
                        ? "\n[error]\n" + res.getError() : "");
            return ToolResult.ok(output, System.currentTimeMillis() - start);
        } catch (Exception e) {
            throw new ToolException("代码执行失败：" + e.getMessage(), e);
        }
    }
}
