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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 内置工具：版本控制集成（git，DANGEROUS，默认禁用，需显式开启）。
 * <p>在用户工作区根目录执行 git 子命令：status / diff / log / branch / commit。
 * 仅允许只读查询与 commit 两类动作；拒绝 push / force / reset --hard 等高危操作。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitTool implements AgentTool {

    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "git";
    }

    @Override
    public String description() {
        return "对工作区执行 git 操作（status/diff/log/branch/commit）。默认禁用，需显式开启；禁止 push/force/reset 等高危操作。";
    }

    @Override
    public JsonNode parameters() {
        com.fasterxml.jackson.databind.node.ObjectNode root = objectMapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ObjectNode props = objectMapper.createObjectNode();
        props.putObject("action").put("type", "string")
                .put("description", "status / diff / log / branch / commit");
        props.putObject("message").put("type", "string")
                .put("description", "action=commit 时的提交说明");
        props.putObject("files").put("type", "array")
                .put("description", "action=commit 时提交的文件（相对路径）；省略则 git add -A 全部");
        props.putObject("timeoutSec").put("type", "integer").put("description", "超时秒数，默认 30，上限 120");
        root.put("type", "object").set("properties", props);
        root.putArray("required").add("action");
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
        String action = args.has("action") ? args.get("action").asText() : null;
        if (action == null) {
            return ToolResult.fail("缺少必填参数 action", System.currentTimeMillis() - start);
        }
        Path root = FsReadTool.resolveRoot(ctx);
        long timeout = args.has("timeoutSec") ? args.get("timeoutSec").asLong(30) : 30;
        timeout = Math.max(1, Math.min(120, timeout)) * 1000L;

        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        switch (action) {
            case "status" -> cmd.add("status");
            case "diff" -> {
                cmd.add("diff");
                cmd.add("--stat");
            }
            case "log" -> {
                cmd.add("log");
                cmd.add("--oneline");
                cmd.add("-n");
                cmd.add("20");
            }
            case "branch" -> {
                cmd.add("branch");
                cmd.add("-a");
            }
            case "commit" -> {
                String message = args.has("message") ? args.get("message").asText() : null;
                if (message == null || message.isBlank()) {
                    return ToolResult.fail("commit 需要 message", System.currentTimeMillis() - start);
                }
                // 暂存文件：指定则 add 指定文件，否则全部；随后提交
                List<String> addCmd = new ArrayList<>();
                addCmd.add("git");
                if (args.has("files") && args.get("files").isArray() && args.get("files").size() > 0) {
                    for (JsonNode f : args.get("files")) {
                        addCmd.add("add");
                        addCmd.add(f.asText());
                    }
                } else {
                    addCmd.add("add");
                    addCmd.add("-A");
                }
                runGit(root, addCmd, timeout);
                cmd.add("commit");
                cmd.add("-m");
                cmd.add(message);
            }
            default -> {
                // 高危动作统一拒绝
                if (isDangerous(action)) {
                    return ToolResult.fail("禁止的 git 动作：" + action, System.currentTimeMillis() - start);
                }
                cmd.add("status");
            }
        }

        Captured cap = runGit(root, cmd, timeout);
        String out = (cap.stdout == null ? "" : cap.stdout) + (cap.stderr == null ? "" : "\n" + cap.stderr);
        if (cap.timedOut) out += "\n...[git 超时]";
        return ToolResult.ok("git " + action + " (exit=" + cap.exitCode + "):\n" + out, System.currentTimeMillis() - start);
    }

    private boolean isDangerous(String action) {
        return switch (action) {
            case "push", "pull", "fetch", "reset", "checkout", "rebase", "merge", "clean", "revert", "tag", "stash" -> true;
            default -> false;
        };
    }

    private Captured runGit(Path root, List<String> cmd, long timeoutMs) {
        Captured cap = new Captured();
        long start = System.currentTimeMillis();
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(root.toFile());
            pb.redirectErrorStream(false);
            Process p = pb.start();
            StreamReader outR = new StreamReader(p.getInputStream());
            StreamReader errR = new StreamReader(p.getErrorStream());
            outR.start();
            errR.start();
            boolean finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
                p.waitFor(2000, TimeUnit.MILLISECONDS);
                cap.timedOut = true;
            } else {
                cap.exitCode = p.exitValue();
            }
            outR.join(3000);
            errR.join(3000);
            cap.stdout = outR.getCaptured();
            cap.stderr = errR.getCaptured();
        } catch (Exception e) {
            cap.stderr = e.getMessage();
        }
        cap.timeUsedMs = System.currentTimeMillis() - start;
        return cap;
    }

    private static class Captured {
        String stdout = "";
        String stderr = "";
        int exitCode = -1;
        boolean timedOut = false;
        long timeUsedMs = 0;
    }

    private static class StreamReader extends Thread {
        private final InputStream in;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

        StreamReader(InputStream in) {
            this.in = in;
        }

        @Override
        public void run() {
            byte[] chunk = new byte[4096];
            try {
                int n;
                while ((n = in.read(chunk)) != -1) {
                    buf.write(chunk, 0, n);
                }
            } catch (java.io.IOException ignored) {
            }
        }

        String getCaptured() {
            return buf.toString(StandardCharsets.UTF_8);
        }
    }
}
