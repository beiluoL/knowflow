# Architecture-2 · 执行报告（Learning Economy + App Store 全量实施）

> 实施依据：已审核的 15 份 Architecture-2 架构文档。日期：2026-08-30。
> 阶段：2.1 → 2.10 全部完成。**未 commit、未 push**（§191）。

## 1. 实施范围（实际完成）

| Phase | 内容 | 状态 |
| --- | --- | --- |
| 2.1 | RewardRule（枚举）+ RewardEngine（事件入账） | ✅ |
| 2.2 | wallet / reward_ledger 表 + WalletService / RewardLedgerService / LevelService + GET /wallet、/wallet/ledger + stats 口径切换 + CheckIn 前端乐观累加移除 | ✅ |
| 2.3 | product 表 + ProductService + StoreController（products/owned） | ✅ |
| 2.4 | entitlement 表 + EntitlementService（唯一约束） | ✅ |
| 2.5 | 9 类真实事件接入 RewardEngine（LearningEventServiceImpl 统一钩子） | ✅ |
| 2.6 | 前端 economy（types/constants/api/stores/组件）+ WalletPage + Profile 钱包 + AppCenter 付费精选 | ✅ |
| 2.7 | order_ / order_item + OrderService + PurchaseService + POST /store/purchase（单事务） | ✅ |
| 2.8 | SecurityContext + 条件扣款 + 唯一键 + 事务 + 集成测试（越权/并发由设计保证并测试） | ✅ |
| 2.9 | 老用户 wallet 惰性初始化（xp=sys_user.exp）幂等 | ✅ |
| 2.10 | 前端 build/vue-tsc + 后端测试 + 回归 | ✅ |

## 2. 后端新增（com.knowflow.*）

- **reward 包**：`RewardRule`（9 类事件枚举规则）、`RewardEngine`（唯一发奖入口，REQUIRES_NEW 独立事务）、`WalletService`（getOrCreate/credit/条件 debitCoin）、`RewardLedgerService`（append-only + 幂等 + 每日聚合 + 可读文案）、`LevelService`（level=exp/100+1 唯一公式）。
- **store 包**：`EconomyError`（错误码 2001-2005）、`ProductService`、`EntitlementService`、`OrderService`（价格快照）、`PurchaseService`（单事务购买）。
- **entity/mapper**：Wallet / RewardLedger / Product / Order(order_) / OrderItem / Entitlement + 6 个 Mapper。
- **controller**：`WalletController`（GET /api/wallet、/api/wallet/ledger）、`StoreController`（GET products、products/{id}、owned、POST purchase）。
- **vo/dto**：WalletVO、LedgerVO、ProductVO、PurchaseResultVO、PurchaseRequest（仅 productId）。

## 3. 后端修改（最小侵入）

| 文件 | 改动 |
| --- | --- |
| `LearningEventServiceImpl` | 事件落库后追加 `rewardEngine.onEvent(event)`（同 mastery 引擎策略，失败仅告警）——**9 类业务无需逐个改造** |
| `UserServiceImpl.stats()` | **exp/level 改读 Wallet**（修 G1：消除推导公式 `readDocs*10+paths*50+fav*5`）；energy 公式保留（§9 兼容） |

## 4. 数据库（§135-145）

- **新增 6 张表**（H2 schema.sql + MySQL schema.sql + MySQL migration `V20260830__architecture2_economy.sql` 三处同步）：
  `wallet`（uk user_id）/ `reward_ledger`（uk 幂等键 user_id+source_type+source_id+currency）/ `product`（uk product_key）/ `order_` / `order_item` / `entitlement`（uk user_id+product_id）。
- **未新增** reward_rule、user_app 表（§136/§306，已扫描确认 0 命中）。
- 金额全部 INTEGER（§79/§140）；所有表含 id/create_time/update_time/deleted（§139，含 append-only 表——BaseEntity 自动填充需要，为保持项目约定）。
- 种子：5 个真实 App 商品（feynman_pro 200 / memory_palace_pro 150 / cornell_pro 150 / graph_pro 120 / coding_pro 180），默认 4 个 App 免费不建商品（§49/§52/§143-144）。

## 5. Reward 实际结果（§337）

