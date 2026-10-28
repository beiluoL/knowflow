# Architecture-2 · API Design（接口设计）

> 遵循项目 REST 风格（`/api/**`，JWT + SecurityContext，返回统一信封）。
> 原则：**用户只能查询 Wallet/Ledger/Order/Entitlement，不能创建奖励；价格/奖励金额全部服务端读取（§68-70、§102-104）。**

## 一、Wallet / Ledger API

| Method | Path | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/wallet` | 当前用户余额（xp/coin/level/nextLevelExp） | USER |
| GET | `/api/wallet/ledger?page&size&currency` | 当前用户流水（只读，§69） | USER |

- 无 `POST /api/wallet`、无 `POST /api/reward`（用户侧禁止创建奖励，§65-67）。

## 二、Store / Product API

| Method | Path | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/store/products?type=APP` | 商品列表（含拥有状态 owned） | USER |
| GET | `/api/store/products/{id}` | 商品详情 | USER |
| GET | `/api/store/owned` | 我拥有的商品（Entitlement 列表） | USER |
| POST | `/api/store/purchase` | 购买（body 只含 `productId`，**不含价格/不含 userId**，§38/§102） | USER |

- 购买响应：成功 → `{code:200, data:{orderNo, productName, coin, balanceAfter}}`；
- 失败 → 业务错误码（见下），前端展示友好文案（§136）。

## 三、App API（复用 Architecture-1，不重复建 §71）

- 前端静态 Registry + localStorage（现有）：`getApps()` 等纯函数。
- 未来跨设备才加：`GET /api/apps`、`GET /api/apps/installed`、`POST /api/apps/{key}/install|enable|disable`（§153-154 最小集）。

## 四、学习事件 API（既有，不改）

- `LearningEventService.record(...)` 由业务 Service 内部调用（§12-13）；RewardEngine 订阅同源事件，**不新增对外事件 API**。

## 五、错误码（§134-136，后端返回明确码，前端映射友好文案）

| 错误码 | 场景 | 前端文案 |
| --- | --- | --- |
| INSUFFICIENT_BALANCE | 余额不足 | 余额不足，还差 X Coin，[去学习赚取] |
| ALREADY_OWNED | 已拥有该商品 | 该应用已拥有 |
| PRODUCT_DISABLED | 商品下架 | 该应用暂时无法购买 |
| APP_DISABLED | 关联 App 停用 | 该应用暂时无法购买 |
| INVALID_PRODUCT | 商品不存在 | 商品不存在 |
| SYSTEM_ERROR | 其他 | 操作失败，请稍后重试 |

## 六、不需要新增的 API（§179）

| 项 | 结论 |
| --- | --- |
| 奖励创建/发放 | ❌（RewardEngine 内部，无对外） |
| 余额充值/提现/转账 | ❌（本阶段禁止，§58-61） |
| 排行榜/评分/评论 | ❌（§139-140 不做） |
| user_app 后端 CRUD | ❌（MVP 前端 localStorage 已够） |
| reward_rule 管理 | ❌（MVP 静态枚举） |
