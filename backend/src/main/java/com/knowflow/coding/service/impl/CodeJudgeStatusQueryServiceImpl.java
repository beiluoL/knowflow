package com.knowflow.coding.service.impl;

import com.knowflow.coding.contract.CodeJudgeStatusQueryPort;
import com.knowflow.coding.judge.CodeJudgeGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 判题状态契约实现：把当前激活的 {@link CodeJudgeGateway} 适配为只读状态视图。
 *
 * <p>状态完全由网关决定，本类不缓存、不改造语义，也不触发代码执行。
 */
@Service
@RequiredArgsConstructor
public class CodeJudgeStatusQueryServiceImpl implements CodeJudgeStatusQueryPort {

  private final CodeJudgeGateway gateway;

  @Override
  public String mode() {
    return gateway.mode();
  }

  @Override
  public boolean executionEnabled() {
    return gateway.executionEnabled();
  }

  @Override
  public boolean productionSandboxReady() {
    // 与重构前一致：恒为 false。判题实现（disabled / mock / local-host）均未通过独立 Runner
    // 隔离验收；此处不读取 gateway 的可覆盖实现，避免响应值随实现而变。
    return false;
  }
}