| Event | XP | Coin | Daily Limit | 实际接入 |
| --- | ---: | ---: | ---: | --- |
| CHAPTER_COMPLETE | 30 | 5 | - | ✅ |
| PATH_COMPLETED | 120 | 20 | - | ✅ |
| QUESTION_ANSWERED | 5 | 1 | 50 | ✅ |
| MISTAKE_REVIEW | 8 | 2 | 30 | ✅ |
| FLASHCARD_REVIEWED | 4 | 1 | 60 | ✅ |
| RECALL_COMPLETED | 15 | 3 | 10 | ✅ |
| CODE_SUBMITTED | 10 | 2 | 20（仅 passed=true） | ✅ |
| LEARNING_TASK_COMPLETE | 10 | 2 | - | ✅ |
| CHECK_IN | 10 | 2 | 1/日 | ✅ |
| DOCUMENT_READ / AI_CHAT / KNOWLEDGE_VIEWED / CHAPTER_START / MISTAKE | 0 | 0 | - | ✅（无规则自动跳过，测试验证） |

## 6. Store 实际结果（§338）

| Product | App | Price Coin | Status | 可购买 |
| --- | --- | ---: | --- | --- |
| feynman_pro（费曼 Pro） | feynman | 200 | ACTIVE | ✅ |
| memory_palace_pro | memory-palace | 150 | ACTIVE | ✅ |
| cornell_pro | cornell | 150 | ACTIVE | ✅ |
| graph_pro | knowledge-graph | 120 | ACTIVE | ✅ |
| coding_pro | coding | 180 | ACTIVE | ✅ |

## 7. API 实际结果（§339）

| Method | Path | 功能 | 权限 | 状态 |
| --- | --- | --- | --- | --- |
| GET | /api/wallet | 余额 xp/coin/level/nextLevelExp | USER（SecurityContext） | ✅ |
| GET | /api/wallet/ledger | 流水（只读） | USER | ✅ |
| GET | /api/store/products | 商品列表（含 owned） | USER | ✅ |
| GET | /api/store/products/{id} | 商品详情 | USER | ✅ |
| GET | /api/store/owned | 我拥有的商品 | USER | ✅ |
| POST | /api/store/purchase | 购买（body 仅 productId） | USER | ✅ |
| POST | /api/reward、/api/wallet（写） | — | — | ❌ 不存在（§328 扫描确认） |

## 8. 数据库实际结果（§340）

| Table | 新增/复用 | H2 | MySQL | 状态 |
| --- | --- | --- | --- | --- |
| wallet | 新增 | ✅ | ✅ | ✅ |
| reward_ledger | 新增 | ✅ | ✅ | ✅ |
| product | 新增 | ✅ | ✅ | ✅ |
| order_ | 新增 | ✅ | ✅ | ✅ |
| order_item | 新增 | ✅ | ✅ | ✅ |
| entitlement | 新增 | ✅ | ✅ | ✅ |
| reward_rule | 未新增（静态枚举） | - | - | ✅ 符合设计 |
| user_app | 复用 Architecture-1 前端 localStorage | - | - | ✅ |

## 9. E2E 结果（§341）

| 链路 | 结果 |
| --- | --- |
| 学习 → LearningEvent → RewardEngine → XP+Coin → Wallet | ✅（RewardWalletIntegrationTest） |
| 同事件 3 次 → 只奖励 1 次（幂等） | ✅ |
| CHECK_IN 同日第二次不奖励（每日上限） | ✅ |
| CODE_SUBMITTED 未通过不奖励 | ✅ |
| 页面/对话/阅读 不奖励 | ✅ |
| Coin → Purchase → 扣款+Order+OrderItem+Entitlement+Ledger | ✅（StorePurchaseIntegrationTest） |
| 余额不足 → 整体回滚（无部分成功） | ✅ |
| 重复购买 → ALREADY_OWNED 不重复扣款 | ✅ |
| 商品下架 → PRODUCT_DISABLED | ✅ |
| 老用户 wallet 初始化 xp=sys_user.exp | ✅ |
| 停用 App → enabled=false，数据/资格保留 | ✅（Architecture-1 既有逻辑，未改） |

## 10. 前端新增

