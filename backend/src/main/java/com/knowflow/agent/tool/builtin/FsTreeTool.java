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
import java.util.Comparator;
import java.util.List;

/**
 * 内置工具：输出工作区的目录结构树（SAFE，只读）。
 * <p>用于让编程 Agent「理解项目文件结构」，支撑跨文件编辑与上下文感知对话。
 * 默认最大扫描深度 4 层、最多 400 个条目，避免超大仓库拖慢对话。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FsTreeTool implements AgentTool {

    private final ObjectMapper objectMapper;

    /** 最大扫描条目数，超出则截断并在结果中提示，避免大模型上下文被目录淹没 */
    private static final int MAX_ENTRIES = 400;
    /** 默认最大深度 */
    private static final int DEFAULT_MAX_DEPTH = 4;

    @Override
    public String name() {
        return "fs_tree";
    }

    @Override
    public String description() {
        return "列出工作区的目录结构树（含文件与子目录），用于理解项目文件组织。只读，不读取文件内容。";
    }

    @Override
    public JsonNode parameters() {
        com.fasterxml.jackson.databind.node.ObjectNode root = objectMapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ObjectNode props = objectMapper.createObjectNode();
        props.putObject("maxDepth").put("type", "integer")
                .put("description", "最大扫描深度，默认 4，范围 1~10");
        props.putObject("includeHidden").put("type", "boolean")
                .put("description", "是否包含隐藏文件/目录（以 . 开头），默认 false");
        root.put("type", "object").set("properties", props);
        root.putArray("required");
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
        int maxDepth = args.has("maxDepth") ? args.get("maxDepth").asInt(DEFAULT_MAX_DEPTH) : DEFAULT_MAX_DEPTH;
        maxDepth = Math.max(1, Math.min(10, maxDepth));
        boolean includeHidden = args.has("includeHidden") && args.get("includeHidden").asBoolean();

        Path root = FsReadTool.resolveRoot(ctx);
        if (!Files.exists(root)) {
            return ToolResult.ok("(空工作区，尚无文件)", System.currentTimeMillis() - start);
        }
        try {
            StringBuilder sb = new StringBuilder();
            int[] count = {0};
            boolean[] truncated = {false};
            walk(sb, root, root, "", 0, maxDepth, includeHidden, count, truncated);
            if (truncated[0]) {
                sb.append("\n...[已截断，超出 ").append(MAX_ENTRIES).append(" 个条目上限]");
            }
            sb.insert(0, "项目结构（最多 ").append(maxDepth).append(" 层）：\n");
            return ToolResult.ok(sb.toString(), System.currentTimeMillis() - start);
        } catch (IOException e) {
            throw new ToolException("遍历目录失败：" + e.getMessage(), e);
        }
    }

    private void walk(StringBuilder sb, Path root, Path current, String prefix,
                      int depth, int maxDepth, boolean includeHidden, int[] count, boolean[] truncated)
            throws IOException {
        if (depth >= maxDepth || count[0] >= MAX_ENTRIES) return;
        List<Path> children;
        try (var stream = Files.list(current)) {
            children = stream
                    .filter(p -> includeHidden || !p.getFileName().toString().startsWith("."))
                    .sorted(Comparator.comparing((Path p) -> Files.isDirectory(p) ? 0 : 1)
                            .thenComparing(p -> p.getFileName().toString().toLowerCase()))
                    .toList();
        }
        for (int i = 0; i < children.size(); i++) {
            if (count[0] >= MAX_ENTRIES) {
                truncated[0] = true;
                return;
            }
            Path child = children.get(i);
            boolean isLast = i == children.size() - 1;
            boolean isDir = Files.isDirectory(child);
            String name = child.getFileName().toString();
            String rel = root.relativize(child).toString().replace('\\', '/');
            sb.append(prefix).append(isLast ? "└── " : "├── ").append(name)
                    .append(isDir ? "/" : "").append("  (").append(rel).append(")\n");
            count[0]++;
            if (isDir) {
                walk(sb, root, child, prefix + (isLast ? "    " : "│   "), depth + 1, maxDepth, includeHidden, count, truncated);
            }
        }
    }
}
