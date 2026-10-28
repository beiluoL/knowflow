# Architecture-2 · Domain Boundaries（模块边界）

> 目标：一个 Wallet、一个 Ledger、一个 Product、一个 Order、一个 Entitlement（§77）。
> 边界铁律：**Reward/Store 是平台层，业务 App 一律通过 Core 能力访问，绝不直连 Wallet（§151-154）。**

## 一、分层总览

```
CORE（既有，基本不动）
 ├─ identity      User / Auth / JWT
 ├─ knowledge     doc / category / favorite / search
 ├─ learning      path / chapter / task / plan
 ├─ event         learning_event（唯一行为输入）✅ 已有
 ├─ mastery       knowledge_mastery（只读消费）
 ├─ review        SrsService / flashcard / review
 └─ ai            chat / agent / rag

PLATFORM（Architecture-1 已有，复用）
 ├─ apps          AppDefinition / AppRegistry（前端静态）
 └─ user-app      UserApp 状态（前端 localStorage；未来后端表）

REWARD（本阶段新增设计）
 ├─ WalletService     余额（xp/coin/level）读写，仅被 RewardEngine/Purchase 调用
 ├─ RewardEngine      事件 → 规则 → 入账（唯一发奖入口）
 ├─ RewardRule        静态规则（枚举/配置）
 ├─ RewardLedger      流水（append-only）
 └─ LevelService      exp → level

STORE（本阶段新增设计）
 ├─ ProductService    商品元数据（价格服务端唯一可信）
 ├─ PurchaseService   购买事务（唯一负责：扣 Coin + Order + Entitlement + UserApp）
 ├─ OrderService      订单（含价格快照）
 └─ EntitlementService 资格

MINI APPS（既有，消费 Core）
 └─ flashcard / quiz / mistake / focus / feynman / cornell / graph / coding …
```

## 二、调用方向（§89-95 分层）

```
Controller
   ↓
Application Service（RewardEngine / PurchaseService）
   ↓
Domain Service（WalletService / LevelService / OrderService）
   ↓
Repository / Mapper
   ↓
DB
```

## 三、禁止的调用（硬约束）

| 禁止 | 原因 |
| --- | --- |
| `QuizController → wallet.credit(9999)` | 任意业务直接发币（§95） |
| `FlashcardService → wallet.credit()` | 每个 App 自己发奖（§151） |
| 前端 `POST /reward {xp, coin}` | 前端提交奖励金额（§66） |
| 前端购买提交 `priceCoin` | 价格服务端唯一可信（§38） |
| 请求体传 `userId` 购买 | 必须 SecurityContext（§102） |

## 四、状态边界（§132-133）

| 对象 | 状态 | 含义 |
| --- | --- | --- |
| App（AppDefinition） | ACTIVE / BETA / LEGACY | 能力是否可用 |
| Product | ACTIVE / DISABLED | 是否可购买（DISABLED=老用户可用，新用户不可买） |
| Entitlement | ACTIVE | 拥有资格 |
| UserApp | enabled true/false | 应用启用状态（停用不删数据/资格，§47） |

## 五、与既有模块的关系

- **RewardEngine 唯一输入 = LearningEvent**（§13）：业务 Service 成功后调用 `LearningEventService.record()`（既有），RewardEngine 监听/订阅事件做奖励——不在业务代码里散插发奖。
- **Mastery 是观察对象**：奖励不修改 mastery（§131）；mastery 继续由 Phase 2 引擎驱动。
- **SRS 不动**：复习奖励只看 `FLASHCARD_REVIEWED` 事件，不碰 SM-2。
- **Architecture-1 复用**：Store 购买成功后写的是 `Entitlement` + 前端 `UserApp`（installed=true/enabled=true），不重造 App 平台（§156）。
