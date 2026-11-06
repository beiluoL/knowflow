package com.knowflow.reward.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.reward.entity.RewardLedger;
import com.knowflow.reward.mapper.RewardLedgerMapper;
import com.knowflow.reward.vo.LedgerVO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 奖励流水服务：reward_ledger 的追加写入与只读查询，是钱包余额变动的可审计明细。
 *
 * <p>append-only 契约：只 INSERT，不 UPDATE / 不物理删除；发错奖励时追加 COMPENSATION 反向流水，而不是改写原行。实体继承
 * BaseEntity，deleted 由 @TableLogic 自动过滤。
 *
 * <p>幂等最终防线：唯一索引 uk_ledger_idempotent(user_id, source_type, source_id, currency)；exists
 * 是应用层预检，并发下仍由数据库唯一键兜底，本类未捕获 DuplicateKeyException，冲突会向上抛并回滚调用方事务。
 *
 * <p>与 Wallet 的对账口径：每行 balance_after 是写入时的余额快照，加上后续变动应等于当前 wallet 余额；对外 VO 不暴露 sourceId /
 * sourceType 等内部字段。
 */
@Service
@RequiredArgsConstructor
public class RewardLedgerService {

  private final RewardLedgerMapper ledgerMapper;

  /**
   * 内部原因码到用户可读文案的映射，覆盖全部 9 种奖励规则、PURCHASE_APP 与 COMPENSATION。
   *
   * <p>未命中的原因码直接回退原值，保证前端不会拿到 null；映射只用于展示，不改写库里的 reason。
   */
  private static final Map<String, String> REASON_LABELS =
      Map.ofEntries(
          Map.entry("CHAPTER_COMPLETE", "完成章节"),
          Map.entry("PATH_COMPLETED", "完成学习路径"),
          Map.entry("QUESTION_ANSWERED", "完成答题"),
          Map.entry("MISTAKE_REVIEW", "完成错题复习"),
          Map.entry("FLASHCARD_REVIEWED", "复习闪卡"),
          Map.entry("RECALL_COMPLETED", "完成主动回忆"),
          Map.entry("CODE_SUBMITTED", "代码练习通过"),
          Map.entry("LEARNING_TASK_COMPLETE", "完成学习任务"),
          Map.entry("CHECK_IN", "每日打卡"),
          Map.entry("PURCHASE_APP", "兑换应用"),
          Map.entry("COMPENSATION", "奖励补偿"));

  /**
   * 幂等预检：判断同一用户、同一来源、同一来源 ID、同一币种是否已有流水行。
   *
   * <p>四个参数拼成 uk_ledger_idempotent 的业务键；RewardEngine 用 ("EVENT", event.id, "XP"/"COIN")
   * 分别判定两个币种，因此一次奖励最多两行。查询经 MyBatis-Plus 自动追加 deleted = 0。
   *
   * @param sourceId 来源业务 ID，RewardEngine 传 learning_event.id，购买传 order.id；允许为 null 并参与唯一键
   * @param currency 币种，XP 或 COIN
   * @return true 表示该币种已入账，调用方应跳过；false 不保证并发下仍可插入
   */
  public boolean exists(Long userId, String sourceType, Long sourceId, String currency) {
    return ledgerMapper.selectCount(
            new LambdaQueryWrapper<RewardLedger>()
                .eq(RewardLedger::getUserId, userId)
                .eq(RewardLedger::getSourceType, sourceType)
                .eq(RewardLedger::getSourceId, sourceId)
                .eq(RewardLedger::getCurrency, currency))
        > 0;
  }

  /**
   * 统计某自然日某原因已发放的奖励「事件数」，供 RewardRule.dailyLimit 判定。
   *
   * <p>口径由 RewardLedgerMapper#countRewardEvents 决定：COUNT(DISTINCT source_id)，且只计 source_type =
   * 'EVENT'、direction = 'CREDIT'、deleted = 0、create_time 落在 [当日 00:00, 次日 00:00) 的行。一次奖励产生 XP +
   * COIN 两行流水但仍只算 1 次，RewardWalletIntegrationTest#dailyLimitCountsEventsInsteadOfCurrencyRows 已覆盖。
   *
   * @param reason 事件类型名（RewardEngine 传 rule.eventType.name()），不是用户可读文案
   * @param date 自然日，按服务端本地时区展开为半开区间
   * @return 当日已奖励的事件数，无记录为 0
   */
  public long dailyCount(Long userId, String reason, LocalDate date) {
    LocalDateTime start = date.atStartOfDay();
    LocalDateTime end = date.plusDays(1).atStartOfDay();
    return ledgerMapper.countRewardEvents(userId, reason, start, end);
  }

