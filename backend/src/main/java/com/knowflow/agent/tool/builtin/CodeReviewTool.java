package com.knowflow.agent.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.agent.ToolContext;
import com.knowflow.agent.tool.AgentTool;
import com.knowflow.agent.tool.ToolException;
import com.knowflow.agent.tool.ToolPermission;
import com.knowflow.agent.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 内置工具：智能代码审查（SAFE，只读）。
 * <p>读取指定文件（或目录下的全部源码），交由大模型做静态审查，输出分级问题清单
 * （bug / 安全 / 性能 / 规范 / 可维护性），每条含行号、说明与修复建议。
 * 本工具只做分析与建议，不直接落盘修复——修复由 Agent 经 {@code fs_write} 完成，
 * 从而复用既有的写授权与二次确认机制。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeReviewTool implements AgentTool {

    private final ObjectMapper objectMapper;
    private final com.knowflow.service.AiService aiService;

    @Override
    public String name() {
        return "code_review";
    }

    @Override
    public String description() {
        return "对指定文件或目录做智能代码审查，输出分级问题清单（bug/安全/性能/规范）与修复建议。只读分析，不修改文件。";
    }

    @Override
    public JsonNode parameters() {
        com.fasterxml.jackson.databind.node.ObjectNode root = objectMapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ObjectNode props = objectMapper.createObjectNode();
        props.putObject("path").put("type", "string")
                .put("description", "相对工作区的文件或目录路径；传文件则审该文件，传目录则审目录下全部源码");
        props.putObject("severity").put("type", "string")
                .put("description", "关注级别：all(默认) / warning / error，仅返回不低于该级别的问题");
        props.putObject("language").put("type", "string")
                .put("description", "可选，显式指定语言（如 python/java），省略则由扩展名推断");
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
        String severity = args.has("severity") ? args.get("severity").asText("all") : "all";
        String language = args.has("language") ? args.get("language").asText() : null;

        Path root = FsReadTool.resolveRoot(ctx);
        Path target = FsReadTool.safeResolve(root, rel);
        if (!Files.exists(target)) {
            return ToolResult.fail("路径不存在：" + rel, System.currentTimeMillis() - start);
        }

        // 收集待审源码
        List<Path> files = new ArrayList<>();
        try {
            if (Files.isDirectory(target)) {
                collectSource(target, files, 0, 4);
            } else {
                files.add(target);
            }
        } catch (IOException e) {
            throw new ToolException("读取目录失败：" + e.getMessage(), e);
        }
        if (files.isEmpty()) {
            return ToolResult.fail("路径下未找到可审查的源码文件：" + rel, System.currentTimeMillis() - start);
        }

        StringBuilder codeBundle = new StringBuilder();
        for (Path f : files) {
            String r = root.relativize(f).toString().replace('\\', '/');
            String content;
            try {
                content = Files.readString(f);
            } catch (IOException e) {
                continue;
            }
            String lang = language != null ? language : inferLang(r);
            codeBundle.append("===== 文件: ").append(r).append(" (").append(lang).append(") =====\n")
                    .append(content).append("\n\n");
        }

        String systemPrompt = """
                你是一名资深代码审查专家。请审查用户提供的源码，找出真实存在的问题。
                只报告有依据的、值得修复的问题，不要无中生有，也不要报告纯风格偏好（除非严重影响可读性）。
                必须只输出一个 JSON 对象（不要包含任何解释性文字或 markdown 围栏），结构如下：
                {"summary":"一句话总结","issues":[{"severity":"error|warning|info","line":行号或null,"rule":"问题类别(bug|security|performance|style|maintainability)","desc":"问题描述","suggestion":"修复建议"}]}
                行号尽量对应实际代码位置；若无法定位具体行则置 null。""";
        String userPrompt = "审查级别：" + severity + "\n\n" + codeBundle;

        String raw;
        try {
            raw = aiService.complete(systemPrompt, userPrompt, null, ctx.getUserId());
        } catch (Exception e) {
            throw new ToolException("调用审查模型失败：" + e.getMessage(), e);
        }
        if (raw == null || raw.isBlank()) {
            return ToolResult.fail("模型未返回审查结果", System.currentTimeMillis() - start);
        }
        // 抽取 JSON（兼容模型偶尔包裹的 ```json 围栏）
        String json = extractJson(raw);
        return ToolResult.ok(json, System.currentTimeMillis() - start);
    }

    private void collectSource(Path dir, List<Path> out, int depth, int maxDepth) throws IOException {
        if (depth >= maxDepth || out.size() >= 60) return;
        try (var stream = Files.list(dir)) {
            List<Path> children = stream
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .sorted().toList();
            for (Path c : children) {
                if (out.size() >= 60) return;
                if (Files.isDirectory(c)) {
                    collectSource(c, out, depth + 1, maxDepth);
                } else if (isSource(c)) {
                    out.add(c);
                }
            }
        }
    }

    private boolean isSource(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        return n.endsWith(".py") || n.endsWith(".js") || n.endsWith(".ts") || n.endsWith(".jsx")
                || n.endsWith(".tsx") || n.endsWith(".java") || n.endsWith(".go") || n.endsWith(".rs")
                || n.endsWith(".c") || n.endsWith(".cpp") || n.endsWith(".cc") || n.endsWith(".h")
                || n.endsWith(".hpp") || n.endsWith(".cs") || n.endsWith(".sql") || n.endsWith(".sh");
    }

    private String inferLang(String path) {
        String n = path.toLowerCase();
        if (n.endsWith(".py")) return "python";
        if (n.endsWith(".java")) return "java";
        if (n.endsWith(".ts") || n.endsWith(".tsx")) return "typescript";
        if (n.endsWith(".js") || n.endsWith(".jsx")) return "javascript";
        if (n.endsWith(".go")) return "go";
        if (n.endsWith(".rs")) return "rust";
        if (n.endsWith(".sql")) return "sql";
        if (n.endsWith(".cpp") || n.endsWith(".cc") || n.endsWith(".c") || n.endsWith(".h") || n.endsWith(".hpp")) return "cpp";
        if (n.endsWith(".cs")) return "csharp";
        return "text";
    }

    /** 从模型输出中提取 JSON 片段（去除可能的 markdown 围栏与前后文本） */
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
        if (start >= 0 && end > start) return s.substring(start, end + 1);
        return s;
    }
}
