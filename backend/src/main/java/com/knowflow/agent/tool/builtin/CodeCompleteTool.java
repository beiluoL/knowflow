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

/**
 * 内置工具：行内代码补全（SAFE，只读）。
 * <p>基于当前光标的前缀/后缀上下文（FIM 风格），由大模型返回续写片段，用于「多语言补全」能力。
 * 仅返回补全代码本身（不含解释、不含 markdown 围栏），便于前端直接插入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeCompleteTool implements AgentTool {

    private final ObjectMapper objectMapper;
    private final com.knowflow.service.AiService aiService;

    @Override
    public String name() {
        return "code_complete";
    }

    @Override
    public String description() {
        return "根据光标处的前缀与后缀上下文，生成行内代码补全续写片段。返回纯代码，不解释。";
    }

    @Override
    public JsonNode parameters() {
        com.fasterxml.jackson.databind.node.ObjectNode root = objectMapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ObjectNode props = objectMapper.createObjectNode();
        props.putObject("prefix").put("type", "string").put("description", "光标之前的代码上下文");
        props.putObject("suffix").put("type", "string").put("description", "光标之后的代码上下文（可选）");
        props.putObject("language").put("type", "string").put("description", "语言：python/java/javascript/typescript/cpp/go 等");
        props.putObject("maxTokens").put("type", "integer").put("description", "补全最大长度（近似字符数），默认 300");
        root.put("type", "object").set("properties", props);
        root.putArray("required").add("prefix").add("language");
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
        String prefix = args.has("prefix") ? args.get("prefix").asText() : null;
        if (prefix == null) {
            return ToolResult.fail("缺少必填参数 prefix", System.currentTimeMillis() - start);
        }
        String suffix = args.has("suffix") ? args.get("suffix").asText() : "";
        String language = args.has("language") ? args.get("language").asText("text") : "text";
        int maxTokens = args.has("maxTokens") ? args.get("maxTokens").asInt(300) : 300;

        String systemPrompt = """
                你是一个代码补全引擎。根据前缀与后缀，直接输出应插入光标处的代码续写片段。
                规则：
                1. 只输出补全代码本身，不要任何解释、注释性前言或 markdown 代码围栏。
                2. 不要重复前缀或后缀已存在的代码；补全应是二者之间缺失的部分。
                3. 保持与上下文一致的语言与缩进风格。
                4. 长度控制在 %d 字符以内。""".formatted(maxTokens);

        String userPrompt = "语言：" + language + "\n\n【前缀】\n" + prefix
                + (suffix.isBlank() ? "" : "\n\n【后缀】\n" + suffix);

        String raw;
        try {
            raw = aiService.complete(systemPrompt, userPrompt, null, ctx.getUserId());
        } catch (Exception e) {
            throw new ToolException("调用补全模型失败：" + e.getMessage(), e);
        }
        if (raw == null || raw.isBlank()) {
            return ToolResult.fail("模型未返回补全", System.currentTimeMillis() - start);
        }
        String completion = stripFence(raw).strip();
        return ToolResult.ok(completion, System.currentTimeMillis() - start);
    }

    private String stripFence(String raw) {
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            int end = s.lastIndexOf("```");
            if (firstNewline > 0 && end > firstNewline) {
                return s.substring(firstNewline + 1, end).strip();
            }
        }
        return s;
    }
}
