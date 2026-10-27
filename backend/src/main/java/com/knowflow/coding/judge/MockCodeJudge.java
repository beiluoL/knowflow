package com.knowflow.coding.judge;

import com.knowflow.coding.entity.CodeTestCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 确定性 Mock 判题器：<b>不编译、不执行任何代码</b>，仅用于验证业务链路
 * （提交状态机、幂等、错题归集、学习事件、考试结算），结果由源码中的测试标记驱动：
 *
 * <pre>
 * // @MOCK_VERDICT:ACCEPTED
 * // @MOCK_VERDICT:WRONG_ANSWER
 * // @MOCK_VERDICT:COMPILE_ERROR
 * // @MOCK_VERDICT:RUNTIME_ERROR
 * // @MOCK_VERDICT:TIME_LIMIT_EXCEEDED
 * // @MOCK_VERDICT:SYSTEM_ERROR
 * </pre>
 *
 * 无标记时默认 WRONG_ANSWER（保证调用方不会把未验证代码误判为通过）。
 * 任何报告都不得把本实现的结果称为「真实 Java 编译运行」。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "knowflow.judge.mode", havingValue = "mock")
public class MockCodeJudge implements CodeJudgeGateway {

    private static final Pattern MARKER = Pattern.compile("@MOCK_VERDICT:\\s*([A-Z_]+)");

    @Override
    public JudgeVerdict judge(JudgeRequest request) {
        log.warn("[Judge] 使用 MOCK 判题器（非真实执行）questionId={}", request.questionId());
        String forced = JudgeStatus.WRONG_ANSWER;
        Matcher m = MARKER.matcher(request.sourceCode() == null ? "" : request.sourceCode());
        if (m.find()) {
            forced = m.group(1);
        }
        List<CodeTestCase> cases = request.effectiveCases();

        if (JudgeStatus.COMPILE_ERROR.equals(forced)) {
            return new JudgeVerdict(JudgeStatus.COMPILE_ERROR, 0, 0, cases.size(), null, null,
                    "MOCK: compilation error", List.of(), "MOCK",
                    Map.of("mock", true, "forced", forced));
        }
        if (JudgeStatus.SYSTEM_ERROR.equals(forced)) {
            return new JudgeVerdict(JudgeStatus.SYSTEM_ERROR, null, 0, cases.size(), null, null,
                    null, List.of(), "MOCK", Map.of("mock", true, "forced", forced,
                            "reason", "MOCK_PLATFORM_FAILURE"));
        }
        if (JudgeStatus.TIME_LIMIT_EXCEEDED.equals(forced)) {
            return new JudgeVerdict(JudgeStatus.TIME_LIMIT_EXCEEDED, 0, 0, cases.size(),
                    request.problem().getTimeLimitMs(), null, null,
                    perCase(cases, forced, false), "MOCK", Map.of("mock", true));
        }

        boolean accepted = JudgeStatus.ACCEPTED.equals(forced);
        List<CaseResult> results = perCase(cases, forced, accepted);
        int totalWeight = cases.stream().mapToInt(c -> c.getWeight() == null ? 1 : c.getWeight()).sum();
        int passedWeight = cases.stream()
                .filter(c -> accepted)
                .mapToInt(c -> c.getWeight() == null ? 1 : c.getWeight()).sum();
        int score = totalWeight == 0 ? 0 : (int) Math.round(passedWeight * 100.0 / totalWeight);
        String status = accepted ? JudgeStatus.ACCEPTED
                : (JudgeStatus.RUNTIME_ERROR.equals(forced) ? JudgeStatus.RUNTIME_ERROR : JudgeStatus.WRONG_ANSWER);
        return new JudgeVerdict(status, accepted ? score : 0,
                accepted ? cases.size() : 0, cases.size(), accepted ? 5 : null, null,
                null, results, "MOCK", Map.of("mock", true, "forced", forced));
    }

    private List<CaseResult> perCase(List<CodeTestCase> cases, String forced, boolean passAll) {
        List<CaseResult> out = new ArrayList<>();
        for (CodeTestCase tc : cases) {
            boolean hidden = CodeTestCase.VIS_HIDDEN.equals(tc.getVisibility());
            if (passAll) {
                out.add(new CaseResult(hidden, tc.getOrderNo(), tc.getInput(), tc.getExpectedOutput(),
                        tc.getExpectedOutput(), true, 3, JudgeStatus.ACCEPTED));
            } else {
                // 第一个用例失败即结束真实 Runner 的语义；Mock 中让公开用例带 actual，隐藏用例脱敏
                String actual = hidden ? null : "mock-actual";
                out.add(new CaseResult(hidden, tc.getOrderNo(),
                        hidden ? null : tc.getInput(), hidden ? null : tc.getExpectedOutput(),
                        actual, false, 2, forced));
            }
        }
        return out;
    }

    @Override
    public boolean executionEnabled() {
        return true;
    }

    @Override
    public String mode() {
        return "MOCK";
    }
}
