package com.knowflow.service.impl;

import com.knowflow.dto.CodeDebugRequest;
import com.knowflow.dto.CodeDebugResult;
import com.knowflow.dto.CodeRunRequest;
import com.knowflow.dto.CodeRunResult;
import com.knowflow.service.CodeDebugService;
import com.knowflow.service.CodeExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 轻量在线调试实现（推进 2.1「在线调试器」）。
 *
 * <p>能力边界（诚实说明）：
 * <ul>
 *   <li><b>Python</b>：基于 {@code sys.settrace} 实现真正的<b>逐行执行追踪</b>，采集每行执行后的局部变量快照；</li>
 *   <li><b>JavaScript / Java / C++</b>：提供<b>错误行号精确定位</b>（解析 traceback / 编译器报错中的行号），
 *       复用 {@link CodeExecutionService} 真实运行，错误时定位到用户代码对应行；</li>
 *   <li>暂不实现断点打断 / 单步步入步过 / 远程调试协议（DAP）—— 属后续增强路线。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeDebugServiceImpl implements CodeDebugService {

    private final CodeExecutionService codeExecutionService;

    @Value("${code.execution.python-bin:python3}")
    private String pythonBin;

    @Value("${code.execution.time-limit-ms:10000}")
    private long defaultTimeLimitMs;

    @Value("${code.execution.output-limit-bytes:1048576}")
    private int outputLimitBytes;

    private static final Pattern LINE_IN_TRACE = Pattern.compile("line (\\d+)");
    private static final Pattern FILE_LINE = Pattern.compile("\\.(py|js|java|cpp):(\\d+)");

    @Override
    public CodeDebugResult debug(CodeDebugRequest request, Long userId) {
        String language = request.getLanguage() == null ? "" : request.getLanguage().trim().toLowerCase();
        String code = request.getCode() == null ? "" : request.getCode();
        long timeLimit = (request.getTimeLimitMs() != null && request.getTimeLimitMs() > 0)
                ? request.getTimeLimitMs() : defaultTimeLimitMs;

        if (code.isBlank()) {
            return CodeDebugResult.builder().status("ERROR").error("代码为空").output("").build();
        }

        if ("python".equals(language) || "py".equals(language)) {
            return debugPython(code, timeLimit);
        }

        // 其他语言：复用真实执行引擎，再解析错误行号
        CodeRunRequest runReq = new CodeRunRequest();
        runReq.setLanguage(language);
        runReq.setCode(code);
        runReq.setTimeLimitMs(timeLimit);
        CodeRunResult r = codeExecutionService.execute(runReq, userId);

        Integer errorLine = null;
        if (!CodeRunResult.Status.SUCCESS.equals(r.getStatus()) && r.getError() != null) {
            errorLine = parseErrorLine(language, r.getError(), 0);
        }
        String status = CodeRunResult.Status.TIMEOUT.equals(r.getStatus()) ? "TIMEOUT"
                : CodeRunResult.Status.SUCCESS.equals(r.getStatus()) ? "SUCCESS" : "ERROR";
        return CodeDebugResult.builder()
                .status(status)
                .trace(new ArrayList<>())
                .errorLine(errorLine)
                .error(r.getError())
                .output(r.getOutput())
                .timeUsedMs(r.getTimeUsedMs())
                .build();
    }

    // ===== Python 逐行追踪 =====

    // ===== Python 逐行追踪（基于 AST 插桩，环境无关，不依赖 sys.settrace）=====
    // 说明：本沙箱环境（及部分宿主终端）的 Python 中 sys.settrace 的 C 级派发被禁用，
    // 追踪函数注册后永不回调。因此改用 AST 在每条语句前注入 __kb_trace(行号, 局部变量快照)，
    // 该方式在任何标准 CPython 上均稳定可用。

    private static final String PY_DEBUG_WRAPPER =
            "import ast, json, sys\n"
            + "\n"
            + "__kb_steps = []\n"
            + "def __kb_trace(__ln, __locs):\n"
            + "    try:\n"
            + "        __v = {k: repr(val) for k, val in __locs.items() if not k.startswith('__') and k not in ('__kb_trace','__kb_steps')}\n"
            + "    except Exception:\n"
            + "        __v = {}\n"
            + "    __kb_steps.append({'line': __ln, 'event': 'line', 'vars': __v})\n"
            + "\n"
            + "def instrument(src):\n"
            + "    tree = ast.parse(src)\n"
            + "    def te(ln):\n"
            + "        return ast.Expr(value=ast.Call(\n"
            + "            func=ast.Name('__kb_trace', ctx=ast.Load()),\n"
            + "            args=[ast.Constant(ln), ast.Call(func=ast.Name('dict', ctx=ast.Load()), args=[ast.Call(func=ast.Name('locals', ctx=ast.Load()), args=[], keywords=[])], keywords=[])],\n"
            + "            keywords=[]))\n"
            + "    STMT=['body','orelse','finalbody']\n"
            + "    class T(ast.NodeTransformer):\n"
            + "        def visit(self, node):\n"
            + "            node=self.generic_visit(node)\n"
            + "            for f in STMT:\n"
            + "                old=getattr(node,f,None)\n"
            + "                if isinstance(old,list):\n"
            + "                    new=[]\n"
            + "                    for s in old:\n"
            + "                        if isinstance(s,ast.stmt):\n"
            + "                            ln=getattr(s,'lineno',None)\n"
            + "                            if ln is not None:\n"
            + "                                new.append(te(ln))\n"
            + "                        new.append(s)\n"
            + "                    setattr(node,f,new)\n"
            + "            return node\n"
            + "    T().visit(tree)\n"
            + "    ast.fix_missing_locations(tree)\n"
            + "    return compile(tree, '<user>', 'exec')\n"
            + "\n"
            + "src = open(sys.argv[1]).read()\n"
            + "namespace = {'__kb_trace': __kb_trace, '__kb_steps': __kb_steps}\n"
            + "__err = None\n"
            + "try:\n"
            + "    code = instrument(src)\n"
            + "    exec(code, namespace)\n"
            + "except SyntaxError as e:\n"
            + "    __err = 'SyntaxError: ' + str(e) + '\\n' + (e.text or '')\n"
            + "except Exception:\n"
            + "    import traceback as __tb\n"
            + "    __err = __tb.format_exc()\n"
            + "__safe=[]\n"
            + "for s in __kb_steps:\n"
            + "    try:\n"
            + "        v = json.dumps(s['vars'], ensure_ascii=False)\n"
            + "    except Exception:\n"
            + "        v='{}'\n"
            + "    __safe.append({'line': s['line'], 'event': s['event'], 'vars': v})\n"
            + "if __err is not None:\n"
            + "    sys.stderr.write(__err)\n"
            + "sys.stderr.write('__KBTRACE__' + json.dumps(__safe, ensure_ascii=False) + '\\n')\n"
            + "if __err is not None:\n"
            + "    sys.exit(1)\n";

    private CodeDebugResult debugPython(String code, long timeLimit) {
        Path userFile = null;
        Path wrapperFile = null;
        try {
            userFile = Files.createTempFile("knowflow-dbg-user-", ".py");
            Files.writeString(userFile, code, StandardCharsets.UTF_8);
            wrapperFile = Files.createTempFile("knowflow-dbg-wrap-", ".py");
            Files.writeString(wrapperFile, PY_DEBUG_WRAPPER, StandardCharsets.UTF_8);

            Captured cap = runCapture(
                    List.of(pythonBin, wrapperFile.toString(), userFile.toString()), timeLimit);
            int userLineCount = code.split("\n", -1).length;

            // stderr 结构：<用户代码 traceback（若有）> + "__KBTRACE__" + <逐行轨迹 JSON>
            String stderrAll = cap.stderr == null ? "" : cap.stderr;
            int kbIdx = stderrAll.indexOf("__KBTRACE__");
            String tracebackPart = stderrAll;
            String jsonPart = "[]";
            if (kbIdx >= 0) {
                tracebackPart = stderrAll.substring(0, kbIdx);
                jsonPart = stderrAll.substring(kbIdx + "__KBTRACE__".length()).trim();
            }

            Integer errorLine = null;
            if (cap.timedOut) {
                errorLine = null;
            } else if (cap.exitCode != 0 && !tracebackPart.isBlank()) {
                // 用户代码直接运行，traceback 行号即用户视角，无需前缀偏移
                errorLine = parseErrorLine("python", tracebackPart, 0);
            }

            List<CodeDebugResult.TraceStep> trace = parseTrace(jsonPart, 0, userLineCount);
            String status = cap.timedOut ? "TIMEOUT" : (cap.exitCode == 0 ? "SUCCESS" : "ERROR");
            String errorMsg = cap.timedOut ? "执行超时（超过 " + timeLimit + "ms），已强制终止"
                    : (cap.exitCode != 0 ? tracebackPart : null);
            return CodeDebugResult.builder()
                    .status(status)
                    .trace(trace)
                    .errorLine(errorLine)
                    .error(errorMsg)
                    .output(cap.stdout)
                    .timeUsedMs(cap.timeUsedMs)
                    .build();
        } catch (Exception e) {
            log.error("[Debug] Python 追踪异常", e);
            return CodeDebugResult.builder().status("ERROR").error("调试执行异常：" + e.getMessage()).build();
        } finally {
            if (userFile != null) {
                try { Files.deleteIfExists(userFile); } catch (Exception ignored) { }
            }
            if (wrapperFile != null) {
                try { Files.deleteIfExists(wrapperFile); } catch (Exception ignored) { }
            }
        }
    }


    // ===== 解析与执行辅助 =====

    /** 解析 trace JSON 数组（Jackson），转换为用户视角（1-based）的逐行步骤，过滤出界步骤 */
    private static final ObjectMapper TRACE_MAPPER = new ObjectMapper();

    private List<CodeDebugResult.TraceStep> parseTrace(String json, int prefixLines, int userLineCount) {
        List<CodeDebugResult.TraceStep> out = new ArrayList<>();
        try {
            JsonNode arr = TRACE_MAPPER.readTree(json);
            if (!arr.isArray()) {
                return out;
            }
            for (JsonNode n : arr) {
                int absLine = n.path("line").asInt();
                int userLine = absLine - prefixLines;
                if (userLine < 1 || userLine > userLineCount) {
                    continue;
                }
                String event = n.path("event").asText("line");
                String vars = n.path("vars").asText("");
                out.add(CodeDebugResult.TraceStep.builder()
                        .line(userLine).event(event).vars(vars).build());
            }
        } catch (Exception e) {
            log.warn("[Debug] trace 解析失败：{}", e.getMessage());
        }
        return out;
    }

    /** 从错误文本提取用户代码出错行号（1-based，已减去前缀偏移） */
    private Integer parseErrorLine(String language, String error, int prefixLines) {
        if (error == null) {
            return null;
        }
        Integer result = null;
        if ("python".equals(language) || "py".equals(language)) {
            // Python traceback: "... line N, in ..." 取最后一个（最底层）
            Matcher m = LINE_IN_TRACE.matcher(error);
            while (m.find()) {
                result = Integer.parseInt(m.group(1)) - prefixLines;
            }
        } else {
            // JS/Java/C++: 文件名:行 格式
            Matcher m = FILE_LINE.matcher(error);
            if (m.find()) {
                result = Integer.parseInt(m.group(2));
            }
        }
        return (result != null && result >= 1) ? result : null;
    }

    /** 执行进程并捕获 stdout/stderr，超时强杀，输出截断 */
    private Captured runCapture(List<String> cmd, long timeLimitMs) {
        Captured cap = new Captured();
        long start = System.currentTimeMillis();
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);
            Process process = pb.start();
            StreamReader outR = new StreamReader(process.getInputStream(), outputLimitBytes);
            StreamReader errR = new StreamReader(process.getErrorStream(), outputLimitBytes);
            outR.start();
            errR.start();
            boolean finished = process.waitFor(timeLimitMs, TimeUnit.MILLISECONDS);
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
        } catch (Exception e) {
            cap.stderr = (cap.stderr == null ? "" : cap.stderr) + e.getMessage();
            cap.timeUsedMs = System.currentTimeMillis() - start;
        }
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
            } catch (java.io.IOException ignored) {
            }
        }

        String getCaptured() {
            String s = buffer.toString(StandardCharsets.UTF_8);
            return truncated ? s + "\n...[输出超过 " + limitBytes + " 字节，已截断]" : s;
        }
    }
}
