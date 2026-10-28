# Architecture-2 · Security（安全设计）

> 铁律：**后端是奖励与价格唯一可信来源**（§67/§103-104）；**只查自己的数据**（§97-101）；**userId 一律来自 SecurityContext**（§102）。

## 一、身份与授权（既有，复用）

- Spring Security + JWT + SecurityContext（既有，不动）。
- 新 API 全部 `requiresAuth`（USER 角色即可）；无新增 Admin 权限点。

## 二、数据隔离（§97-101）

| 资源 | 查询规则 |
| --- | --- |
| wallet | 只能查 `SecurityContext.userId` 自己的余额 |
| reward_ledger | 只能查自己 |
| order / order_item | 只能查自己 |
| entitlement | 只能查自己 |
| user_app（未来后端化） | 只能读写自己 |

所有查询强制 `where user_id = 当前用户`；**禁止信任请求体中的 userId（§102）**。

## 三、奖励安全（§65-67）

| 威胁 | 防护 |
| --- | --- |
| 前端提交奖励金额 | ❌ 无对外发奖 API；金额只来自 RewardRule（§66） |
| 刷奖励 | 事件必须来自业务成功路径（LearningEvent），无伪造入口 |
| 重复奖励 | ledger 唯一键 + 每日上限（§62-64） |
| 重放请求 | 幂等键（source_id）天然防重放 |

## 四、价格安全（§38/§103）

- 前端提交 `priceCoin` 一律忽略；购买时服务端从 `product.price_coin` 读取。
- 商品下架（PRODUCT_DISABLED）拒绝购买，但已拥有者不受影响（§133）。

## 五、钱包安全（§92-95）

- `WalletService.credit/debit` 仅 `RewardEngine` 与 `PurchaseService` 可调用（package-private 或内部接口）。
- 普通 Controller 无法自由调用 `wallet.credit(9999)`（§95）。
- Coin 余额不允许为负：扣款用条件更新 `coin >= ?`（§27/§105）。

## 六、并发安全（§28/§105）

| 场景 | 机制 |
| --- | --- |
| 并发奖励同一事件 | reward_ledger 唯一索引（user_id, source_type, source_id, currency）兜底 |
| 并发购买 | `UPDATE wallet SET coin=coin-? WHERE user_id=? AND coin>=?` 条件更新 |
| 购买事务 | 单 `@Transactional`，部分失败整体回滚（§40） |

## 七、权限与 App enable 分离（§87/§117）

- `enabled` 只是用户界面状态，**不构成任何后端权限**。
- 敏感能力（Admin/Community/Coding 等）继续走既有 Spring Security 权限（§188），与 App Store 无关。

## 八、不引入（§182）

无 Redis 锁 / 无 MQ / 无分布式事务——Modular Monolith 单库单事务即可满足 MVP。
