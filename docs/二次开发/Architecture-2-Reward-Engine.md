# Architecture-2 · Reward Engine 设计

> 输入=LearningEvent，输出=XP/Coin 入账 + Level 更新，唯一发奖入口（§17-18、§91）。
> 规则 MVP 用「枚举 + 静态配置」，不建数据库规则引擎（§19-20）。

## 一、位置（§18，基于真实包结构）

```
com.knowflow.reward
 ├── RewardEngine.java          （订阅/接收 LearningEvent）
 ├── RewardRule.java            （枚举 + 静态配置：事件→XP/Coin/每日上限）
 ├── RewardLedgerService.java   （流水，append-only）
 ├── WalletService.java         （余额读写，仅被 RewardEngine/PurchaseService 调用）
 └── LevelService.java          （exp → level 公式）
```

依赖方向：`RewardEngine → RewardRule + WalletService + RewardLedgerService + LevelService`；
`业务 Service（既有）→ LearningEventService.record()`（不变）。

## 二、输入（唯一行为输入，§12-13）

```
业务成功动作
   ↓（既有调用点，无需改业务）
LearningEventService.record(userId, type, resourceType, resourceId, metadata)
   ↓
RewardEngine.onEvent(event)     ← 新订阅点（同进程内调用，非 MQ，§107/§182）
```

- **禁止** `Page Open → Reward`（§13）；KNOWLEDGE_VIEWED / AI_CHAT / DOCUMENT_READ 不奖励（§15）。

## 三、规则（MVP 静态）

```java
enum RewardRule {
  CHAPTER_COMPLETE(30, 5, 0, 0),        // xp, coin, dailyLimit, 幂等粒度
  PATH_COMPLETED(120, 20, 0, 0),
  QUESTION_ANSWERED(5, 1, 50, DAILY_QUESTION),
  MISTAKE_REVIEW(8, 2, 30, 0),
  FLASHCARD_REVIEWED(4, 1, 60, 0),
  RECALL_COMPLETED(15, 3, 10, 0),
  CODE_SUBMITTED(10, 2, 20, 0),         // 仅 metadata.passed=true
  LEARNING_TASK_COMPLETE(10, 2, 0, 0),
  CHECK_IN(10, 2, 1, 0);                // 自然日唯一
}
```

## 四、幂等（§29/§62）

```
键：(user_id, source_type, source_id, currency)
流程：先查 reward_ledger 是否存在 → 存在则跳过（幂等）；不存在才入账。
约束：reward_ledger 唯一索引（user_id, source_type, source_id, currency）兜底并发。
```

- 同一 `eventId/sourceId` 请求 3 次 → 只奖励一次（§29）。
- 每日上限：`reward_daily_counter(user_id, event_type, date)` 计数（或 ledger 当日聚合），超限不发。

## 五、事务（§106-107）

```java
@Transactional
void reward(LearningEvent event) {
   int xp = rule.xp, coin = rule.coin;
   if (dailyLimitExceeded) return;
   ledger.append(userId, XP, +xp, CREDIT, reason, sourceId, balanceAfter);
   ledger.append(userId, COIN, +coin, CREDIT, reason, sourceId, balanceAfter);
   wallet.add(userId, xp, coin);          // 同事务
   level = levelService.calc(wallet.xp);  // 更新 wallet.level
}
```

- 同一 JVM 事务内完成（Modular Monolith，不跨远程，§107）。

## 六、补偿（§23）

- 错误奖励不 `UPDATE ledger`；新增 `COMPENSATION` 类型流水反向冲销（如 `-20 XP / 错误发放补偿`）。

## 七、与既有系统对接（最小改动）

| 既有点 | 现状 | 改造 |
| --- | --- | --- |
| `AchievementServiceImpl:91` | 直接 `sys_user.exp += rewardExp` | 保留（成就闭环已幂等）；可选：同步写 ledger + wallet 统一口径（P1） |
| `CodeChallengeServiceImpl:320` | 直接 `sys_user.exp += pointsEarned` | 同上（P1 统一入口） |
| `CheckInServiceImpl` | 只写 user_check_in | CHECK_IN 事件 → RewardEngine 入账（P0，修 G2） |
| `/user/stats` 推导 | 计数器公式 | 改为读 wallet（xp/coin/level），删除推导公式（P0，修 G1） |