  /**
   * 追加一笔不可变流水，调用方负责在同一事务内先更新 wallet 余额。
   *
   * <p>不捕获唯一键冲突：并发重复入账时由 uk_ledger_idempotent 抛出 DuplicateKeyException，连同已写入的余额一起回滚。错误奖励请追加
   * COMPENSATION 行，不要修改既有行。
   *
   * @param currency 币种，XP 或 COIN；与 userId / sourceType / sourceId 共同构成幂等键
   * @param amount 变动额，奖励写正数、消费写负数（PurchaseService 传 -price）；方向另由 direction 表达
   * @param direction CREDIT（收入）/ DEBIT（支出）/ 纠错用 COMPENSATION 对应 reason
   * @param reason 原因码，通常是学习事件类型名，购买为 PURCHASE_APP，纠错为 COMPENSATION
   * @param sourceType 来源类型，奖励为 EVENT、购买为 PURCHASE
   * @param sourceId 来源 ID，EVENT 传 learning_event.id；可为 null，仍参与唯一键
   * @param balanceAfter 本次变动后该币种的余额快照，取自更新后的 Wallet
   */
  public void append(
      Long userId,
      String currency,
      int amount,
      String direction,
      String reason,
      String sourceType,
      Long sourceId,
      int balanceAfter) {
    RewardLedger ledger = new RewardLedger();
    ledger.setUserId(userId);
    ledger.setCurrency(currency);
    ledger.setAmount(amount);
    ledger.setDirection(direction);
    ledger.setReason(reason);
    ledger.setSourceType(sourceType);
    ledger.setSourceId(sourceId);
    ledger.setBalanceAfter(balanceAfter);
    ledgerMapper.insert(ledger);
  }

  /**
   * 分页查询当前用户的流水，按 create_time / id 倒序，不返回总页数或总数。
   *
   * <p>页码下限 1、每页下限 1 上限 100，避免一次拉全表；currency 非空白时转大写后精确过滤，为空则不过滤。查询始终带 userId 条件，越权用户读不到他人流水。
   *
   * @param current 页码，小于 1 按 1 处理
   * @param size 每页条数，超出 1–100 会被夹逼
   * @param currency 可选币种过滤，大小写不敏感
   * @return 当前页的流水 VO 列表，无数据返回空列表
   */
  public List<LedgerVO> page(Long userId, String currency, int current, int size) {
    Page<RewardLedger> page = new Page<>(Math.max(current, 1), Math.min(Math.max(size, 1), 100));
    LambdaQueryWrapper<RewardLedger> qw =
        new LambdaQueryWrapper<RewardLedger>()
            .eq(RewardLedger::getUserId, userId)
            .orderByDesc(RewardLedger::getCreateTime)
            .orderByDesc(RewardLedger::getId);
    if (currency != null && !currency.isBlank()) {
      qw.eq(RewardLedger::getCurrency, currency.toUpperCase());
    }
    List<RewardLedger> rows = ledgerMapper.selectPage(page, qw).getRecords();
    return rows.stream().map(this::toVO).collect(Collectors.toList());
  }

  /** 把实体投影为前端 VO：只暴露展示字段，不复制 sourceId / sourceType；reasonLabel 未命中映射时回退原始原因码。 */
  private LedgerVO toVO(RewardLedger l) {
    LedgerVO vo = new LedgerVO();
    vo.setId(l.getId());
    vo.setCurrency(l.getCurrency());
    vo.setAmount(l.getAmount());
    vo.setDirection(l.getDirection());
    vo.setReason(l.getReason());
    vo.setReasonLabel(REASON_LABELS.getOrDefault(l.getReason(), l.getReason()));
    vo.setBalanceAfter(l.getBalanceAfter());
    vo.setCreateTime(l.getCreateTime());
    return vo;
  }
}
