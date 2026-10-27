package com.knowflow.coding.judge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.knowflow.coding.entity.CodeProblem;
import com.knowflow.coding.entity.CodeTestCase;
import com.knowflow.security.HostCapabilityGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 宿主机本地 Java 判题器（javac + java 子进程）。
 *
 * <p><b>这不是 OS 沙箱</b>（见 CODE_EXECUTION_SECURITY_AUDIT / CODE_JUDGE_SECURITY_GATE）：
 * 仅在 {@code knowflow.judge.mode=local} 且 {@link HostCapabilityGuard}
 * 对当前登录用户显式授权时生效；缓解措施：私有 0700 临时目录、环境变量白名单、
 * 单 JVM 单用例超时强杀、堆上限、输出截断。仍缺少网络/namespace/nproc 隔离，
 * 不得对不可信多用户开放，不等同生产在线判题。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "knowflow.judge.mode", havingValue = "local")
public class LocalHostJavaJudge implements CodeJudgeGateway {

    private final HostCapabilityGuard hostGuard;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Set<PosixFilePermission> DIR_PERMS = PosixFilePermissions.fromString("rwx------");
    private static final int OUTPUT_LIMIT = 64 * 1024;

    private static final Pattern METHOD_SIG = Pattern.compile(
            "public\\s+(?:static\\s+)?([\\w]+(?:\\[\\])*)\\s+(\\w+)\\s*\\(([^)]*)\\)");

    @Override
    public JudgeVerdict judge(JudgeRequest request) {
        // 安全门禁：未显式授权的用户直接 403，由上层转 SYSTEM_ERROR/不可用，不执行任何命令。
        hostGuard.requireAccess();
        CodeProblem problem = request.problem();
        List<CodeTestCase> cases = request.effectiveCases();
        if (problem == null || problem.getFunctionSignature() == null) {
            return systemError("题目缺少函数签名，无法构建 Harness");
        }
        MethodSig sig;
        try {
            sig = parseSignature(problem.getFunctionSignature());
        } catch (IllegalArgumentException e) {
            return systemError("函数签名暂不支持：" + e.getMessage());
        }

        Path workDir = null;
        Process compile = null;
        try {
            workDir = createPrivateDir();
            Files.writeString(workDir.resolve("Solution.java"), request.sourceCode(), StandardCharsets.UTF_8);
            Files.writeString(workDir.resolve("Runner.java"), buildRunnerSource(sig), StandardCharsets.UTF_8);

            String javac = javaHomeBin("javac");
            ProcessBuilder cpb = new ProcessBuilder(javac, "-encoding", "UTF-8", "Solution.java", "Runner.java")
                    .directory(workDir.toFile());
            scrubEnvironment(cpb);
            long timeoutMs = problem.getTimeLimitMs() == null ? 1000 : problem.getTimeLimitMs();
            try (HostCapabilityGuard.ProcessLease lease = hostGuard.acquireProcess(timeoutMs + 5000)) {
                compile = cpb.start();
                boolean done = hostGuard.awaitProcess(compile, 30_000);
                if (!done) {
                    compile.destroyForcibly();
                    return systemError("编译超时（平台侧 30s 保护）");
                }
                if (compile.exitValue() != 0) {
                    String err = readStream(compile.getErrorStream().readAllBytes());
                    return new JudgeVerdict(JudgeStatus.COMPILE_ERROR, 0, 0, cases.size(),
                            null, null, truncate(err), List.of(), "LOCAL_HOST",
                            Map.of("sandbox", false, "phase", "COMPILE"));
                }

                List<CaseResult> results = new ArrayList<>();
                int passed = 0;
                int passedWeight = 0;
                int totalWeight = 0;
                int maxTime = 0;
                boolean aborted = false;
                String abortStatus = null;
                for (CodeTestCase tc : cases) {
                    int w = tc.getWeight() == null ? 1 : tc.getWeight();
                    totalWeight += w;
                    if (aborted) {
                        results.add(new CaseResult(isHidden(tc), tc.getOrderNo(), null, null, null,
                                false, 0, abortStatus));
                        continue;
                    }
                    CaseRun run = runCase(workDir, sig, tc, timeoutMs, problem.getMemoryLimitMb());
                    maxTime = Math.max(maxTime, run.timeMs);
                    results.add(run.result);
                    if (run.abort != null) {
                        aborted = true;
                        abortStatus = run.abort;
                        continue;
                    }
                    if (run.result.passed()) {
                        passed++;
                        passedWeight += w;
                    }
                }
                int score = totalWeight == 0 ? 0 : (int) Math.round(passedWeight * 100.0 / totalWeight);
                boolean allPass = passed == cases.size();
                String status = allPass ? JudgeStatus.ACCEPTED
                        : (abortStatus != null ? abortStatus : JudgeStatus.WRONG_ANSWER);
                return new JudgeVerdict(status, allPass ? score : 0, passed, cases.size(),
                        maxTime, null, null, results, "LOCAL_HOST",
                        Map.of("sandbox", false, "timeoutMs", timeoutMs));
            }
        } catch (SecurityException e) {
            return new JudgeVerdict(JudgeStatus.JUDGE_UNAVAILABLE, null, 0, cases.size(),
                    null, null, null, List.of(), "LOCAL_HOST", Map.of("denied", String.valueOf(e.getMessage())));
        } catch (Exception e) {
            log.warn("[Judge] 本地判题异常 questionId={}: {}", request.questionId(), e.getMessage());
            return systemError("判题执行异常：" + e.getClass().getSimpleName());
        } finally {
            if (compile != null && compile.isAlive()) {
                compile.descendants().forEach(ProcessHandle::destroyForcibly);
                compile.destroyForcibly();
            }
            if (workDir != null) {
                cleanup(workDir);
            }
        }
    }

