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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 内置工具：受控终端命令执行（DANGEROUS，默认禁用，需在工具面板显式开启）。
 * <p>在用户工作区根目录执行 shell 命令（构建 / 安装 / 运行），用于调试与工程化操作。
 * 安全措施：命令白名单 + 危险模式黑名单 + 超时强杀 + 路径穿越防护（工作区根）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TerminalTool implements AgentTool {

    private final ObjectMapper objectMapper;

    /** 允许的命令前缀（base command） */
    private static final List<String> ALLOWED = Arrays.asList(
            "npm", "npx", "pip", "pip3", "python", "python3", "node", "java", "mvn", "gradle",
            "go", "cargo", "make", "ls", "cat", "grep", "git", "tsc", "jest", "pytest",
            "php", "ruby", "dotnet", "clang", "gcc", "g++", "pnpm", "yarn", "bun", "echo", "pwd");
    /** 危险模式黑名单（命中即拒绝） */
    private static final List<Pattern> DENY = Arrays.asList(
            Pattern.compile("\\brm\\s+(-[a-zA-Z]*r|-{2}recursive)"),
            Pattern.compile("\\bsudo\\b"),
            Pattern.compile("\\bmkfs\\b"),
            Pattern.compile(":\\(\\)\\s*\\{"),
            Pattern.compile("\\bdd\\b"),
            Pattern.compile("\\bcurl\\b[^|]*\\|\\s*(sh|bash)"),
            Pattern.compile("\\bwget\\b[^|]*\\|\\s*(sh|bash)"),
            Pattern.compile("\\bshutdown\\b"), Pattern.compile("\\breboot\\b"),
            Pattern.compile("chmod\\s+-R"), Pattern.compile(">\\s*/dev/"),
            Pattern.compile("\\bmv\\b[^\\n]*\\s/\\s*$"));
    private static final long DEFAULT_TIMEOUT = 60_000L;

    @Override
    public String name() {
        return "terminal";
    }

    @Override
    public String description() {
        return "在用户工作区执行受控 shell 命令（构建/安装/运行/调试）。默认禁用，需显式开启；仅白名单命令，超时强杀。";
    }

    @Override
    public JsonNode parameters() {
        com.fasterxml.jackson.databind.node.ObjectNode root = objectMapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ObjectNode props = objectMapper.createObjectNode();
        props.putObject("command").put("type", "string").put("description", "要执行的 shell 命令（白名单内）");
        props.putObject("cwd").put("type", "string").put("description", "相对工作区的子目录，省略则为工作区根");
        props.putObject("timeoutSec").put("type", "integer").put("description", "超时秒数，默认 60，上限 300");
        root.put("type", "object").set("properties", props);
        root.putArray("required").add("command");
        return root;
    }

    @Override
    public boolean enabledByDefault() {
        return false;
    }

    @Override
    public ToolPermission permission() {
        return ToolPermission.DANGEROUS;
    }

    @Override
    public ToolResult execute(JsonNode args, ToolContext ctx) throws ToolException {
        long start = System.currentTimeMillis();
        String command = args.has("command") ? args.get("command").asText() : null;
        if (command == null || command.isBlank()) {
            return ToolResult.fail("缺少必填参数 command", System.currentTimeMillis() - start);
        }
        // 危险模式拦截
        for (Pattern p : DENY) {
            if (p.matcher(command).find()) {
                return ToolResult.fail("命令被安全策略拒绝：" + command, System.currentTimeMillis() - start);
            }
        }
        // 基础命令白名单
        String base = command.trim().split("\\s+")[0].replaceAll("^[./]", "");
        if (!ALLOWED.contains(base)) {
            return ToolResult.fail("命令不在白名单：" + base, System.currentTimeMillis() - start);
        }
        long timeout = args.has("timeoutSec") ? args.get("timeoutSec").asLong(60) : 60;
        timeout = Math.max(1, Math.min(300, timeout)) * 1000L;

        Path root = FsReadTool.resolveRoot(ctx);
        Path workdir = root;
        if (args.has("cwd") && !args.get("cwd").asText().isBlank()) {
            workdir = FsReadTool.safeResolve(root, args.get("cwd").asText());
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
            pb.directory(workdir.toFile());
            pb.redirectErrorStream(false);
            Process process = pb.start();
            Captured cap = run(process, timeout, 1_048_576);
            String out = (cap.stdout == null ? "" : cap.stdout)
                    + (cap.stderr == null ? "" : "\n[stderr]\n" + cap.stderr);
            if (cap.truncated) out += "\n...[输出超过 1MB，已截断]";
            String result = "exitCode=" + cap.exitCode + (cap.timedOut ? " (超时)" : "")
                    + "\n--- output ---\n" + out;
            return ToolResult.ok(result, System.currentTimeMillis() - start);
        } catch (Exception e) {
            throw new ToolException("命令执行失败：" + e.getMessage(), e);
        }
    }

    private Captured run(Process process, long timeoutMs, int limitBytes) throws InterruptedException {
        Captured cap = new Captured();
        long start = System.currentTimeMillis();
        StreamReader outR = new StreamReader(process.getInputStream(), limitBytes);
        StreamReader errR = new StreamReader(process.getErrorStream(), limitBytes);
        outR.start();
        errR.start();
        boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(2000, TimeUnit.MILLISECONDS);
            cap.timedOut = true;
        } else {
            cap.exitCode = process.exitValue();
        }
        outR.join(3000);
        errR.join(3000);
        cap.stdout = outR.getCaptured();
        cap.stderr = errR.getCaptured();
        cap.timeUsedMs = System.currentTimeMillis() - start;
        return cap;
    }

    private static class Captured {
        String stdout = "";
        String stderr = "";
        int exitCode = -1;
        boolean timedOut = false;
        boolean truncated = false;
        long timeUsedMs = 0;
    }

    private static class StreamReader extends Thread {
        private final InputStream in;
        private final int limit;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        private boolean truncated = false;
        private final byte[] chunk = new byte[4096];

        StreamReader(InputStream in, int limit) {
            this.in = in;
            this.limit = limit;
        }

        @Override
        public void run() {
            try {
                int n;
                while ((n = in.read(chunk)) != -1) {
                    if (buf.size() + n > limit) {
                        buf.write(chunk, 0, limit - buf.size());
                        truncated = true;
                        break;
                    }
                    buf.write(chunk, 0, n);
                }
            } catch (java.io.IOException ignored) {
            }
        }

        String getCaptured() {
            String s = buf.toString(StandardCharsets.UTF_8);
            return truncated ? s + "\n...[输出已截断]" : s;
        }
    }
}
