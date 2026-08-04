package com.knowflow.agent.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.agent.ToolContext;
import com.knowflow.agent.tool.AgentTool;
import com.knowflow.agent.tool.ToolException;
import com.knowflow.agent.tool.ToolPermission;
import com.knowflow.agent.tool.ToolResult;
import com.knowflow.service.AiService;
import com.knowflow.service.DocChunkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * 内置工具：API 文档查询与集成（SAFE，只读）。
 * <p>优先基于知识库向量检索召回相关文档片段，交由大模型组织成「用法说明 + 集成示例」；
 * 若用户提供了官方文档 URL，则额外抓取页面正文作为补充上下文（仅 GET、超时 8s、限 200KB）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiDocTool implements AgentTool {

    private final ObjectMapper objectMapper;
    private final AiService aiService;
    private final DocChunkService docChunkService;

    private static final int MAX_CONTEXT = 5;

    @Override
    public String name() {
        return "api_doc";
    }

    @Override
    public String description() {
        return "查询 API 文档与集成示例：基于知识库检索相关文档，可选抓取指定官方文档 URL，返回用法说明与代码示例。";
    }

    @Override
    public JsonNode parameters() {
        com.fasterxml.jackson.databind.node.ObjectNode root = objectMapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ObjectNode props = objectMapper.createObjectNode();
        props.putObject("query").put("type", "string").put("description", "要查询的 API / 库 / 功能，如 'axios 上传文件'");
        props.putObject("url").put("type", "string").put("description", "可选，官方文档页 URL，抓取正文作为补充上下文");
        root.put("type", "object").set("properties", props);
        root.putArray("required").add("query");
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
        String query = args.has("query") ? args.get("query").asText() : null;
        if (query == null || query.isBlank()) {
            return ToolResult.fail("缺少必填参数 query", System.currentTimeMillis() - start);
        }
        String url = args.has("url") ? args.get("url").asText() : null;

        // 1) 知识库检索
        List<String> chunks = docChunkService.searchSimilar(query, MAX_CONTEXT);
        StringBuilder context = new StringBuilder();
        if (!chunks.isEmpty()) {
            context.append("【知识库检索片段】\n").append(String.join("\n---\n", chunks)).append("\n");
        } else {
            context.append("（知识库无相关片段）\n");
        }

        // 2) 可选抓取官方文档
        if (url != null && !url.isBlank()) {
            String page = fetch(url);
            if (page != null) {
                context.append("\n【官方文档 ").append(url).append("】\n").append(page).append("\n");
            }
        }

        String systemPrompt = """
                你是 API 文档助手。基于提供的检索片段/文档，回答用户的 API 查询。
                输出结构：先一句话概述，再给出「用法说明」，最后给出可运行的「集成示例」代码。
                若上下文不足以回答，请明确说明并给出建议查找的官方文档入口。""";
        String userPrompt = "查询：" + query + "\n\n" + context;

        String raw;
        try {
            raw = aiService.complete(systemPrompt, userPrompt, null, ctx.getUserId());
        } catch (Exception e) {
            throw new ToolException("调用文档模型失败：" + e.getMessage(), e);
        }
        if (raw == null || raw.isBlank()) {
            return ToolResult.fail("模型未返回文档结果", System.currentTimeMillis() - start);
        }
        return ToolResult.ok(raw, System.currentTimeMillis() - start);
    }

    /** 抓取文档页正文（仅 GET，超时 8s，正文截断 200KB） */
    private String fetch(String url) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "knowflow-agent/1.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300 && resp.body() != null) {
                String body = resp.body();
                return body.length() > 200_000 ? body.substring(0, 200_000) : body;
            }
        } catch (Exception e) {
            log.warn("[api_doc] 抓取失败：{}", e.getMessage());
        }
        return null;
    }
}
