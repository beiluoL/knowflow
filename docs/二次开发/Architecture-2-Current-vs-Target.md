# Architecture-2 · Current vs Target（现状 vs 目标）

## 一、当前架构（真实）

```
Vue3 前端
 ├─ /apps App Center（Architecture-1：静态 Registry + localStorage，13 个内置 App）
 ├─ Home / LearningCenter / Profile / CheckIn（消费 /user/stats 推导的 exp/level/energy）
 └─ 8 个页面调用 userApi.stats()

Spring Boot 后端（Modular Monolith, com.knowflow.*）
 ├─ identity / knowledge / learning / mastery / event / review / ai / social …
 ├─ learning_event 表 + LearningEventService.record()（20 种事件，事实层）✅
 ├─ achievement → sys_user.exp 入账 ✅（唯一完整奖励闭环）
 ├─ code_challenge → sys_user.exp 入账 ✅（无流水）
 ├─ check_in → user_check_in（reward_exp/energy 入库但 ⚠️ 不入 sys_user）
 └─ /user/stats → ⚠️ 推导口径（readDocs*10 + paths*50 + fav*5），与 sys_user.exp 不一致

数据库（H2=MySQL 同构，77 张表）
 └─ 无 wallet / reward_ledger / product / order / entitlement / user_app（DB）
```

**两个核心缺口：**
1. **XP 口径分裂**：`sys_user.exp`（入账）vs `/user/stats`（推导）是两套数。
2. **没有任何经济层**：无 Coin、无 Wallet、无 Ledger、无 Store、无购买。

## 二、目标架构

```
                        KnowFlow
             ┌──────────────┼──────────────┐
           CORE           APPS          STORE
   identity/knowledge   Mini Apps      product
   learning/mastery     (13 内置)      order
   event/review/ai                     entitlement
             └──────────────┼──────────────┘
                            ↓
                      LearningEvent（唯一行为输入）
                            ↓
                       RewardEngine
                     ┌──────┴──────┐
                     ↓             ↓
                    XP            Coin
                     ↓             ↓
                   Level         Wallet
                                   │
                                   ↓
                                App Store
                                   ↓
                                 Product → Purchase → Entitlement → UserApp → Mini App
```

## 三、差距清单（Gap）

| # | 差距 | 现状 | 目标 | 方案 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| G1 | XP 口径分裂 | sys_user.exp ≠ stats.exp | 唯一入账口径 + Wallet 展示 | 新增 `wallet`（xp/coin 余额）+ 统一读取；stats 改读 wallet | P0 |
| G2 | 打卡不入账 | reward 只进 user_check_in | 打卡事件 → RewardEngine → Wallet | CHECK_IN 事件接入奖励 | P0 |
| G3 | 无 Coin | — | 平台内消费积分 | 新增 `wallet` 表 coin 字段 | P0 |
| G4 | 无 Ledger | exp 变化不可审计 | 每笔 +/-(xp/coin) 有流水 | 新增 `reward_ledger`（append-only） | P0 |
| G5 | 无 Store | — | Product/Order/Entitlement | 新增 3 张表 + 购买事务 | P0 |
| G6 | UserApp 仅前端 | localStorage | 可演进后端 user_app | MVP 保持前端；未来加表（§108 预留） | P1 |
| G7 | 前端乐观加 exp | CheckIn.vue:211 内存累加，刷新丢 | 后端入账后前端读真实值 | 奖励引擎入账后，前端不再本地改 exp | P1 |
| G8 | 成就/挑战入账无统一入口 | 两处散写 sys_user.exp | 统一走 WalletService | 改造两处调用点（最小改动） | P1 |
| G9 | 无 Level 公式 | level=exp/100+1 仅 stats 内 | LevelService 统一 | 新增 LevelService（或配置化公式） | P2 |

## 四、复用与不新建（§181）

- ✅ 复用：`learning_event`（事件底座）、`achievement/user_achievement`（成就闭环）、`user_check_in`（打卡事实）、Architecture-1 `apps/registry.ts` + `stores/apps.ts`（App 平台）。
- ➕ 新增（MVP 必须）：`wallet`、`reward_ledger`、`product`、`order`、`order_item`、`entitlement`（6 张表，必要性见 Data Model）。
- ➖ 明确不做：支付/充值/提现/转账/用户交易/抽奖/第三方开发者/Token Billing（§58-61、§140、§185）。
