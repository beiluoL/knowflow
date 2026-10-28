# Architecture-2 · Reward Inventory（现有奖励资产盘点）

> 基于真实源码扫描（backend/src/main/java + resources/db/h2/schema.sql + frontend/src，2026-08-30）。
> 结论先行：**KnowFlow 已有「XP/等级/精力」的雏形，但没有 Coin，没有任何 Wallet/Ledger/Store**。

## 一、现有「经验/等级/精力」资产（全部真实）

| 资产 | 载体 | 数据来源（真实） | 写入方 | 是否真实入账 |
| --- | --- | --- | --- | --- |
| exp（经验值） | `sys_user.exp INT DEFAULT 0` | 成就解锁奖励、代码挑战过关奖励 | `AchievementServiceImpl:91`、`CodeChallengeServiceImpl:320` | ✅ 真实写库 |
| level（等级） | `sys_user.level INT DEFAULT 1` | 仅建号时置 1；`/user/stats` 另行推导 | `UserServiceImpl:92,211` | ⚠️ 无统一升级公式 |
| energy（精力） | `sys_user.energy INT DEFAULT 100` | 打卡奖励（记录在 user_check_in） | `CheckInServiceImpl`（仅写 user_check_in） | ⚠️ 未入 sys_user |
| streak_days | `sys_user.streak_days` | 打卡连续天数 | `CheckInServiceImpl:76-77` | ✅ |
| total_study_hours / read_docs_count / favorite_count | `sys_user` | 文档阅读/收藏 | `DocServiceImpl:515,526,602` | ✅ 计数器 |

## 二、打卡奖励（真实但不入账）

- `user_check_in` 表：`reward_exp`、`reward_energy` 字段真实持久化。
- `CheckInServiceImpl`：`BASE_EXP=10`、`BASE_ENERGY=5`、里程碑加成 `milestoneBonus(continuous)`；**只更新 `sys_user.streak_days`，不累加 `sys_user.exp/energy`**。
- 前端 `CheckIn.vue:211` 在内存里乐观 `user.value.exp += rewardExp`——**刷新后即丢失**（因为 stats 是实时推导，不是读库）。

> 结论：打卡经验目前是「展示型奖励」，不是「入账型奖励」。这是 Architecture-2 首先要修的口径问题。

## 三、成就奖励（真实入账 ✅）

- `achievement` 表：`condition_type/condition_value/reward_exp/status`。
- `AchievementServiceImpl:88-93`：解锁时 `sys_user.exp += reward_exp`，并写 `user_achievement`（一次性，天然幂等）。
- 这是当前唯一「事件 → 判定 → 入账」闭环最完整的奖励。

## 四、代码挑战奖励（真实入账 ✅，但无流水）

- `CodeChallengeServiceImpl:320`：过关 `sys_user.exp += pointsEarned`。
- ⚠️ 无 Ledger：用户无法回答「我的 exp 为什么涨了」；无法审计。

## 五、`/user/stats` 的推导口径（⚠️ 与 sys_user.exp 不一致）

```java
// UserServiceImpl:140-146（GET /user/stats 实时推导，非读库）
int exp  = readDocsCount*10 + completedPaths*50 + favoriteCount*5;
int level= exp/100 + 1;
int energy = max(0, min(100, 100 - pendingTasks*10));
```

- 因此存在**两个 exp 口径**：`sys_user.exp`（成就/挑战入账）≠ `stats.exp`（计数器推导）。
- Home / Profile / LearningCenter / CheckIn / TaskCenter / Mistakes 等 8 个页面消费的都是推导口径。
- **这是 Architecture-2 必须统一的根因之一。**

## 六、不存在的东西（已确认）

| 项 | 结果 |
| --- | --- |
| Coin / Wallet / Balance / Ledger / Order / Entitlement / Product | ❌ 后端 0 命中、schema 无表 |
| user_app（后端） | ❌ 仅 Architecture-1 前端 localStorage |
| RewardEngine / RewardRule | ❌ 无 |

## 七、相关既有表（可复用，不新建）

`achievement` / `user_achievement` / `user_check_in` / `learning_event` / `task` / `task_list` / `habit` / `habit_checkin` / `focus_session` / `learning_task` / `wb_review_card` / `wb_review_log` / `wb_recall_session` / `quiz_answer_record` / `code_challenge_record`

## 八、结论

- **不需要从零发明 XP/等级**：`sys_user.exp/level/energy` 已存在，缺口是「统一口径 + 统一入账 + 可审计流水」。
- **Coin/Wallet/Ledger/Store 是从零新增**（MVP 需要 5-6 张表）。
- 旧 exp 数据是真实存在的（成就/挑战已入账），**迁移策略 = 统一口径，不删字段**（详见 Migration Plan）。
