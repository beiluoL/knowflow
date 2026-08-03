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

/**
 * 内置工具：写入工作区文件（WRITE，需用户授权）。路径限定在用户工作区根目录内，
 * 禁止路径穿越。写入前确保父目录存在。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FsWriteTool implements AgentTool {

    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "fs_write";
    }

    @Override
    public String description() {
        return "将内容写入工作区中的某个文件（覆盖写）。只能写入用户工作区内的路径，需写授权。";
    }

    @Override
    public JsonNode parameters() {
        com.fasterxml.jackson.databind.node.ObjectNode root = objectMapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ObjectNode props = objectMapper.createObjectNode();
        props.putObject("path").put("type", "string").put("description", "相对工作区的文件路径，如 src/main.py");
        props.putObject("content").put("type", "string").put("description", "要写入的文件内容");
        root.put("type", "object").set("properties", props);
        root.putArray("required").add("path").add("content");
        return root;
    }

    @Override
    public boolean enabledByDefault() {
        return false;
    }

    @Override
    public ToolPermission permission() {
        return ToolPermission.WRITE;
    }

    @Override
    public ToolResult execute(JsonNode args, ToolContext ctx) throws ToolException {
        long start = System.currentTimeMillis();
        String rel = args.has("path") ? args.get("path").asText() : null;
        String content = args.has("content") ? args.get("content").asText() : null;
        if (rel == null || rel.isBlank() || content == null) {
            return ToolResult.fail("缺少必填参数 path 或 content", System.currentTimeMillis() - start);
        }
        Path root = FsReadTool.resolveRoot(ctx);
        Path target = FsReadTool.safeResolve(root, rel);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content);
            return ToolResult.ok("已写入 " + rel + " (" + content.length() + " 字符)", System.currentTimeMillis() - start);
        } catch (IOException e) {
            throw new ToolException("写入失败：" + e.getMessage(), e);
        }
    }
}
