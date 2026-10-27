package com.knowflow.reward;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.entity.RewardLedger;
import com.knowflow.mapper.RewardLedgerMapper;
import com.knowflow.vo.LedgerVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 奖励流水服务（append-only，§24-25/§158）。
 * 幂等：唯一键（user_id, source_type, source_id, currency）为最终防线（§29/§31）。
 * 流水只允许追加，不允许普通 UPDATE（§23）。
 */
@Service
@RequiredArgsConstructor
public class RewardLedgerService {

    private final RewardLedgerMapper ledgerMapper;

    /** 用户可读的原因文案（§114/§130：不暴露 eventId/rewardRuleId 等内部字段） */
    private static final Map<String, String> REASON_LABELS = Map.ofEntries(
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
            Map.entry("COMPENSATION", "奖励补偿")
    );

    /** 幂等检查：同一来源同一币种是否已入账 */
    public boolean exists(Long userId, String sourceType, Long sourceId, String currency) {
        return ledgerMapper.selectCount(new LambdaQueryWrapper<RewardLedger>()
                .eq(RewardLedger::getUserId, userId)
                .eq(RewardLedger::getSourceType, sourceType)
                .eq(RewardLedger::getSourceId, sourceId)
                .eq(RewardLedger::getCurrency, currency)) > 0;
    }

    /** 每日已发放次数（按原因 + 自然日聚合，§34：优先 Ledger 当日聚合，不建复杂计数表） */
    public long dailyCount(Long userId, String reason, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        return ledgerMapper.selectCount(new LambdaQueryWrapper<RewardLedger>()
                .eq(RewardLedger::getUserId, userId)
                .eq(RewardLedger::getReason, reason)
                .ge(RewardLedger::getCreateTime, start)
                .lt(RewardLedger::getCreateTime, end));
    }

    /** 追加一笔流水（调用方保证唯一键幂等；并发由数据库唯一索引兜底，§32） */
    public void append(Long userId, String currency, int amount, String direction,
                       String reason, String sourceType, Long sourceId, int balanceAfter) {
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

    /** 分页流水（只读当前用户） */
    public List<LedgerVO> page(Long userId, String currency, int current, int size) {
        Page<RewardLedger> page = new Page<>(Math.max(current, 1), Math.min(Math.max(size, 1), 100));
        LambdaQueryWrapper<RewardLedger> qw = new LambdaQueryWrapper<RewardLedger>()
                .eq(RewardLedger::getUserId, userId)
                .orderByDesc(RewardLedger::getCreateTime)
                .orderByDesc(RewardLedger::getId);
        if (currency != null && !currency.isBlank()) {
            qw.eq(RewardLedger::getCurrency, currency.toUpperCase());
        }
        List<RewardLedger> rows = ledgerMapper.selectPage(page, qw).getRecords();
        return rows.stream().map(this::toVO).collect(Collectors.toList());
    }

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
