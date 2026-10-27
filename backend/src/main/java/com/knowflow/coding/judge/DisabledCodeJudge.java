package com.knowflow.coding.judge;

import com.knowflow.coding.entity.CodeTestCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 默认判题网关：不执行任何用户代码。
 *
 * <p>Phase 3F 安全审计结论：宿主机 ProcessBuilder 不是 OS 沙箱，在隔离 Runner
 * （容器/gVisor/Firecracker）落地并通过安全门禁前，真实执行入口必须保持关闭。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "knowflow.judge.mode", havingValue = "disabled", matchIfMissing = true)
public class DisabledCodeJudge implements CodeJudgeGateway {

    @Override
    public JudgeVerdict judge(JudgeRequest request) {
        log.warn("[Judge] 判题服务未就绪（隔离 Runner 未部署），questionId={} 提交保持待判，不记 0 分",
                request.questionId());
        List<CodeTestCase> cases = request.effectiveCases();
        List<CaseResult> pending = cases.stream()
                .map(tc -> new CaseResult(isHidden(tc), tc.getOrderNo(),
                        null, null, null, false, 0, JudgeStatus.JUDGE_UNAVAILABLE))
                .toList();
        return new JudgeVerdict(JudgeStatus.JUDGE_UNAVAILABLE, null, 0, cases.size(),
                null, null, null, pending, "DISABLED",
                Map.of("reason", "ISOLATED_RUNNER_NOT_DEPLOYED"));
    }

    private static boolean isHidden(CodeTestCase tc) {
        return CodeTestCase.VIS_HIDDEN.equals(tc.getVisibility());
    }

    @Override
    public boolean executionEnabled() {
        return false;
    }

    @Override
    public String mode() {
        return "DISABLED";
    }
}
