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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内置工具：扫描工作区源码，抽取跨文件符号索引（导出函数 / 类 / 接口 / import 等）。
 * <p>基于轻量正则解析主流语言（py/js/ts/java/go/rs/c/cpp/sql），无需外部解析器，
 * 用于让 Agent 理解项目结构与跨文件引用关系，支撑「上下文感知」与「跨文件编辑」。
 * 只读，默认扫描深度 4 层、最大 120 个文件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FsSymbolsTool implements AgentTool {

    private final ObjectMapper objectMapper;

    private static final int MAX_FILES = 120;
    private static final int DEFAULT_MAX_DEPTH = 4;

    /** 各语言「定义」符号识别（捕获组 1 = 名称） */
    private static final Pattern DEF_PATTERN = Pattern.compile(
            // python: def / class / async def
            "^(?:async\\s+)?def\\s+([A-Za-z_]\\w*)|"
                    // js/ts: function / const|let|var x = (arrow|function) ; 以及 class
                    + "^(?:export\\s+)?(?:async\\s+)?function\\s+([A-Za-z_]\\w*)|"
                    + "^(?:export\\s+)?(?:const|let|var)\\s+([A-Za-z_]\\w*)\\s*[:=]|"
                    + "^(?:export\\s+)?(?:default\\s+)?class\\s+([A-Za-z_]\\w*)|"
                    // java/c#/go/cpp: 修饰符 + (class|interface|struct|func|void|类型) name(
                    + "^(?:public|private|protected|internal|static|final|abstract|override|func|export\\s+)?\\s*"
                    + "(?:class|interface|struct|enum|func|(?:[A-Za-z_\\d<>,]+\\s+))(?!if|for|while|switch)([A-Za-z_]\\w*)\\s*\\("
    );
    /** import / require / use 语句 */
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "^(?:import\\s+.*?\\s+from\\s+['\\\"]([^'\\\"]+)['\\\"]|"
                    + "import\\s+['\\\"]([^'\\\"]+)['\\\"]|"
                    + "const\\s+\\w+\\s*=\\s*require\\(['\\\"]([^'\\\"]+)['\\\"]\\)|"
                    + "from\\s+([A-Za-z_\\w.]+)\\s+import|"
                    + "use\\s+([A-Za-z_\\w:]+);|"
                    + "import\\s+([A-Za-z_\\w.]+);)"
    );

    @Override
    public String name() {
        return "fs_symbols";
    }

    @Override
    public String description() {
        return "扫描工作区源码，抽取每个文件导出的函数/类/接口与 import 依赖，用于理解项目结构与跨文件引用关系。只读。";
    }

    @Override
    public JsonNode parameters() {
        com.fasterxml.jackson.databind.node.ObjectNode root = objectMapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ObjectNode props = objectMapper.createObjectNode();
        props.putObject("path").put("type", "string")
                .put("description", "可选，限定扫描的子目录或文件，相对工作区；省略则扫描整个工作区");
        props.putObject("maxDepth").put("type", "integer")
                .put("description", "最大扫描深度，默认 4，范围 1~10");
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
        String subPath = args.has("path") ? args.get("path").asText() : "";

        Path root = FsReadTool.resolveRoot(ctx);
        Path scanRoot = root;
        if (subPath != null && !subPath.isBlank()) {
            scanRoot = FsReadTool.safeResolve(root, subPath);
        }
        if (!Files.exists(scanRoot)) {
            return ToolResult.fail("路径不存在：" + subPath, System.currentTimeMillis() - start);
        }
        try {
            StringBuilder sb = new StringBuilder();
            int[] fileCount = {0};
            collect(sb, root, scanRoot, 0, maxDepth, fileCount);
            if (fileCount[0] == 0) {
                return ToolResult.ok("(未扫描到源码文件)", System.currentTimeMillis() - start);
            }
            sb.insert(0, "符号索引（共 " + fileCount[0] + " 个文件）：\n");
            return ToolResult.ok(sb.toString(), System.currentTimeMillis() - start);
        } catch (IOException e) {
            throw new ToolException("扫描符号失败：" + e.getMessage(), e);
        }
    }

    private void collect(StringBuilder sb, Path root, Path current, int depth, int maxDepth, int[] fileCount)
            throws IOException {
        if (depth >= maxDepth || fileCount[0] >= MAX_FILES) return;
        List<Path> children;
        try (var stream = Files.list(current)) {
            children = stream
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .sorted(Comparator.comparing((Path p) -> Files.isDirectory(p) ? 0 : 1)
                            .thenComparing(p -> p.getFileName().toString().toLowerCase()))
                    .toList();
        }
        for (Path child : children) {
            if (fileCount[0] >= MAX_FILES) return;
            if (Files.isDirectory(child)) {
                collect(sb, root, child, depth + 1, maxDepth, fileCount);
            } else if (isSource(child)) {
                String rel = root.relativize(child).toString().replace('\\', '/');
                List<String> defs = new ArrayList<>();
                List<String> imports = new ArrayList<>();
                parse(child, defs, imports);
                if (!defs.isEmpty() || !imports.isEmpty()) {
                    sb.append("\n■ ").append(rel).append("\n");
                    if (!defs.isEmpty()) sb.append("  defs: ").append(String.join(", ", defs)).append("\n");
                    if (!imports.isEmpty()) sb.append("  imports: ").append(String.join(", ", imports)).append("\n");
                }
                fileCount[0]++;
            }
        }
    }

    private boolean isSource(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        return n.endsWith(".py") || n.endsWith(".js") || n.endsWith(".ts") || n.endsWith(".jsx")
                || n.endsWith(".tsx") || n.endsWith(".java") || n.endsWith(".go") || n.endsWith(".rs")
                || n.endsWith(".c") || n.endsWith(".cpp") || n.endsWith(".cc") || n.endsWith(".h")
                || n.endsWith(".hpp") || n.endsWith(".cs") || n.endsWith(".sql");
    }

    private void parse(Path file, List<String> defs, List<String> imports) throws IOException {
        List<String> lines = Files.readAllLines(file);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("#")
                    || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
                continue;
            }
            Matcher im = IMPORT_PATTERN.matcher(trimmed);
            if (im.find()) {
                String dep = im.group(1) != null ? im.group(1)
                        : im.group(2) != null ? im.group(2)
                        : im.group(3) != null ? im.group(3)
                        : im.group(4) != null ? im.group(4)
                        : im.group(5) != null ? im.group(5) : im.group(6);
                if (dep != null && !dep.isBlank()) imports.add(dep);
                continue;
            }
            Matcher dm = DEF_PATTERN.matcher(trimmed);
            if (dm.find()) {
                String name = null;
                for (int g = 1; g <= dm.groupCount(); g++) {
                    if (dm.group(g) != null) {
                        name = dm.group(g);
                        break;
                    }
                }
                if (name != null) defs.add(name + ":" + (i + 1));
            }
        }
    }
}
