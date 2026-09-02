# Architecture-2 · Implementation Phases（实施阶段）

> 每个阶段含：目标 / 改动 / 风险 / 测试。按依赖排序，P0 在前。**本报告只设计，实施等待下一条指令。**

## Architecture-2.1 · Reward Core（规则 + 引擎骨架）
- 目标：RewardRule 枚举 + RewardEngine 骨架 + LearningEvent 订阅点。
- 新增：`reward/RewardRule.java`、`reward/RewardEngine.java`（先打日志不入账）。
- 数据库：无。API：无。
- 风险：低。测试：单测（规则解析、事件过滤）。

## Architecture-2.2 · Wallet + Ledger
- 目标：wallet/reward_ledger 表 + WalletService + RewardLedgerService + LevelService + 用户注册/惰性初始化。
- 新增：3 张表（H2+MySQL 同步）、4 个 Entity/Mapper、3 个 Service；`GET /api/wallet`、`GET /api/wallet/ledger`。
- 修改：`/user/stats` 改读 wallet（修 G1）；CheckIn.vue:211 移除前端乐观累加（修 G7）。
- 风险：P0（口径切换）。测试：注册建钱包幂等；老用户惰性初始化；金额非负。

## Architecture-2.3 · App Store（Product + 列表）
- 目标：product 表 + ProductService + `GET /api/store/products`、`GET /api/store/owned`。
- 新增：product 表、Entity/Mapper、Service、Controller；种子数据（真实 App，≤5 个，§142-144）。
- 风险：P1。测试：商品列表/拥有态。

## Architecture-2.4 · Entitlement（资格）
- 目标：entitlement 表 + EntitlementService。
- 新增：表 + Entity/Mapper/Service。
- 风险：P1。测试：资格查询、唯一约束。

## Architecture-2.5 · Learning Reward Integration（学习奖励接入）
- 目标：RewardEngine 真正入账（CHAPTER_COMPLETE / QUESTION_ANSWERED / FLASHCARD_REVIEWED / MISTAKE_REVIEW / RECALL_COMPLETED / CODE_SUBMITTED / LEARNING_TASK_COMPLETE / PATH_COMPLETED / CHECK_IN）。
- 修改：仅加订阅点；不动业务代码（事件已由既有 Service 发出，§146-150）。
- 风险：P0（幂等）。测试：同事件 3 次只奖 1 次；每日上限；页面查看不奖励。

## Architecture-2.6 · Frontend Economy UI
- 目标：WalletPage（/wallet）+ Profile WalletCard + Home/LearningCenter「今日 +XP +Coin」轻量展示 + AppCenter 精选区（ProductCard）。
- 新增：`api/wallet.ts`、`api/store.ts`、`stores/wallet.ts`、`components/economy/*`、`views/WalletPage.vue`。
- 风险：P1（状态同步）。测试：余额/流水展示；深色；390px。

## Architecture-2.7 · Purchase Flow（购买闭环）
- 目标：`POST /api/store/purchase` + PurchaseService 事务（扣 Coin + Order + OrderItem + Entitlement + ledger + UserApp 通知）。
- 新增：order/order_item 表、OrderService、PurchaseService、StoreController、错误码。
- 风险：P0（并发超扣、部分成功）。测试：余额不足回滚；重复购买幂等；并发购买只成功一个。

## Architecture-2.8 · Security + Concurrency
- 目标：全链路 SecurityContext 校验、条件扣款、唯一键兜底、审计。
- 修改：Controller 统一取当前用户；无 userId 信任。
- 风险：P0。测试：越权访问被拒；并发压力。

## Architecture-2.9 · Migration（兼容与数据初始化）
- 目标：老用户 wallet 种子初始化（xp=sys_user.exp）、stats 口径切换回归。
- 风险：P1（老用户数据）。测试：老账号首次访问钱包正确；8 个 stats 调用页面无回归。

## Architecture-2.10 · Regression（全量回归）
- 目标：UI-1~UI-9 + Architecture-1 全功能回归。
- 测试：`npm run build`（vue-tsc 基线 74）、后端 `mvn test`、核心链路（学习→奖励→购买→App）。

## 总顺序（§190）
```
1 确认重复模型 → 2 Reward Domain → 3 Wallet → 4 Ledger → 5 RewardEngine
→ 6 Product → 7 Order → 8 Entitlement → 9 UserApp(复用) → 10 Store
→ 11 接 LearningEvent → 12 接 XP/Coin → 13 接 Home/LearningCenter
→ 14 接 My Page → 15 Purchase → 16 Security → 17 Concurrency
→ 18 Migration → 19 Regression
```
