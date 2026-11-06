package com.knowflow.reward.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.learning.contract.LearningEventType;
import com.knowflow.learning.contract.LearningRewardEvent;
import com.knowflow.reward.entity.Wallet;
import com.knowflow.reward.enums.RewardRule;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 奖励引擎：把已落库的学习事件按 RewardRule 兑换成钱包余额与奖励流水，是服务端唯一发奖入口。
 *
 * <p>调用链：LearningEventServiceImpl 在 learning_event 落库后调用 {@link #onEvent}；WalletController 只暴露 GET
 * 查询，前端没有直接发奖接口。
 *
 * <p>事务隔离：发奖使用 REQUIRES_NEW + READ_COMMITTED 的独立事务。LearningEventServiceImpl 已用 try/catch
 * 吞掉本引擎异常并仅告警，因此奖励失败不会回滚业务主流程，也不会改写掌握度或 SRS 数据。
 *
 * <p>幂等与并发：流水唯一键为 (user_id, source_type=EVENT, source_id=event.id, currency)，XP 与 COIN 各判一次；先对
 * wallet 行加 FOR UPDATE 锁，锁持续到本事务提交，使限额统计、幂等判定、余额写入在同一临界区内串行。
 *
 * <p>不发奖的情形：没有 RewardRule 的浏览类事件（文档阅读、AI 对话、知识图谱查看等）、任务撤销后带 rewardEligible=false 的重做、代码未全部通过、当日已达
 * dailyLimit、该事件该币种已入账。跳过时不写 wallet 也不写 reward_ledger。
 */
@Service
@RequiredArgsConstructor
public class RewardEngine {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final WalletService walletService;
  private final RewardLedgerService ledgerService;

  /**
   * 按奖励规则把一条已落库的学习事件兑换为 XP / Coin，并追加至多两行奖励流水。
   *
   * <p>执行顺序：
   *
   * <ol>
   *   <li>校验事件身份与事件类型，id / userId 缺失或类型无法反查规则时直接返回。
   *   <li>过滤「事件真实发生但不发奖」的情形：任务重做且 rewardEligible=false、代码未全部通过。
   *   <li>对 wallet 行加 FOR UPDATE 锁，再在锁内检查当日限额与逐币种幂等。
   *   <li>一次更新合并 XP / Coin 增量，随后按币种追加 CREDIT 流水，balanceAfter 取更新后的余额快照。
   * </ol>
   *
   * <p>事务与回滚：REQUIRES_NEW 独立事务，READ_COMMITTED
   * 隔离；任一步抛异常（含第二笔流水写入失败）都会回滚钱包更新与第一笔流水，RewardWalletIntegrationTest 已覆盖该回滚路径。
   *
   * @param event 已持久化的学习事件，id / userId / eventType 均不可为空；id 是幂等键中的 sourceId，eventType 用于反查
   *     RewardRule
   * @throws IllegalArgumentException 触发资格校验的事件 metadata 不是合法 JSON 时由 readMetadata 抛出，整个发奖事务回滚
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  public void onEvent(LearningRewardEvent event) {
    if (event == null || event.getId() == null || event.getUserId() == null) {
      return;
    }
    LearningEventType type = parseType(event.getEventType());
    RewardRule rule = RewardRule.byEventType(type);
    if (rule == null) {
      return; // 不奖励事件：页面查看/对话/阅读/开始/错题归集等（§18）
    }
    // 阶段二：旧学习任务撤销后重做仍是合法活动，但不能重复获得任务奖励。
    // 资格来自事件自身的 metadata.rewardEligible；旧事件缺少该字段时保持兼容、照常发奖，其他规则不变。
    if (type == LearningEventType.LEARNING_TASK_COMPLETE
        && event.getMetadata() != null
        && Boolean.FALSE.equals(readMetadata(event).get("rewardEligible"))) {
      return;
    }
    if (type == LearningEventType.CODE_SUBMITTED && !isCodePassed(event)) {
      return; // 代码仅全部用例通过才奖励（§17/§28）
    }
    // 阶段三：先锁钱包行再统计限额与幂等，避免并发事务同时判定「未发过」而重复入账。
    Wallet lockedWallet = walletService.lockForReward(event.getUserId());
    if (rule.getDailyLimit() > 0
        && ledgerService.dailyCount(event.getUserId(), rule.getEventType().name(), LocalDate.now())
            >= rule.getDailyLimit()) {
      return; // 每日上限（§33-34/§208）
    }
    boolean xpPending =
        rule.getXp() > 0 && !ledgerService.exists(event.getUserId(), "EVENT", event.getId(), "XP");
    boolean coinPending =
        rule.getCoin() > 0
            && !ledgerService.exists(event.getUserId(), "EVENT", event.getId(), "COIN");
    if (!xpPending && !coinPending) {
      return; // 幂等：已奖励过（§29-30/§207）
    }
    // 阶段四：余额一次更新，两笔流水分别取对应币种的新余额；任一 append 失败则整体回滚。
    Wallet wallet =
        walletService.creditLocked(
            lockedWallet, xpPending ? rule.getXp() : 0, coinPending ? rule.getCoin() : 0);
    if (xpPending) {
      ledgerService.append(
          event.getUserId(),
          "XP",
          rule.getXp(),
          "CREDIT",
          rule.getEventType().name(),
          "EVENT",
          event.getId(),
          wallet.getXp());
    }
    if (coinPending) {
      ledgerService.append(
          event.getUserId(),
          "COIN",
          rule.getCoin(),
          "CREDIT",
          rule.getEventType().name(),
          "EVENT",
          event.getId(),
          wallet.getCoin());
    }
  }

  /** 解析事件 metadata JSON；只有任务重做的资格判定会走这里，非法 JSON 抛异常使发奖事务回滚。 */
  private Map<?, ?> readMetadata(LearningRewardEvent event) {
    try {
      return MAPPER.readValue(event.getMetadata(), Map.class);
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new IllegalArgumentException("Invalid reward event metadata", exception);
    }
  }

  /** 把字符串事件类型转成枚举；null、空白、未知枚举值统一返回 null，让调用方按「不奖励事件」跳过。 */
  private LearningEventType parseType(String name) {
    if (name == null || name.isBlank()) {
      return null;
    }
    try {
      return LearningEventType.valueOf(name);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * 判定 CODE_SUBMITTED 是否全部用例通过，只有返回 true 才发奖。
   *
   * <p>接受两种 metadata：显式 {"passed": true}，或 {"passCount": N, "total": N} 且两者相等并大于 0；passed
   * 字段优先。解析失败、缺字段、类型不符一律返回 false（静默不发奖，不抛异常）。
   */
  private boolean isCodePassed(LearningRewardEvent event) {
    String metadata = event.getMetadata();
    if (metadata == null || metadata.isBlank()) {
      return false;
    }
    try {
      Map<?, ?> map = MAPPER.readValue(metadata, Map.class);
      Object passed = map.get("passed");
      if (passed != null) {
        return Boolean.TRUE.equals(passed) || "true".equalsIgnoreCase(String.valueOf(passed));
      }
      Object passCount = map.get("passCount");
      Object total = map.get("total");
      if (passCount instanceof Number && total instanceof Number) {
        return ((Number) passCount).intValue() > 0
            && ((Number) passCount).intValue() == ((Number) total).intValue();
      }
      return false;
    } catch (Exception e) {
      return false;
    }
  }
}
