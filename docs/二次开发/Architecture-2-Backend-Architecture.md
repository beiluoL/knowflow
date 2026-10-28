# Architecture-2 · Backend Architecture（后台架构）

> Modular Monolith（§84 不拆微服务）；在既有 `com.knowflow.*` 包上叠加两个新模块。

## 一、包结构

```
com.knowflow
 ├── controller            （既有 + 新增）
 │   ├── WalletController        GET /api/wallet, GET /api/wallet/ledger
 │   └── StoreController         GET /api/store/products, GET /api/store/owned, POST /api/store/purchase
 ├── service/              （既有）
 ├── reward/               （新增）
 │   ├── RewardEngine.java
 │   ├── RewardRule.java
 │   ├── RewardLedgerService.java
 │   ├── WalletService.java
 │   └── LevelService.java
 ├── store/                （新增）
 │   ├── ProductService.java
 │   ├── PurchaseService.java
 │   ├── OrderService.java
 │   └── EntitlementService.java
 ├── entity/               （新增 6 个）
 │   ├── Wallet.java  RewardLedger.java  Product.java
 │   ├── Order.java  OrderItem.java  Entitlement.java
 ├── mapper/               （新增 6 个 Mapper）
 └── common/               （LearningEventType 既有；新增错误码枚举 StoreErrorCode）
```

## 二、Service 职责（§85-95）

| Service | 职责 | 谁调用 |
| --- | --- | --- |
| RewardEngine | 事件 → 规则 → 入账（唯一发奖入口，§91） | LearningEvent 订阅点 |
| WalletService | 余额读取 / 内部 credit / 条件 debit | 仅 RewardEngine、PurchaseService（§92-94） |
| RewardLedgerService | append-only 流水 | RewardEngine、PurchaseService、补偿 |
| LevelService | exp → level 公式 | RewardEngine、WalletController |
| ProductService | 商品查询（价格读取） | StoreController、PurchaseService |
| PurchaseService | 购买事务（唯一负责，§90） | StoreController |
| OrderService | 订单+明细（价格快照） | PurchaseService |
| EntitlementService | 资格查询/授予 | PurchaseService、StoreController |

## 三、调用方向（§189）

```
Frontend
  ↓
Controller（薄：鉴权 + 取 userId + 参数校验）
  ↓
Application Service（RewardEngine / PurchaseService——事务边界）
  ↓
Domain Service（Wallet / Order / Entitlement / Ledger）
  ↓
Mapper / Repository
  ↓
DB
```

## 四、事务边界（§106-107）

| 操作 | 事务 |
| --- | --- |
| 奖励入账（xp+coin+ledger+level） | 单 `@Transactional` |
| 购买（扣 coin + order + item + entitlement + ledger + userApp） | 单 `@Transactional` |
| 普通查询 | 无事务 |

## 五、错误码（§134-135）

`StoreErrorCode` 枚举：INSUFFICIENT_BALANCE / ALREADY_OWNED / PRODUCT_DISABLED / APP_DISABLED / INVALID_PRODUCT / SYSTEM_ERROR → 全局异常处理器转为业务错误码（沿用项目 `BusinessException` 惯例，code 默认 500 的项目约定按既有体系走）。

## 六、不新增（§182）

无微服务 / 无 CQRS / 无 Event Sourcing / 无 Kafka / 无 Redis / 无支付对接 / 无插件沙箱 / 无第三方开发者平台。
