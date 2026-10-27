package com.knowflow.coding.judge;

/** 判题状态机。SYSTEM_ERROR / JUDGE_UNAVAILABLE 是平台侧失败，绝不记为用户答错、不进错题本。 */
public final class JudgeStatus {

    private JudgeStatus() {
    }

    public static final String QUEUED = "QUEUED";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String WRONG_ANSWER = "WRONG_ANSWER";
    public static final String COMPILE_ERROR = "COMPILE_ERROR";
    public static final String RUNTIME_ERROR = "RUNTIME_ERROR";
    public static final String TIME_LIMIT_EXCEEDED = "TIME_LIMIT_EXCEEDED";
    public static final String MEMORY_LIMIT_EXCEEDED = "MEMORY_LIMIT_EXCEEDED";
    /** 平台执行失败（沙箱/Runner 异常）：不计用户错误，允许重试 */
    public static final String SYSTEM_ERROR = "SYSTEM_ERROR";
    /** 判题服务未就绪（安全门禁未通过时的默认状态）：不判分，等待隔离 Runner */
    public static final String JUDGE_UNAVAILABLE = "JUDGE_UNAVAILABLE";

    /** 用户侧失败（计入错题）。 */
    public static boolean isUserFailure(String status) {
        return WRONG_ANSWER.equals(status) || COMPILE_ERROR.equals(status)
                || RUNTIME_ERROR.equals(status) || TIME_LIMIT_EXCEEDED.equals(status)
                || MEMORY_LIMIT_EXCEEDED.equals(status);
    }

    /** 平台侧失败：score 必须保持 null，不记错，不发失败事件。 */
    public static boolean isPlatformFailure(String status) {
        return SYSTEM_ERROR.equals(status) || JUDGE_UNAVAILABLE.equals(status);
    }

    public static boolean isFinished(String status) {
        return ACCEPTED.equals(status) || isUserFailure(status);
    }
}
