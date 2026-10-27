package com.knowflow.coding.seed;

import com.knowflow.coding.entity.CodeProblem;
import com.knowflow.coding.entity.CodeTestCase;
import com.knowflow.coding.judge.JudgeRequest;
import com.knowflow.coding.judge.JudgeStatus;
import com.knowflow.coding.judge.JudgeVerdict;
import com.knowflow.coding.judge.LocalHostJavaJudge;
import com.knowflow.security.HostCapabilityGuard;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3G 种子题真实 javac/java 校验（非 Mock）：
 * <ul>
 *   <li>每道种子题的参考解答必须在真实 Java 17 子进程中通过全部公开+隐藏用例；</li>
 *   <li>初始骨架代码必须不能通过（防止骨架伪装成答案）；</li>
 *   <li>编译错误/超时/隐藏用例脱敏有独立断言。</li>
 * </ul>
 * 运行前提：本机存在 JDK（javac/java）。这是<b>本地授权执行</b>，不是 OS 沙箱安全验证。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LocalJavaJudgeSeedVerificationTest {

    private LocalHostJavaJudge judge;

    @BeforeAll
    void setUp() {
        // 绕过 Web 认证上下文：测试直接持有一个“已授权本机用户”的门禁替身（不触碰任何安全配置）
        HostCapabilityGuard localAuthorized = new HostCapabilityGuard(null) {
            @Override
            public Long requireAccess() {
                return 1L;
            }
        };
        // 无 JDK 环境（CI 镜像只有 JRE）时跳过，但不允许声明为 PASS
        Assumptions.assumeTrue(hasJavac(), "本机未检测到 javac，跳过真实编译执行校验（NOT_VERIFIED）");
        judge = new LocalHostJavaJudge(localAuthorized);
    }

    private boolean hasJavac() {
        try {
            Process p = new ProcessBuilder(System.getProperty("java.home")
                    + java.io.File.separator + ".." + java.io.File.separator + "bin"
                    + java.io.File.separator + "javac", "-version").redirectErrorStream(true).start();
            p.waitFor();
            // JDK 布局下 ../bin/javac 存在；JRE 布局会抛 IOException
            return p.exitValue() == 0;
        } catch (Exception e) {
            try {
                Process p = new ProcessBuilder("javac", "-version").redirectErrorStream(true).start();
                p.waitFor();
                return p.exitValue() == 0;
            } catch (Exception ex) {
                return false;
            }
        }
    }

    @Test
    @DisplayName("全部种子题参考解答经真实 javac/java 执行，必须 ACCEPTED 所有用例")
    void allReferenceSolutionsAcceptedByRealJava() {
        for (JavaAlgorithmProblemSeeder.Seed s : JavaAlgorithmProblemSeeder.SEEDS) {
            CodeProblem problem = JavaAlgorithmProblemSeeder.buildProblem(s);
            List<CodeTestCase> cases = JavaAlgorithmProblemSeeder.buildCases(s);
            JudgeVerdict v = judge.judge(new JudgeRequest(1L, 1L, "java", s.reference(), true, problem, cases));
            assertEquals(JudgeStatus.ACCEPTED, v.status(),
                    () -> s.key() + " 参考解答判题失败：" + v.status() + " " + v.compileOutput()
                            + " cases=" + v.cases());
            assertEquals(cases.size(), v.passedCount(), () -> s.key() + " 通过用例数不符");
            assertEquals(100, v.score(), () -> s.key() + " 分数应为满分");
            assertEquals("LOCAL_HOST", v.runnerMode());
        }
    }

    @Test
    @DisplayName("初始骨架代码不能通过判题（防止空骨架伪装标准答案）")
    void starterCodeMustFail() {
        for (JavaAlgorithmProblemSeeder.Seed s : JavaAlgorithmProblemSeeder.SEEDS) {
            CodeProblem problem = JavaAlgorithmProblemSeeder.buildProblem(s);
            List<CodeTestCase> cases = JavaAlgorithmProblemSeeder.buildCases(s);
            JudgeVerdict v = judge.judge(new JudgeRequest(1L, 1L, "java", s.starter(), true, problem, cases));
            assertNotEquals(JudgeStatus.ACCEPTED, v.status(),
                    () -> s.key() + " 的初始骨架竟然通过了判题");
        }
    }

    @Test
    @DisplayName("语法错误返回 COMPILE_ERROR，且不执行任何用例")
    void compileErrorDetected() {
        JavaAlgorithmProblemSeeder.Seed s = JavaAlgorithmProblemSeeder.SEEDS.get(0);
        CodeProblem problem = JavaAlgorithmProblemSeeder.buildProblem(s);
        String broken = "class Solution { public int[] twoSum(int[] nums, int target) { return new[; }";
        JudgeVerdict v = judge.judge(new JudgeRequest(1L, 1L, "java", broken, true, problem,
                JavaAlgorithmProblemSeeder.buildCases(s)));
        assertEquals(JudgeStatus.COMPILE_ERROR, v.status());
        assertEquals(0, v.passedCount());
    }

    @Test
    @DisplayName("RUN 只执行公开用例；SUBMIT 执行全部用例")
    void runOnlyPublicCases() {
        JavaAlgorithmProblemSeeder.Seed s = JavaAlgorithmProblemSeeder.SEEDS.get(0);
        CodeProblem problem = JavaAlgorithmProblemSeeder.buildProblem(s);
        List<CodeTestCase> cases = JavaAlgorithmProblemSeeder.buildCases(s);
        long publicCount = cases.stream().filter(c -> CodeTestCase.VIS_PUBLIC.equals(c.getVisibility())).count();

        JudgeVerdict run = judge.judge(new JudgeRequest(1L, 1L, "java", s.reference(), false, problem, cases));
        assertEquals(publicCount, run.totalCount());
        assertEquals(JudgeStatus.ACCEPTED, run.status());

        JudgeVerdict submit = judge.judge(new JudgeRequest(1L, 1L, "java", s.reference(), true, problem, cases));
        assertEquals(cases.size(), submit.totalCount());
    }

    @Test
    @DisplayName("隐藏用例结果视图不泄露 input/expected")
    void hiddenCasesSanitized() {
        JavaAlgorithmProblemSeeder.Seed s = JavaAlgorithmProblemSeeder.SEEDS.get(0);
        CodeProblem problem = JavaAlgorithmProblemSeeder.buildProblem(s);
        List<CodeTestCase> cases = JavaAlgorithmProblemSeeder.buildCases(s);
        JudgeVerdict v = judge.judge(new JudgeRequest(1L, 1L, "java", s.starter(), true, problem, cases));
        boolean hasHiddenView = v.cases().stream().anyMatch(c -> c.hidden());
        assertTrue(hasHiddenView, "该题应包含隐藏用例结果");
        v.cases().stream().filter(c -> c.hidden()).forEach(c -> {
            Map<String, Object> view = c.toView();
            assertFalse(view.containsKey("input"), "隐藏用例不得回传 input");
            assertFalse(view.containsKey("expected"), "隐藏用例不得回传 expected");
        });
    }

    @Test
    @DisplayName("死循环触发 TIME_LIMIT_EXCEEDED（真实超时强杀，非 Mock）")
    void infiniteLoopTriggersTle() {
        JavaAlgorithmProblemSeeder.Seed s = JavaAlgorithmProblemSeeder.SEEDS.get(3); // 二分查找
        CodeProblem problem = JavaAlgorithmProblemSeeder.buildProblem(s);
        problem.setTimeLimitMs(400);
        String infinite = """
                class Solution {
                    public int search(int[] nums, int target) {
                        while (true) { }
                    }
                }
                """;
        JudgeVerdict v = judge.judge(new JudgeRequest(1L, 1L, "java", infinite, true, problem,
                JavaAlgorithmProblemSeeder.buildCases(s)));
        assertEquals(JudgeStatus.TIME_LIMIT_EXCEEDED, v.status());
    }
}
