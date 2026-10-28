# Architecture-3 · App Catalog（应用目录设计）

> Catalog = 用户可发现的应用；Registry = 系统可运行能力（§25-28）。两者分离。

## 1. Catalog 与 Registry 关系（真实）

- **Registry（静态，前端）**：13 个 AppDefinition = 「系统能跑什么」。
- **Catalog（展示层）** = Registry 元数据 + `product`（商品）+ `Entitlement`（拥有）+ `UserApp`（启用）→ 汇总成用户看到的「应用中心」。
- 结论：**Catalog 是逻辑概念，不是新表/新服务**——前端已由 `AppCenterPage`（我的应用/全部/付费精选）+ `storeApi.products`（含 owned）承担；后端目录数据 = `product` 表 + `ProductService`（§186）。

## 2. 目录视图（§25/§106）

| 视图 | 含义 | 真实数据来源 |
| --- | --- | --- |
| 我的应用 | installed + enabled | `stores/apps.ts`（localStorage，未来 backend user_app）+ registry |
| 全部/目录 | available（全部 App） | `registry.ts`（前端）+ `storeApi.products`（付费项） |
| 已拥有 | Entitlement ACTIVE | `GET /api/store/owned`（服务端真源） |
| 精选 | 官方推荐（静态） | `product` 表 ACTIVE 商品（前端「付费精选」区，§120） |

## 3. 分类（§66-67/§106）

统一 **应用中心** 一个入口（§141-143），内部 Tab/筛选：`精选 / 我的应用 / 学习 / AI / 工具 / 游戏`。
- 分类映射见 App-Inventory；游戏分类**只占位**（无游戏，§69）。
- 前端本地筛选即可（App 数量少，§118-119），不做搜索引擎/排行榜/评分（§121-122）。

## 4. 商品目录（§128-131/§186-187）

- Catalog 的「出售面」= Product（**Catalog 发现能力，Product 出售商品**，§128）。
- 现有 5 个付费商品（feynman_pro 等）+ 4 免费 App + 4 无商品 App——**复查结论：适合作为最终 Store 商品，无需增删**（§131-132）。
- 一个 App 多商品（Free/Pro/Premium）为概念支持，当前 1:1（§129）。

## 5. App 与 Product 聚合（§188 候选，判断：暂不需要）

- 候选聚合 `{app, product, owned, enabled}` 一次性接口——**第一版不做 BFF/聚合接口**（§189）：前端已有 registry + products + owned 三份数据可组合，避免为「好看」建新层。

## 6. Catalog 是否需要新表/新 API（§209-219）

| 项 | 结论 | 依据 |
| --- | --- | --- |
| app_listing 表 | ❌ 不建 | Registry + Product 已覆盖展示所需（§214） |
| GET /api/apps/catalog | ❌ 不建 | 前端静态 registry 已是最佳目录源（§91） |
| GET /api/store/apps | ❌ 不建 | 避免重复 product API（§90/§219） |

## 7. Store UI 最终形态（§256-263）

```
应用中心（/apps）
 ├─ 我的应用：闪卡/练习/错题/专注（installed+enabled，打开）
 ├─ 全部 + 分类筛选（学习/AI/工具）
 └─ 付费精选：费曼 Pro(200 Coin 兑换) / … （owned → 打开；余额不足 → 引导去学习）
```
- 状态文案：未购 `[200 Coin 兑换]`；已购 `[打开]`；已购停用 `[重新启用]`；Product 下架 `暂不可购买`（已拥有仍可打开，§154）；Legacy `已停止推荐`（§153）。
- 顶部轻量 Coin（`walletStore.wallet.coin`，正式 Icon credit-card，§257）。