    @Override
    public boolean executionEnabled() {
        return true;
    }

    @Override
    public String mode() {
        return "LOCAL_HOST";
    }

    // ==================== 单用例执行 ====================

    private record CaseRun(CaseResult result, String abort, int timeMs) {
    }

    private CaseRun runCase(Path workDir, MethodSig sig, CodeTestCase tc, long timeoutMs, Integer memMb)
            throws IOException, InterruptedException {
        JsonNode inputNode = objectMapper.readTree(tc.getInput());
        JsonNode expectedNode = objectMapper.readTree(tc.getExpectedOutput());
        String argsLiteral = buildArgsLiteral(sig, inputNode);
        // CaseCall 自带 main：编译依赖 Runner/Solution，运行入口为 CaseCall。
        String callSource = "final class CaseCall {\n"
                + "public static void main(String[] a) throws Exception {\n"
                + "Object[] args = new Object[]{" + argsLiteral + "};\n"
                + "Object ret = Runner.invoke(args);\n"
                + "System.out.println(\"CASE_RESULT:\" + Runner.toJson(ret));\n"
                + "} }\n";
        Files.writeString(workDir.resolve("CaseCall.java"), callSource, StandardCharsets.UTF_8);

        String javac = javaHomeBin("javac");
        ProcessBuilder cpb = new ProcessBuilder(javac, "-encoding", "UTF-8", "CaseCall.java")
                .directory(workDir.toFile());
        scrubEnvironment(cpb);
        Process cp = cpb.start();
        if (!hostGuard.awaitProcess(cp, 15_000)) {
            cp.destroyForcibly();
            return new CaseRun(hiddenResult(tc, JudgeStatus.SYSTEM_ERROR, null),
                    JudgeStatus.SYSTEM_ERROR, 0);
        }
        if (cp.exitValue() != 0) {
            return new CaseRun(hiddenResult(tc, JudgeStatus.SYSTEM_ERROR, truncate(readStream(cp.getErrorStream().readAllBytes()))),
                    JudgeStatus.SYSTEM_ERROR, 0);
        }

        String java = javaHomeBin("java");
        int heap = memMb == null || memMb <= 0 ? 256 : Math.min(memMb, 1024);
        ProcessBuilder rpb = new ProcessBuilder(java, "-Xmx" + heap + "m", "-XX:+UseSerialGC",
                "CaseCall", String.valueOf(tc.getOrderNo() == null ? 0 : tc.getOrderNo()))
                .directory(workDir.toFile());
        scrubEnvironment(rpb);
        long start = System.nanoTime();
        Process p = rpb.start();
        boolean done = hostGuard.awaitProcess(p, timeoutMs);
        int timeMs = (int) Math.max(1, (System.nanoTime() - start) / 1_000_000);
        if (!done) {
            p.descendants().forEach(ProcessHandle::destroyForcibly);
            p.destroyForcibly();
            return new CaseRun(hiddenResult(tc, JudgeStatus.TIME_LIMIT_EXCEEDED, null),
                    JudgeStatus.TIME_LIMIT_EXCEEDED, timeMs);
        }
        String stdout = readStream(p.getInputStream().readAllBytes());
        String stderr = readStream(p.getErrorStream().readAllBytes());
        if (p.exitValue() != 0) {
            String status = stderr.contains("OutOfMemoryError")
                    ? JudgeStatus.MEMORY_LIMIT_EXCEEDED : JudgeStatus.RUNTIME_ERROR;
            return new CaseRun(hiddenResult(tc, status, truncate(stderr)), status, timeMs);
        }
        JsonNode actualNode;
        try {
            String line = stdout.lines().filter(l -> l.startsWith("CASE_RESULT:")).findFirst()
                    .map(l -> l.substring("CASE_RESULT:".length())).orElseThrow();
            actualNode = objectMapper.readTree(line);
        } catch (Exception e) {
            return new CaseRun(hiddenResult(tc, JudgeStatus.SYSTEM_ERROR, truncate(stdout + "\n" + stderr)),
                    JudgeStatus.SYSTEM_ERROR, timeMs);
        }
        boolean pass = compare(expectedNode, actualNode);
        boolean hidden = isHidden(tc);
        CaseResult cr = new CaseResult(hidden, tc.getOrderNo(),
                hidden ? null : tc.getInput(), hidden ? null : tc.getExpectedOutput(),
                hidden ? null : truncate(objectMapper.writeValueAsString(actualNode)),
                pass, timeMs, pass ? JudgeStatus.ACCEPTED : JudgeStatus.WRONG_ANSWER);
        return new CaseRun(cr, pass ? null : JudgeStatus.WRONG_ANSWER, timeMs);
    }

