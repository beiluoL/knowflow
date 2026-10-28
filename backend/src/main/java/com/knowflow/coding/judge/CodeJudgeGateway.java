package com.knowflow.coding.judge;

/**
 * 代码判题网关（唯一判题入口）。
 *
 * <p>三种实现由 {@code knowflow.judge.mode} 选择：
 * <ul>
 *   <li>disabled（默认）：{@link DisabledCodeJudge}，不执行任何代码，返回 JUDGE_UNAVAILABLE；</li>
 *   <li>mock：{@link MockCodeJudge}，仅测试/演示业务链路，不代表真实执行；</li>
 *   <li>local：{@link LocalHostJavaJudge}，宿主机 javac/java，复用 HostCapabilityGuard，
 *       仅限显式授权的本地用户，<b>不是 OS 沙箱</b>，不得作为生产在线判题。</li>
 * </ul>
 */
public interface CodeJudgeGateway {

    JudgeVerdict judge(JudgeRequest request);

    /** 网关是否能真实执行代码（false 时提交应落 JUDGE_UNAVAILABLE/PENDING）。 */
    boolean executionEnabled();

    /** 独立Runner隔离验收证明；已有宿主与mock实现始终不具备普通用户执行资格。 */
    default boolean isolatedExecutionVerified() { return false; }

    /** DISABLED / MOCK / LOCAL_HOST / ISOLATED。 */
    String mode();
}
