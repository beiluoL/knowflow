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
import java.nio.file.Paths;

/**
 * 内置工具：读取工作区文件内容或列出目录（SAFE，只读）。路径限定在用户工作区根目录下，
 * 禁止路径穿越（..），避免越权读系统文件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FsReadTool implements AgentTool {

    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "fs_read";
    }

    @Override
    public String description() {
        return "读取工作区中某个文件的内容，或列出目录下的条目。只能访问用户工作区内的文件。";
    }

    @Override
    public JsonNode parameters() {
        com.fasterxml.jackson.databind.node.ObjectNode root = objectMapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ObjectNode props = objectMapper.createObjectNode();
        props.putObject("path").put("type", "string").put("description", "相对工作区的文件或目录路径，如 src/main.py");
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
        Path root = resolveRoot(ctx);
        Path target = safeResolve(root, rel);
        try {
            if (Files.isDirectory(target)) {
                StringBuilder sb = new StringBuilder("目录 ").append(rel).append(":\n");
                try (var stream = Files.list(target)) {
                    stream.forEach(p -> sb.append(Files.isDirectory(p) ? "[D] " : "[F] ")
                            .append(p.getFileName()).append("\n"));
                }
                return ToolResult.ok(sb.toString(), System.currentTimeMillis() - start);
            }
            if (!Files.exists(target)) {
                return ToolResult.fail("文件不存在：" + rel, System.currentTimeMillis() - start);
            }
            String content = Files.readString(target);
            return ToolResult.ok(content, System.currentTimeMillis() - start);
        } catch (IOException e) {
            throw new ToolException("读取失败：" + e.getMessage(), e);
        }
    }

    /** 解析用户工作区根目录：./agent-workspaces/{userId}/。 */
    static Path resolveRoot(ToolContext ctx) {
        String base = "agent-workspaces";
        if (ctx.getUserId() != null) base = base + "/" + ctx.getUserId();
        return Paths.get(base).toAbsolutePath().normalize();
    }

    /** 安全解析：禁止路径穿越。 */
    static Path safeResolve(Path root, String rel) throws ToolException {
        Path resolved = root.resolve(rel).normalize();
        if (!resolved.startsWith(root)) {
            throw new ToolException("非法路径，禁止访问工作区之外的文件");
        }
        return resolved;
    }
}
