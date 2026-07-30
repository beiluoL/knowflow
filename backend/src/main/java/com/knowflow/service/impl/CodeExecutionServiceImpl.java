package com.knowflow.service.impl;

import com.knowflow.dto.CodeRunRequest;
import com.knowflow.dto.CodeRunResult;
import com.knowflow.service.CodeExecutionService;
import com.knowflow.service.CodeWorkspaceService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 代码执行引擎实现：基于 {@link ProcessBuilder} 调起系统语言运行时真运行用户代码。
 *
 * <p>安全与资源控制（地基版，适用于本地 / 受信任内网学习环境）：
 * <ul>
 *   <li>每次执行在独立临时目录中进行，结束统一清理；</li>
 *   <li>超时由看门狗线程 {@code process.destroyForcibly()} 强制终止；</li>
 *   <li>并发通过信号量限流，避免资源耗尽；</li>
 *   <li>标准输出 / 错误按字节上限截断。</li>
 * </ul>
 *
 * <p>生产环境建议替换为 Judge0 / Docker 隔离沙箱（PRD 5.3），以获得网络隔离、
 * 内存 / 进程数硬限制与多租户隔离。本实现提供可平滑替换的接口契约。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeExecutionServiceImpl implements CodeExecutionService {

    private final CodeWorkspaceService workspaceService;

    @Value("${code.execution.time-limit-ms:10000}")
    private long defaultTimeLimitMs;

    @Value("${code.execution.output-limit-bytes:1048576}")
    private int outputLimitBytes;

    @Value("${code.execution.max-concurrency:8}")
    private int maxConcurrency;

    @Value("${code.execution.python-bin:python3}")
    private String pythonBin;

    @Value("${code.execution.node-bin:node}")
    private String nodeBin;

    @Value("${code.execution.java-bin:java}")
    private String javaBin;

    @Value("${code.execution.javac-bin:javac}")
    private String javacBin;

    @Value("${code.execution.gpp-bin:g++}")
    private String gppBin;

    private Semaphore semaphore;

    private static final Pattern PUBLIC_CLASS = Pattern.compile("public\\s+class\\s+(\\w+)");

    @PostConstruct
    void init() {
        // Semaphore 容量一经创建不可变，故在 @PostConstruct 用注入后的配置值新建
        this.semaphore = new Semaphore(Math.max(1, maxConcurrency));
        log.info("[CodeExecution] 沙箱初始化完成：timeLimit={}ms, outputLimit={}B, maxConcurrency={}",
                defaultTimeLimitMs, outputLimitBytes, maxConcurrency);
    }

    private enum Lang {
        PYTHON, JAVASCRIPT, JAVA, CPP, UNSUPPORTED
    }

    @Override
    public CodeRunResult execute(CodeRunRequest request, Long userId) {
        CodeRunResult result = new CodeRunResult();
        Path workDir = null;
        boolean persistent = false;
        try {
            Lang lang = resolveLang(request.getLanguage());
            if (lang == Lang.UNSUPPORTED) {
                return rejected(result, "不支持的语言：" + request.getLanguage()
                        + "（支持 python / java / javascript / cpp）");
            }

            semaphore.acquire();
            try {
                long timeLimit = (request.getTimeLimitMs() != null && request.getTimeLimitMs() > 0)
                        ? request.getTimeLimitMs() : defaultTimeLimitMs;

                // ===== 工作区模式（SC1-IDE-02）：运行用户持久目录中的入口文件 =====
                if (Boolean.TRUE.equals(request.getWorkspace())) {
                    if (userId == null) {
                        return rejected(result, "工作区模式需要登录");
                    }
                    String entry = request.getEntryFile();
                    if (entry == null || entry.isBlank()) {
                        return rejected(result, "未指定工作区入口文件 entryFile");
                    }
                    Path wsDir = workspaceService.getWorkspaceDir(userId);
                    Path srcFile = wsDir.resolve(entry).normalize();
                    if (!srcFile.startsWith(wsDir) || !Files.exists(srcFile) || !Files.isRegularFile(srcFile)) {
                        return rejected(result, "工作区中找不到文件：" + entry);
                    }
                    workDir = wsDir;
                    persistent = true; // 持久目录，执行后不清理
                    switch (lang) {
                        case PYTHON -> runSimpleFile(pythonBin, srcFile, request, workDir, timeLimit, result);
                        case JAVASCRIPT -> runSimpleFile(nodeBin, srcFile, request, workDir, timeLimit, result);
                        case JAVA -> runJavaFile(srcFile, request, workDir, timeLimit, result);
                        case CPP -> runCppFile(srcFile, request, workDir, timeLimit, result);
                        default -> rejected(result, "暂未支持该语言");
                    }
                    return result;
                }

                // ===== 单文件模式：写入临时目录执行，结束清理 =====
                if (request.getCode() == null || request.getCode().trim().isEmpty()) {
                    return rejected(result, "代码内容为空");
                }
                workDir = Files.createTempDirectory("knowflow-code-");
                switch (lang) {
                    case PYTHON -> runSimple(pythonBin, ".py", request, workDir, timeLimit, result);
                    case JAVASCRIPT -> runSimple(nodeBin, ".js", request, workDir, timeLimit, result);
                    case JAVA -> runJava(request, workDir, timeLimit, result);
                    case CPP -> runCpp(request, workDir, timeLimit, result);
                    default -> rejected(result, "暂未支持该语言");
                }
            } finally {
                semaphore.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return rejected(result, "执行被中断");
        } catch (Exception e) {
            log.error("[CodeExecution] 执行异常", e);
            return rejected(result, "服务器执行异常：" + e.getMessage());
        } finally {
            if (workDir != null && !persistent) {
                deleteRecursively(workDir);
            }
        }
        return result;
    }

    // ===== 各语言执行 =====

    /** 解释型语言（Python / JS）：写入源文件后运行 */
    private void runSimple(String bin, String ext, CodeRunRequest request, Path workDir,
                           long timeLimit, CodeRunResult result) throws IOException, InterruptedException {
        Path src = workDir.resolve("solution" + ext);
        Files.writeString(src, request.getCode(), StandardCharsets.UTF_8);
        runSimpleFile(bin, src, request, workDir, timeLimit, result);
    }

    /** 解释型语言（Python / JS）：直接运行已存在的源文件（工作区模式复用） */
    private void runSimpleFile(String bin, Path src, CodeRunRequest request, Path workDir,
                               long timeLimit, CodeRunResult result) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add(bin);
        cmd.add(src.getFileName().toString());
        ExecOutcome out = executeCommand(cmd, workDir, timeLimit, request.getStdin());
        applyOutcome(out, timeLimit, result);
    }

    /** 从源代码中提取 public 类名（用于单文件模式命名 .java 文件） */
    private String javaClassNameFromCode(String code) {
        Matcher m = PUBLIC_CLASS.matcher(code);
        return m.find() ? m.group(1) : "Main";
    }

    /** Java：先 javac 编译，再 java 运行（单文件模式：按类名写文件） */
    private void runJava(CodeRunRequest request, Path workDir, long timeLimit, CodeRunResult result)
            throws IOException, InterruptedException {
        String className = javaClassNameFromCode(request.getCode());
        Path src = workDir.resolve(className + ".java");
        Files.writeString(src, request.getCode(), StandardCharsets.UTF_8);
        runJavaFile(src, request, workDir, timeLimit, result);
    }

    /** Java：运行工作区中已存在的 .java 文件（类名取自文件名） */
    private void runJavaFile(Path src, CodeRunRequest request, Path workDir, long timeLimit, CodeRunResult result)
            throws IOException, InterruptedException {
        String fileName = src.getFileName().toString();
        String className = fileName.endsWith(".java") ? fileName.substring(0, fileName.length() - 5) : "Main";

        List<String> compileCmd = Arrays.asList(javacBin, "-d", workDir.toString(), src.getFileName().toString());
        ExecOutcome compile = executeCommand(compileCmd, workDir, Math.max(5000, timeLimit), null);
        if (compile.timedOut) {
            result.setStatus(CodeRunResult.Status.TIMEOUT);
            result.setError("编译超时");
            result.setTimeUsedMs(compile.elapsedMs);
            return;
        }
        if (compile.exitCode != 0) {
            result.setStatus(CodeRunResult.Status.COMPILE_ERROR);
            result.setError(pickError(compile));
            result.setTimeUsedMs(compile.elapsedMs);
            return;
        }
        List<String> runCmd = Arrays.asList(javaBin, "-cp", workDir.toString(), className);
        ExecOutcome run = executeCommand(runCmd, workDir, timeLimit, request.getStdin());
        applyOutcome(run, timeLimit, result);
    }

    /** C++：先 g++ 编译，再运行可执行文件（单文件模式：写 solution.cpp） */
    private void runCpp(CodeRunRequest request, Path workDir, long timeLimit, CodeRunResult result)
            throws IOException, InterruptedException {
        Path src = workDir.resolve("solution.cpp");
        Files.writeString(src, request.getCode(), StandardCharsets.UTF_8);
        runCppFile(src, request, workDir, timeLimit, result);
    }

    /** C++：运行工作区中已存在的 .cpp 文件 */
    private void runCppFile(Path src, CodeRunRequest request, Path workDir, long timeLimit, CodeRunResult result)
            throws IOException, InterruptedException {
        Path bin = workDir.resolve("solution.bin");

        List<String> compileCmd = Arrays.asList(gppBin, "-O2", "-o", bin.toString(), src.toString());
        ExecOutcome compile = executeCommand(compileCmd, workDir, Math.max(5000, timeLimit), null);
        if (compile.timedOut) {
            result.setStatus(CodeRunResult.Status.TIMEOUT);
            result.setError("编译超时");
            result.setTimeUsedMs(compile.elapsedMs);
            return;
        }
        if (compile.exitCode != 0) {
            result.setStatus(CodeRunResult.Status.COMPILE_ERROR);
            result.setError(pickError(compile));
            result.setTimeUsedMs(compile.elapsedMs);
            return;
        }
        List<String> runCmd = Arrays.asList(bin.toString());
        ExecOutcome run = executeCommand(runCmd, workDir, timeLimit, request.getStdin());
        applyOutcome(run, timeLimit, result);
    }

    // ===== 底层执行与结果处理 =====

    /** 启动子进程，喂入 stdin，捕获 stdout/stderr，超时强杀，返回结构化结果 */
    private ExecOutcome executeCommand(List<String> cmd, Path workDir, long timeLimitMs, String stdin)
            throws IOException, InterruptedException {
        ExecOutcome out = new ExecOutcome();
        long start = System.currentTimeMillis();

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(false);
        // 继承当前环境（PATH/JAVA_HOME 等），保证各运行时可被定位
        Process process = pb.start();

        // 写入标准输入
        if (stdin != null && !stdin.isEmpty()) {
            try (OutputStream os = process.getOutputStream()) {
                os.write(stdin.getBytes(StandardCharsets.UTF_8));
            }
        } else {
            process.getOutputStream().close();
        }

        StreamReader stdoutReader = new StreamReader(process.getInputStream(), outputLimitBytes);
        StreamReader stderrReader = new StreamReader(process.getErrorStream(), outputLimitBytes);
        stdoutReader.start();
        stderrReader.start();

        boolean finished = process.waitFor(timeLimitMs, TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(2000, TimeUnit.MILLISECONDS);
            out.timedOut = true;
        } else {
            out.exitCode = process.exitValue();
        }
        stdoutReader.join(3000);
        stderrReader.join(3000);
        out.stdout = stdoutReader.getCaptured();
        out.stderr = stderrReader.getCaptured();
        out.elapsedMs = System.currentTimeMillis() - start;
        return out;
    }

    /** 将底层执行结果映射为对外 CodeRunResult */
    private void applyOutcome(ExecOutcome out, long timeLimitMs, CodeRunResult result) {
        result.setTimeUsedMs(out.elapsedMs);
        if (out.timedOut) {
            result.setStatus(CodeRunResult.Status.TIMEOUT);
            result.setOutput(truncate(out.stdout));
            result.setError("执行超时（超过 " + timeLimitMs + "ms），已强制终止");
            result.setExitCode(-1);
            return;
        }
        result.setExitCode(out.exitCode);
        result.setOutput(truncate(out.stdout));
        if (out.exitCode == 0) {
            result.setStatus(CodeRunResult.Status.SUCCESS);
            result.setError(out.stderr.isEmpty() ? null : out.stderr);
        } else {
            result.setStatus(CodeRunResult.Status.RUNTIME_ERROR);
            result.setError(pickError(out));
        }
    }

    private String pickError(ExecOutcome out) {
        String s = out.stderr;
        if (s == null || s.isEmpty()) {
            s = out.stdout;
        }
        return truncate(s);
    }

    private String truncate(String s) {
        if (s == null) return null;
        if (s.length() > 20000) {
            return s.substring(0, 20000) + "\n...[输出过长已截断]";
        }
        return s;
    }

    private CodeRunResult rejected(CodeRunResult result, String message) {
        result.setStatus(CodeRunResult.Status.INTERNAL_ERROR);
        result.setError(message);
        result.setTimeUsedMs(0L);
        result.setExitCode(null);
        return result;
    }

    private Lang resolveLang(String language) {
        if (language == null) return Lang.UNSUPPORTED;
        return switch (language.trim().toLowerCase()) {
            case "python", "py" -> Lang.PYTHON;
            case "javascript", "js" -> Lang.JAVASCRIPT;
            case "java" -> Lang.JAVA;
            case "cpp", "c++", "cplusplus" -> Lang.CPP;
            default -> Lang.UNSUPPORTED;
        };
    }

    private void deleteRecursively(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (Exception e) {
            log.warn("[CodeExecution] 清理临时目录失败：{}", dir, e);
        }
    }

    /** 子进程执行结果（内部使用） */
    private static class ExecOutcome {
        String stdout = "";
        String stderr = "";
        int exitCode = -1;
        boolean timedOut = false;
        long elapsedMs = 0;
    }

    /** 带截断保护的流读取线程 */
    private static class StreamReader extends Thread {
        private final InputStream in;
        private final int limitBytes;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private boolean truncated = false;
        private final byte[] chunk = new byte[4096];

        StreamReader(InputStream in, int limitBytes) {
            this.in = in;
            this.limitBytes = limitBytes;
        }

        @Override
        public void run() {
            try {
                int n;
                while ((n = in.read(chunk)) != -1) {
                    if (buffer.size() + n > limitBytes) {
                        int remain = limitBytes - buffer.size();
                        if (remain > 0) {
                            buffer.write(chunk, 0, remain);
                        }
                        truncated = true;
                        break;
                    }
                    buffer.write(chunk, 0, n);
                }
            } catch (IOException ignored) {
                // 进程终止后流关闭属正常
            }
        }

        String getCaptured() {
            String s = buffer.toString(StandardCharsets.UTF_8);
            return truncated ? s + "\n...[输出超过 " + limitBytes + " 字节，已截断]" : s;
        }
    }
}