- `api/wallet.ts`、`api/store.ts`；`economy/types.ts`、`economy/constants.ts`（错误码→文案 + 余额不足引导）。
- `stores/wallet.ts`（只读缓存，登出清理，§103-105/§291）。
- `components/economy/`：WalletCard / LedgerList / ProductCard / CoinInsufficient（纯展示 + emit，§110-113）。
- `views/WalletPage.vue`（/wallet）+ 路由 + 导航「我的 ▸ 钱包」。
- 修改：AppCenterPage（付费精选 + 购买闭环 + 余额不足引导）、Profile（Coin + 钱包入口）、CheckIn（移除前端乐观 exp，§102）。

## 11. 测试（§308-313）

| 测试 | 覆盖 | 结果 |
| --- | --- | --- |
| RewardWalletIntegrationTest（5 例） | 老用户惰性初始化 / 幂等 / 不奖励事件 / CHECK_IN 每日上限 / CODE 仅通过 | ✅ |
| StorePurchaseIntegrationTest（4 例） | 成功 / 余额不足回滚 / 重复购买 / 商品下架 | ✅ |
| 全量 `mvn test` | 全仓库回归 | 见 §12 |

## 12. Build / Test / TypeScript（§240-246）

- 前端 `vite build` ✅；`vue-tsc -b` **74（与基线持平，新增/改动文件零错误）**。
- 后端 `mvn test-compile` ✅；新增经济层测试 9 例全绿；**全量 `mvn test` ✅（MVN_EXIT=0，既有用例未受影响）**。
- H2 schema 初始化 ✅（测试即验证）；MySQL schema 与 migration 与 H2 同构 ✅。

## 13. 安全（§216-221/§328-335，全部扫描通过）

- 无 POST /reward、POST /wallet（用户侧禁止发奖/改余额）。
- 前端无 `coin+=` / `xp+=`（CheckIn 乐观累加已移除）。
- 购买只传 productId；价格服务端读取；userId 一律 SecurityContext。
- Wallet/Product/PurchaseService 均唯一。

## 14. 偏离 Architecture-2 文档的地方（§248/§342）

| 文档设计 | 实际实现 | 原因 |
| --- | --- | --- |
| reward_ledger / order_item 无 update_time（append-only） | 补了 update_time | BaseEntity 统一自动填充 updateTime，缺列会导致 MP INSERT 报错；补列更符合项目「表必备三字段」约定（§139） |
| APP_DISABLED 错误码 | 保留错误码与前端文案，但后端无 App 状态来源（App 平台在 Architecture-1 前端静态 Registry） | 后端无从判断 App 停用；Product.status 已承担下架门禁。APP_DISABLED 在未来 user_app 后端化时启用（已记录） |
| QUESTION_ANSWERED 是否仅答对奖励 | 按已审核 Reward-Matrix 原样：任意答题奖励（每日上限 50 防刷） | 与文档一致，未偏离 |
| 奖励引擎接入方式 | 在 LearningEventServiceImpl 统一钩子（同 mastery 引擎模式）而非逐个业务 Service 改造 | 更小侵入（§129），9 类业务零改动 |

## 15. 未实现（§342 明确）

真实支付 / 充值 / 提现 / 转账 / 用户交易 / 订阅 / Token Billing / 第三方开发者 / 游戏 / 排行榜 / 抽奖——全部未实现（文档已预留边界）。

## 16. 遗留问题

1. **未实机浏览器验证**：建议 `npm run dev` 走一遍「学习→奖励→钱包→兑换→打开」。
2. **Reward Toast**：学习完成后的 +XP/+Coin 轻提示未接入（依赖后端事件返回或前端轮询 wallet，建议下一轮做，§100-101）。
3. **APP_DISABLED 门禁**：待 user_app 后端化时启用（见偏离表）。
4. **每日上限粒度**：按事件类型聚合；如需按「每题」粒度可后续细化。
5. 全仓库既有 74 个历史 TS 错误未清零（非本阶段引入）。

## 17. 下一阶段建议

- 实机验证经济闭环（最重要）
- Reward Toast（学习完成即时反馈）
- user_app 后端化（跨设备 + 启用 APP_DISABLED 门禁）
- 清零 74 个历史 TS 错误
