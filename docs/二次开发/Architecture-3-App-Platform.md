# Architecture-3 · App Platform（应用平台设计）

> 平台模型：**Built-in Mini App + Remote Catalog Metadata（可选）**（§5）。第一版不引入任何远程代码执行。

## 1. 三个核心概念（必须区分，§26-28）

| 概念 | 定义 | 载体（真实） |
| --- | --- | --- |
| **Registry** | 运行时能力注册（系统能跑哪些 App） | `frontend/src/apps/registry.ts`（静态） |
| **Catalog** | 用户可发现的应用目录（展示/发现/状态） | 前端 registry 元数据 + 后端 `product`（商品目录） |
| **Runtime** | App 如何运行 | **Vue Router + Built-in 组件**（§134，不是远程执行） |

- `AppDefinition.route` 必须命中**前端已知的本地路由**（§233-236）；Store/Catalog **永远不能决定执行什么代码**（§237）。

## 2. 平台分层（§280）

```
KnowFlow Core
     │
App Platform（Registry + Catalog + Runtime）
     │
App（AppDefinition）
     │
Product（商品）→ Purchase → Entitlement → UserApp → User
     │
Version（元数据）  Capability（元数据）
```

## 3. 生命周期（§40-45，MVP 收敛为三态，§41）

```
REGISTERED → ACTIVE → DISABLED → LEGACY
              （默认）   （能力不可用）  （不再推荐/不再新购/历史保留）
```

- **MVP 只保留：ACTIVE / DISABLED / LEGACY**（§41）；BETA 可选标注。
- 发布链路（DRAFT→REVIEWING→PUBLISHED 等）仅在未来第三方场景（§76），本阶段不实施。

## 4. 状态矩阵（§42-46/§43-44）

| App.status | Product.status | Entitlement | UserApp.enabled | 用户侧表现 |
| --- | --- | --- | --- | --- |
| ACTIVE | ACTIVE | ACTIVE | ENABLED | 正常可用 |
| ACTIVE | DISABLED | ACTIVE | ENABLED | 老用户可用；新用户不可购买（§44/§133） |
| ACTIVE | ACTIVE | — | — | 可购买 |
| ACTIVE | — | — | DISABLED | 已安装但停用（数据/资格保留，§88/§97） |
| LEGACY | DISABLED | ACTIVE | ENABLED | 历史用户继续可用（§45/§153） |

- **App Disabled = 能力不可用；Product Disabled = 商品不能新购买**（§46）——两者独立。

## 5. App 与 Product 关系（§32/§129）

- 一个 App → 可多个 Product：`Feynman = Free + Pro + Premium`（概念支持）。
- 当前实现：App 与 Product 通过 `product.app_key` 关联（1 App ↔ 1 Product 的 MVP 形态，未来可扩为 1:N）。
- **Entitlement 挂在 Product 上（不是 App 上）**（§33）——保持不变。

## 6. Runtime（§134-136）

- 第一版 Runtime = `AppDefinition.route → Vue Router → Built-in 组件`，**复用现有 Router**，不重写（§136）。
- 若未来需要 `AppRuntimeAdapter`（AppDefinition→Route→Component），仅作适配层，本阶段不需要（§135）。

## 7. 平台 API 边界（§197-198/§218-220）

| 域 | API | 说明 |
| --- | --- | --- |
| App 平台 | （无新增） | 元数据在前端静态 Registry；无需 runtime/update/download API（§92-94） |
| Store | products / products/{id} / owned / purchase | Architecture-2 已有，**继续复用**（§90/§104） |
| UserApp | （Architecture-3.2 视决策新增） | GET/PUT user apps（若后端化） |

- 禁止重复接口：不新增 `/apps/all`、`/apps/catalog`、`/apps/list`、`/apps/store` 多套（§219）。

## 8. 模块归属（§191-196）

- 后端保持 `reward` / `store` 模块；若 UserApp 后端化，新增 `apps`（或并入既有）模块——**不建 apps/app/platform/marketplace 四套**（§192）。
- 前端保持 `src/apps/`（registry/types）+ `src/store/`（api 层）分离；`economy/` 继续。
