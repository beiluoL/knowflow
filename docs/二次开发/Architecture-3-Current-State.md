# Architecture-3 · Current State（现状报告）

> 基于真实源码核验（frontend/src + backend/src/main/java + resources/db，2026-08-30）。
> 本阶段只扫描设计，零代码改动。

## 1. 当前 App Registry（Architecture-1，前端静态）

- `frontend/src/apps/types.ts`：`AppDefinition` 已有 11 字段（key/name/description/icon/category/categoryLabel/route/version/status/defaultInstalled/capabilities）——**已是 Manifest 雏形**；`UserAppState`（installed/enabled/sortOrder）；**`MiniAppContext` 已存在**（types.ts:70，仅类型契约）。
- `frontend/src/apps/registry.ts`：13 个内置 App + `CATEGORY_ORDER` / `CATEGORY_LABELS` / `DEFAULT_APP_KEYS` + 纯函数查询。
- `frontend/src/stores/apps.ts`：Pinia，`localStorage` key=`knowflow:apps:<userId>`，幂等 install/enable/disable，登出重置。
- 结论：**Registry = 运行时能力注册（静态 Built-in），已成立。**

## 2. 当前 Mini Apps（13 个，全部真实）

flashcard / quiz / mistake / focus（默认安装）+ feynman / cornell / memory-palace / knowledge-graph / coding / mindmap / drawing / ai-writing / notes（可安装）。route 均与 `router/index.ts` 核对一致。

## 3. 当前 Store（Architecture-2，后端）

- `com.knowflow.store`：ProductService / EntitlementService / OrderService / PurchaseService / EconomyError。
- API：`GET /api/store/products`（含 owned）、`GET /api/store/products/{id}`、`GET /api/store/owned`、`POST /api/store/purchase`（仅 productId，服务端定价）。

## 4. 当前 Product（5 个，真实种子）

feynman_pro(200) / memory_palace_pro(150) / cornell_pro(150) / graph_pro(120) / coding_pro(180)，均 ACTIVE，type=APP，app_key 指向真实 App。

## 5. 当前 Order / OrderItem

`order_` / `order_item` 表 + OrderService（价格快照、order_no 唯一）。购买 = PurchaseService 单事务。

## 6. 当前 Entitlement

`entitlement` 表（uk user_id+product_id），EntitlementService.hasActive/grant/listOwned。**Entitlement = 拥有资格（服务端真源）。**

## 7. 当前 UserApp

**仍为前端 localStorage**（Architecture-1 原样）；无后端表、无后端 API。**这是 Architecture-3 最大的迁移点。**

## 8. 当前 Route

- App 主入口 route 在静态 `AppDefinition.route`（前端已知、Built-in 组件）。
- `/apps` = 应用中心（AppCenterPage，含 Architecture-2 付费精选 + Coin 余额不足引导）。
- `/wallet` = 钱包页。

## 9. 当前 App 状态

`AppDefinition.status`：ACTIVE（13 个全 ACTIVE，无 BETA/LEGACY）；`Product.status`：ACTIVE/DISABLED；`UserApp.enabled`：本地布尔。

## 10. 当前 App 版本

仅 `AppDefinition.version = '1.0.0'` 元数据（前端静态）；**无版本表、无版本 API**。

## 11. 当前权限

- 后端：Spring Security + JWT（既有）。
- App 能力：`AppDefinition.capabilities`（**仅元数据声明**，无沙箱执行，无权限联动）。
- 明确缺口：**后端无法判断「App 是否停用」**（App 平台在前端）→ `APP_DISABLED` 门禁（Architecture-2 遗留）待 Architecture-3 解决。