    private CaseResult hiddenResult(CodeTestCase tc, String status, String actual) {
        boolean hidden = isHidden(tc);
        return new CaseResult(hidden, tc.getOrderNo(),
                hidden ? null : tc.getInput(), hidden ? null : tc.getExpectedOutput(),
                hidden ? null : actual, false, 0, status);
    }

    // ==================== 结果比较 ====================

    private boolean compare(JsonNode expected, JsonNode actual) {
        if (expected == null || actual == null) {
            return false;
        }
        JsonNodeType et = expected.getNodeType();
        JsonNodeType at = actual.getNodeType();
        if (et != at) {
            // 数字家族宽松：int/long/double
            if (et == JsonNodeType.NUMBER || at == JsonNodeType.NUMBER) {
                return et == JsonNodeType.NUMBER && at == JsonNodeType.NUMBER
                        && expected.asDouble() == actual.asDouble();
            }
            return false;
        }
        if (et == JsonNodeType.NUMBER) {
            return expected.asDouble() == actual.asDouble();
        }
        if (et == JsonNodeType.ARRAY) {
            if (expected.size() != actual.size()) {
                return false;
            }
            for (int i = 0; i < expected.size(); i++) {
                if (!compare(expected.get(i), actual.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return expected.equals(actual);
    }

    // ==================== Harness 生成 ====================

    private record MethodSig(String name, String returnType, List<String> paramTypes) {
    }

    private MethodSig parseSignature(String raw) {
        Matcher m = METHOD_SIG.matcher(raw.replace('\n', ' '));
        if (!m.find()) {
            throw new IllegalArgumentException("无法解析 public 方法签名");
        }
        String ret = m.group(1).trim();
        String name = m.group(2).trim();
        List<String> params = new ArrayList<>();
        String inside = m.group(3).trim();
        if (!inside.isEmpty()) {
            // 类型与变量名成对，取每段第一个 token（支持 int[] nums 这类数组写法）
            for (String part : inside.split(",")) {
                String[] toks = part.trim().split("\\s+");
                if (toks.length < 2) {
                    throw new IllegalArgumentException("参数必须是「类型 名称」: " + part);
                }
                params.add(toks[0].trim());
            }
        }
        return new MethodSig(name, ret, params);
    }

    private String buildArgsLiteral(MethodSig sig, JsonNode inputNode) {
        JsonNode arr = inputNode.isArray() ? inputNode : objectMapper.createArrayNode().add(inputNode);
        if (arr.size() != sig.paramTypes().size()) {
            throw new IllegalArgumentException("入参数量 " + arr.size() + " 与签名 " + sig.paramTypes().size() + " 不一致");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('(').append(javaType(sig.paramTypes().get(i))).append(')')
              .append(toJavaLiteral(arr.get(i), sig.paramTypes().get(i)));
            if (!isPrimitive(sig.paramTypes().get(i))) {
                // Object[] 装引用类型无需强转拆箱；上面的 (int) 强转表达式会自动装箱
            }
        }
        return sb.toString();
    }

    private String javaType(String t) {
        return switch (t) {
            case "int" -> "int";
            case "long" -> "long";
            case "double" -> "double";
            case "boolean" -> "boolean";
            case "char" -> "char";
            case "String" -> "java.lang.String";
            case "int[]" -> "int[]";
            case "long[]" -> "long[]";
            case "double[]" -> "double[]";
            case "boolean[]" -> "boolean[]";
            case "String[]" -> "java.lang.String[]";
            case "int[][]" -> "int[][]";
            case "String[][]" -> "java.lang.String[][]";
            default -> throw new IllegalArgumentException("不支持的参数类型 " + t);
        };
    }

    private boolean isPrimitive(String t) {
        return Set.of("int", "long", "double", "boolean", "char").contains(t);
    }

    private String toJavaLiteral(JsonNode node, String type) {
        if (node == null || node.isNull()) {
            return "null";
        }
        switch (type) {
            case "int" -> {
                return "(" + node.asInt() + ")";
            }
            case "long" -> {
                return node.asLong() + "L";
            }
            case "double" -> {
                return node.asDouble() + "d";
            }
            case "boolean" -> {
                return String.valueOf(node.asBoolean());
            }
            case "char" -> {
                return "'" + node.asText().replace("\\", "\\\\").replace("'", "\\'") + "'";
            }
            case "String" -> {
                return quote(node.asText());
            }
            case "int[]" -> {
                StringBuilder b = new StringBuilder("new int[]{");
                for (int i = 0; i < node.size(); i++) {
                    b.append(i > 0 ? "," : "").append(node.get(i).asInt());
                }
                return b.append("}").toString();
            }
            case "long[]" -> {
                StringBuilder b = new StringBuilder("new long[]{");
                for (int i = 0; i < node.size(); i++) {
                    b.append(i > 0 ? "," : "").append(node.get(i).asLong()).append("L");
                }
                return b.append("}").toString();
            }
            case "double[]" -> {
                StringBuilder b = new StringBuilder("new double[]{");
                for (int i = 0; i < node.size(); i++) {
                    b.append(i > 0 ? "," : "").append(node.get(i).asDouble()).append("d");
                }
                return b.append("}").toString();
            }
            case "String[]" -> {
                StringBuilder b = new StringBuilder("new String[]{");
                for (int i = 0; i < node.size(); i++) {
                    b.append(i > 0 ? "," : "").append(quote(node.get(i).asText()));
                }
                return b.append("}").toString();
            }
            case "int[][]" -> {
                StringBuilder b = new StringBuilder("new int[][]{");
                for (int i = 0; i < node.size(); i++) {
                    if (i > 0) {
                        b.append(',');
                    }
                    b.append('{');
                    for (int j = 0; j < node.get(i).size(); j++) {
                        b.append(j > 0 ? "," : "").append(node.get(i).get(j).asInt());
                    }
                    b.append('}');
                }
                return b.append("}").toString();
            }
            case "String[][]" -> {
                StringBuilder b = new StringBuilder("new String[][]{");
                for (int i = 0; i < node.size(); i++) {
                    if (i > 0) {
                        b.append(',');
                    }
                    b.append('{');
                    for (int j = 0; j < node.get(i).size(); j++) {
                        b.append(j > 0 ? "," : "").append(quote(node.get(i).get(j).asText()));
                    }
                    b.append('}');
                }
                return b.append("}").toString();
            }
            default -> throw new IllegalArgumentException("不支持的字面量类型 " + type);
        }
    }

    private String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    /**
     * Runner：反射调用 Solution 的目标方法，并把返回值序列化为单行 JSON。
     * 不依赖任何第三方库（执行环境 classpath 只有当前目录）。
     */
    private String buildRunnerSource(MethodSig sig) {
        StringBuilder sb = new StringBuilder();
        sb.append("import java.lang.reflect.Array;\n");
        sb.append("public class Runner {\n");
        sb.append("static Object invoke(Object[] args) throws Exception {\n");
        sb.append("Class<?> c = Class.forName(\"Solution\");\n");
        sb.append("Class<?>[] pts = new Class<?>[]{");
        for (int i = 0; i < sig.paramTypes().size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(paramClassLiteral(sig.paramTypes().get(i)));
        }
        sb.append("};\n");
        sb.append("java.lang.reflect.Method m = c.getDeclaredMethod(\"").append(sig.name()).append("\", pts);\n");
        sb.append("m.setAccessible(true);\n");
        sb.append("Object inst = null;\n");
        sb.append("boolean isStatic = java.lang.reflect.Modifier.isStatic(m.getModifiers());\n");
        sb.append("if (!isStatic) inst = c.getDeclaredConstructor().newInstance();\n");
        sb.append("return m.invoke(inst, args);\n");
        sb.append("}\n");
        sb.append("static String toJson(Object v) {\n");
        sb.append("StringBuilder s = new StringBuilder(); write(s, v); return s.toString();\n");
        sb.append("}\n");
        sb.append("static void write(StringBuilder s, Object v) {\n");
        sb.append("if (v == null) { s.append(\"null\"); return; }\n");
        sb.append("Class<?> k = v.getClass();\n");
        sb.append("if (v instanceof String || v instanceof Character) { s.append('\"')"
                + ".append(v.toString().replace(\"\\\\\",\"\\\\\\\\\").replace(\"\\\"\",\"\\\\\\\"\")"
                + ".replace(\"\\n\",\"\\\\n\").replace(\"\\r\",\"\\\\r\").replace(\"\\t\",\"\\\\t\")).append('\"'); return; }\n");
        sb.append("if (k.isArray()) {\n s.append('[');\n int n = Array.getLength(v);\n");
        sb.append(" for (int i=0;i<n;i++){ if(i>0)s.append(','); write(s, Array.get(v,i)); }\n s.append(']'); return; }\n");
        sb.append("s.append(v.toString());\n");
        sb.append("}\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String paramClassLiteral(String t) {
        return switch (t) {
            case "int" -> "int.class";
            case "long" -> "long.class";
            case "double" -> "double.class";
            case "boolean" -> "boolean.class";
            case "char" -> "char.class";
            case "String" -> "String.class";
            case "int[]" -> "int[].class";
            case "long[]" -> "long[].class";
            case "double[]" -> "double[].class";
            case "boolean[]" -> "boolean[].class";
            case "String[]" -> "String[].class";
            case "int[][]" -> "int[][].class";
            case "String[][]" -> "String[][].class";
            default -> throw new IllegalArgumentException("不支持的参数类型 " + t);
        };
    }

    // ==================== 进程与环境 ====================

    private void scrubEnvironment(ProcessBuilder pb) {
        // 白名单清洗：子进程不继承主服务环境变量，杜绝 API Key/Token 泄露。
        Map<String, String> env = pb.environment();
      env.clear();
        env.put("PATH", "/usr/bin:/bin:/usr/local/bin");
        env.put("LANG", "en_US.UTF-8");
        pb.redirectErrorStream(false);
    }

    private String javaHomeBin(String tool) {
        String home = System.getProperty("java.home");
        Path p = Path.of(home, "bin", tool);
        if (Files.isExecutable(p)) {
            return p.toString();
        }
        return tool; // 回退 PATH（环境已被白名单限制）
    }

    private Path createPrivateDir() throws IOException {
        return Files.createTempDirectory("kfjudge-",
                PosixFilePermissions.asFileAttribute(DIR_PERMS));
    }

    private void cleanup(Path dir) {
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 清理失败不影响判题结论；残留由 OS tmp 策略回收（已知风险写入安全报告）
                }
            });
        } catch (IOException ignored) {
            //
        }
    }

    private String readStream(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > OUTPUT_LIMIT ? s.substring(0, OUTPUT_LIMIT) + "\n...[truncated]" : s;
    }

    private boolean isHidden(CodeTestCase tc) {
        return CodeTestCase.VIS_HIDDEN.equals(tc.getVisibility());
    }

    private JudgeVerdict systemError(String reason) {
        return new JudgeVerdict(JudgeStatus.SYSTEM_ERROR, null, 0, 0, null, null,
                null, List.of(), "LOCAL_HOST", Map.of("reason", reason, "sandbox", false));
    }
}
