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
import com.knowflow.service.AiService;
import com.knowflow.service.CodeExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 内置工具：单元测试生成并验证（SAFE，只读分析 + 沙箱运行，不落盘）。
 * <p>读取目标源码，由大模型生成自包含单元测试（将被测逻辑内联，便于独立运行），
 * 再用 {@link CodeExecutionService} 在沙箱真实跑一遍验证可运行，返回测试代码与运行结果。
 * 落盘由 Agent 经 {@code fs_write} 完成（复用写授权 / 二次确认）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestGenTool implements AgentTool {

    private final ObjectMapper objectMapper;
    private final AiService aiService;
    private final CodeExecutionService codeExecutionService;

    @Override
    public String name() {
        return "test_gen";
    }

    @Override
    public String description() {
        return "为目标函数/文件生成单元测试，并在沙箱真实运行验证是否通过。返回测试代码与执行结果，不修改文件。";
    }

    @Override
    public JsonNode parameters() {
        com.fasterxml.jackson.databind.node.ObjectNode root = objectMapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ObjectNode props = objectMapper.createObjectNode();
        props.putObject("path").put("type", "string")
                .put("description", "相对工作区的源文件路径（如 src/calc.py）");
        props.putObject("framework").put("type", "string")
                .put("description", "测试框架：auto(默认，按语言推断) / pytest / junit / jest / plain");
        props.putObject("timeLimitMs").put("type", "integer")
                .put("description", "沙箱运行超时（毫秒），默认 10000");
        root.put("type", "object").set("properties", props);
        root.putArray("required").add("path");
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
        String rel = args.has("path") ? args.get("path").asText() : null;
        if (rel == null || rel.isBlank()) {
            return ToolResult.fail("缺少必填参数 path", System.currentTimeMillis() - start);
        }
        String framework = args.has("framework") ? args.get("framework").asText("auto") : "auto";
        Long timeLimit = args.has("timeLimitMs") ? args.get("timeLimitMs").asLong() : null;

        Path root = FsReadTool.resolveRoot(ctx);
        Path target = FsReadTool.safeResolve(root, rel);
        if (!Files.exists(target) || Files.isDirectory(target)) {
            return ToolResult.fail("文件不存在或路径为目录：" + rel, System.currentTimeMillis() - start);
        }
        String source;
        try {
            source = Files.readString(target);
        } catch (IOException e) {
            return ToolResult.fail("读取源文件失败：" + e.getMessage(), System.currentTimeMillis() - start);
        }
        String lang = inferLang(rel);
        String fw = "auto".equals(framework) ? defaultFramework(lang) : framework;

        // 1) 生成自包含测试（将被测逻辑内联）
        String systemPrompt = """
                你是一名测试工程师。请为给定源码生成单元测试。
                要求：
                1. 将【被测逻辑内联】进测试文件，使其成为可独立运行的程序（不要依赖外部 import 被测模块）。
                2. 覆盖正常路径、边界条件与典型异常路径，至少 3 个用例。
                3. 使用所选框架的断言；运行后应能清晰打印每个用例的通过/失败。"""
                + "\n测试框架：" + fw + "；语言：" + lang + "。\n"
                + "只输出一个 JSON 对象（不要 markdown 围栏），结构："
                + "{\"testFile\":\"建议文件名\",\"framework\":\"" + fw + "\",\"testCode\":\"完整测试代码\"}";
        String userPrompt = "【源码 " + rel + "】\n```" + lang + "\n" + source + "\n```";

        String raw;
        try {
            raw = aiService.complete(systemPrompt, userPrompt, null, ctx.getUserId());
        } catch (Exception e) {
            throw new ToolException("调用测试生成模型失败：" + e.getMessage(), e);
        }
        String json = extractJson(raw);
        String testCode = parseField(json, "testCode");
        String testFile = parseField(json, "testFile");
        if (testCode == null || testCode.isBlank()) {
            return ToolResult.fail("模型未返回测试代码", System.currentTimeMillis() - start);
        }

        // 2) 沙箱运行验证
        CodeRunRequest runReq = new CodeRunRequest();
        runReq.setLanguage(mapRunLang(lang));
        runReq.setCode(testCode);
        if (timeLimit != null) runReq.setTimeLimitMs(timeLimit);
        CodeRunResult run = codeExecutionService.execute(runReq, ctx.getUserId());

        String verdict = """
                {
                  "testFile": "%s",
                  "framework": "%s",
                  "runStatus": "%s",
                  "passed": %s,
                  "output": %s,
                  "testCode": %s
                }"""
                .formatted(
                        escape(testFile),
                        escape(fw),
                        run.getStatus(),
                        run.getStatus() == CodeRunResult.Status.SUCCESS ? "true" : "false",
                        escape(run.getOutput() == null ? "" : run.getOutput()),
                        escape(testCode)
                );
        return ToolResult.ok(verdict, System.currentTimeMillis() - start);
    }

    private String parseField(String json, String field) {
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode v = node.get(field);
            return v == null ? null : v.asText();
        } catch (Exception e) {
            return null;
        }
    }

    private String defaultFramework(String lang) {
        return switch (lang) {
            case "python" -> "pytest";
            case "java" -> "junit";
            case "javascript", "typescript" -> "jest";
            default -> "plain";
        };
    }

    private String mapRunLang(String lang) {
        return switch (lang) {
            case "python" -> "python";
            case "java" -> "java";
            case "javascript", "typescript" -> "javascript";
            case "cpp", "c" -> "cpp";
            default -> "python";
        };
    }

    private String inferLang(String path) {
        String n = path.toLowerCase();
        if (n.endsWith(".py")) return "python";
        if (n.endsWith(".java")) return "java";
        if (n.endsWith(".ts") || n.endsWith(".tsx")) return "typescript";
        if (n.endsWith(".js") || n.endsWith(".jsx")) return "javascript";
        if (n.endsWith(".go")) return "go";
        if (n.endsWith(".rs")) return "rust";
        if (n.endsWith(".cpp") || n.endsWith(".cc") || n.endsWith(".c") || n.endsWith(".h") || n.endsWith(".hpp")) return "cpp";
        if (n.endsWith(".cs")) return "csharp";
        return "text";
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
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
