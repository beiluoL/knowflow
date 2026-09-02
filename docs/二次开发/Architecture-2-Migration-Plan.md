# Architecture-2 · Migration Plan（迁移方案）

> 原则：**保留 → 迁移 → 兼容 → 废弃**（§192）；旧数据不删；新架构只叠加经济层。

## 一、迁移总表

| 旧功能/字段 | 新归属 | 是否修改 | 是否兼容 | 数据迁移 |
| --- | --- | --- | --- | --- |
| `sys_user.exp`（成就/挑战已入账） | wallet.xp | ✅ 读口径切换 | ✅ 保留字段 | **无需迁移**：wallet 初始化时以 `sys_user.exp` 为种子余额 |
| `sys_user.level` | wallet.level（LevelService 计算） | ✅ 统一公式 | ✅ | 无需迁移 |
| `sys_user.energy` | wallet 或保留原字段 | ⚠️ 待定 | ✅ | 精力不在 MVP 经济闭环，保留原字段 |
| `user_check_in.reward_exp/reward_energy` | 经 CHECK_IN 事件入账 wallet | ✅ 修复 G2 | ✅ 表不变 | 无需迁移（历史打卡不再补发，仅新打卡入账） |
| `achievement → exp` | 继续走既有闭环，可选同步 ledger | ⚠️ P1 | ✅ | 无需 |
| `code_challenge → exp` | 同上 | ⚠️ P1 | ✅ | 无需 |
| `/user/stats` 推导公式 | 改读 wallet | ✅ 修 G1 | ✅ 前端字段名不变（xp/level/energy 照旧），只换数据来源 | 无需 |
| Architecture-1 App 状态（localStorage） | UserApp（未来后端化） | ✅ MVP 不动 | ✅ | 无需 |
| 旧 URL / 旧导航 | 不变 | ❌ | ✅ | 无 |

## 二、明确「无需迁移」的部分

- 无旧 Coin/Wallet/Ledger/Order/Entitlement 数据（本来就不存在，§191）。
- 无旧奖励流水（exp 变化无历史流水，无法回补，不伪造）。

## 三、兼容策略

| 项 | 策略 |
| --- | --- |
| 老用户 | wallet 首次访问时惰性初始化：`INSERT wallet(user_id, xp=sys_user.exp, coin=0, level=按公式)`，幂等（§110） |
| 新用户 | 注册时建 wallet（xp=0, coin=0, level=1） |
| 旧页面 | `/user/stats` 返回字段名不变，前端 8 个调用方零改动（只换后端数据源） |
| 旧 App 免费 | flashcard/quiz/mistake/focus 不建收费商品（§49） |
| 停用 App | 不动数据/资格（§47） |

## 四、回滚

- 全部改动叠加式：新增表/API/字段，不删旧字段 → 回滚 = 关停新 API + 恢复 stats 旧逻辑，零数据损失。
