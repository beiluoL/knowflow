package com.knowflow.coding.contract;

/**
 * 判题能力状态的只读契约。
 *
 * <p>供其他模块查询「当前判题是否可执行、以什么模式运行」，不暴露判题实现，也不触发任何代码执行。
 *
 * <p>`productionSandboxReady` 恒为 false：现有 disabled / mock / local-host 三种实现均未通过 独立 Runner
 * 隔离验收，不能作为生产在线判题。该口径与 {@code CodeJudgeGateway#isolatedExecutionVerified} 保持一致，待真实沙箱落地后由 coding
 * 模块统一放开。
 */
public interface CodeJudgeStatusQueryPort {

  /** 判题模式：DISABLED / MOCK / LOCAL_HOST / ISOLATED。 */
  String mode();

  /** 是否具备真实执行能力；false 时提交应落 JUDGE_UNAVAILABLE / PENDING。 */
  boolean executionEnabled();

  /** 生产沙箱是否就绪；当前所有实现均未就绪。 */
  default boolean productionSandboxReady() {
    return false;
  }
}
