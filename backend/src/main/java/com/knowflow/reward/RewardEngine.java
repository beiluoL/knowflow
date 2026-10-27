package com.knowflow.reward;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.LearningEventType;
import com.knowflow.entity.LearningEvent;
import com.knowflow.entity.Wallet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

/**
 * 奖励引擎：LearningEvent → RewardRule → Wallet + Ledger（唯一发奖入口，§17/§91/§156）。
 *
 * 关键设计：
 *  - 输入唯一是 LearningEvent（§13/§319），前端无任何发奖 API（§65-67/§328）。
 *  - 幂等：source_id = event.id（learning_event 主键天然唯一），同一事件重复调用只奖励一次（§29-30/§207）。
 *  - 每日上限：dailyCount 聚合（§34），QUESTION_ANSWERED/FLASHCARD_REVIEWED 等高频事件防刷（§33/§208）。
 *  - 不奖励事件（DOCUMENT_READ/AI_CHAT/KNOWLEDGE_VIEWED/CHAPTER_START/MISTAKE）无对应规则，自动跳过（§18/§209）。
 *  - 独立事务（REQUIRES_NEW）：奖励失败绝不影响业务主流程（与掌握度引擎同策略，§35/§106）。
 *  - 不修改 Mastery / SRS（§268-269）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RewardEngine {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WalletService walletService;
    private final RewardLedgerService ledgerService;
    private final LevelService levelService;
    private final com.knowflow.learning.actions.LearningWriteGuard learningWriteGuard;

    /** 事件入账入口（由 LearningEventServiceImpl 在事件落库后调用）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEvent(LearningEvent event) {
        if (event == null || event.getId() == null || event.getUserId() == null) {
            return;
        }
        try {
            LearningEventType type = parseType(event.getEventType());
            RewardRule rule = RewardRule.byEventType(type);
            if (rule == null) {
                return; // 不奖励事件：页面查看/对话/阅读/开始/错题归集等（§18）
            }
            // 旧学习任务撤销后重做仍是合法活动，但不能重复获得任务奖励。
            // 资格由行锁内的真实历史确定；旧事件缺少此字段保持兼容，其他奖励规则不变。
            if (type == LearningEventType.LEARNING_TASK_COMPLETE && event.getMetadata() != null
                    && Boolean.FALSE.equals(MAPPER.readValue(event.getMetadata(), Map.class).get("rewardEligible"))) {
                return;
            }
            if (type == LearningEventType.LEARNING_TASK_COMPLETE) {
                // 主事务提交会释放owner锁；活动奖励在独立事务重新锁同一owner，
                // 避免同用户不同任务的afterCommit回调并发读写钱包导致余额丢更新。
                learningWriteGuard.lock(event.getUserId());
            }
            if (type == LearningEventType.CODE_SUBMITTED && !isCodePassed(event)) {
                return; // 代码仅全部用例通过才奖励（§17/§28）
            }
            if (rule.getDailyLimit() > 0
                    && ledgerService.dailyCount(event.getUserId(), rule.getEventType().name(), LocalDate.now()) >= rule.getDailyLimit()) {
                return; // 每日上限（§33-34/§208）
            }
            boolean xpPending = rule.getXp() > 0
                    && !ledgerService.exists(event.getUserId(), "EVENT", event.getId(), "XP");
            boolean coinPending = rule.getCoin() > 0
                    && !ledgerService.exists(event.getUserId(), "EVENT", event.getId(), "COIN");
            if (!xpPending && !coinPending) {
                return; // 幂等：已奖励过（§29-30/§207）
            }
            Wallet wallet = walletService.credit(event.getUserId(),
                    xpPending ? rule.getXp() : 0, coinPending ? rule.getCoin() : 0);
            if (xpPending) {
                ledgerService.append(event.getUserId(), "XP", rule.getXp(), "CREDIT",
                        rule.getEventType().name(), "EVENT", event.getId(), wallet.getXp());
            }
            if (coinPending) {
                ledgerService.append(event.getUserId(), "COIN", rule.getCoin(), "CREDIT",
                        rule.getEventType().name(), "EVENT", event.getId(), wallet.getCoin());
            }
        } catch (Exception ex) {
            log.warn("奖励引擎处理事件失败（已忽略，不影响主流程）: eventId={}, err={}",
                    event.getId(), ex.getMessage());
        }
    }

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

    /** CODE_SUBMITTED 事件 metadata：{"passed": true} 或 {"passCount": N, "total": N} */
    private boolean isCodePassed(LearningEvent event) {
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
