# Architecture-3 · 执行报告（App Store / Mini App Platform 2.0 实施）

> 实施依据：Architecture-3 已审核的 10 份架构文档。日期：2026-08-30。
> 阶段：3.1 → 3.6 全部完成。**未 commit/push**（§365/§443）。

## 1. 实施范围（§17）

| Phase | 内容 | 状态 |
| --- | --- | --- |
| 3.1 | App Registry 收敛（状态三态语义注释；确认 13 App 全 ACTIVE） | ✅ |
| 3.2 | **UserApp 后端化（核心）**：user_app 表 + OfficialAppRegistry + UserAppService + AppsController + 前端 store 重构 + localStorage 迁移 | ✅ |
| 3.3 | Catalog UI：付费精选 appKey 白名单过滤；分类沿用 Registry 唯一来源 | ✅ |
| 3.4 | Version 元数据：AppCard 显示 v1.0.0（AppDefinition.version） | ✅ |
| 3.5 | Capability：核验 13 App capabilities 已对齐设计文档（零改动，已达标） | ✅ |
| 3.6 | Regression：前端 build/vue-tsc + 后端全量 mvn test + 安全扫描 | ✅ |

## 2. UserApp 后端化（3.2）

- **数据库**：新增 `user_app` 表（uk_user_app user_id+app_key），H2 + MySQL + migration `V20260830__architecture3_user_app.sql` 三处同步（§22/§203）。
- **后端新增**（`com.knowflow.apps`）：
  - `OfficialAppRegistry`：官方 13 App 白名单 + status（全 ACTIVE，§69）——**后端 App 可用性唯一来源**（§161-163），解决 Architecture-2 遗留「APP_DISABLED 门禁」。
  - `UserAppService`：getUserApps（首次自动默认初始化 flashcard/quiz/mistake/focus，幂等 §40）/ upsert（幂等，非法 appKey 拒绝 §34/§345，非法状态组合归一化 §266-267）/ markInstalledAndEnabled。
  - `entity/UserApp` + `mapper/UserAppMapper`。
  - `controller/AppsController`：GET/PUT `/api/apps/user-apps[/{appKey}]`（最小集，§29-30；userId 全走 SecurityContext §256-260）。
  - `dto/UserAppUpdateRequest`、`vo/UserAppVO`；`EconomyError.INVALID_APP=2006`。
- **PurchaseService 增强**（§72/§150-153/§270-276）：
  - 购买前校验 `OfficialAppRegistry.isActive(appKey)` → 非 ACTIVE 抛 `APP_DISABLED`（先校验再扣款，绝不「扣了 Coin 才发现 App 停用」）。
  - 购买成功后**同事务** `markInstalledAndEnabled` → UserApp installed+enabled=true（§341），回滚时全部一致（§342）。
- **前端**：
  - `api/apps.ts`（GET/PUT）；`apps/types.ts` + UserAppVO。
  - `stores/apps.ts` 重构（§54/§225-231）：**后端真源 → Pinia 缓存 → localStorage 降级**；hydrateCache（本地立即展示 §230）→ fetchUserApps（服务端优先合并 + 本地迁移上传 §43-48/§331）；install/enable/disable 乐观更新 + 尝试 PUT（失败保留本地 + syncPending 标记 §132）；登出/切号重置（§127-128）。
  - `economy/constants.ts`：+INVALID_APP 文案；APP_DISABLED 文案改「该应用暂不可用」（§252）。

## 3. Catalog（3.3）/ Version（3.4）/ Capability（3.5）

- AppCenterPage `fetchProducts` 增加 **appKey 白名单过滤**（后端商品必须命中本地 Registry，否则不展示，§84-85/§351-352）。
- AppCard 显示 `v{{ app.version }}`（§94/§96/§243）。
- capabilities 核验：13 个 App 与设计文档 §101 完全一致（architecture 阶段已写齐），**零改动**；高危能力 0 出现（§103-104/§384）。

## 4. API Matrix（§432）

| Method | Path | Auth | Body | 说明 |
| --- | --- | --- | --- | --- |
| GET | /api/apps/user-apps | USER | - | 我的应用状态（含默认初始化） |
| PUT | /api/apps/user-apps/{appKey} | USER | {installed?, enabled?, sortOrder?} | 幂等 upsert（appKey 走路径，userId 走 SecurityContext） |
| GET | /api/store/products | USER | - | 复用（APP_DISABLED 门禁已接入购买） |
| POST | /api/store/purchase | USER | {productId} | 复用（+App 可用性校验 +UserApp 同步） |

未新增：/apps/all、/apps/catalog、/apps/install/enable/disable（§30/§82）。

## 5. 数据库 Matrix（§433）

| Table | 新增/复用 | H2 | MySQL | Index |
| --- | --- | --- | --- | --- |
| user_app | **新增** | ✅ | ✅ | uk_user_app(user_id, app_key) |
| wallet/ledger/product/order_/item/entitlement | 复用 | ✅ | ✅ | 不变 |
| app_definition/app_version/app_listing/publisher/review | **未新增**（§23） | - | - | - |

## 6. UserApp Matrix（§429）

默认初始化（首次 GET 自动创建）：flashcard/quiz/mistake/focus installed+enabled=true；其余 9 个 App 由用户操作/购买后创建（feynman 等购买 → installed+enabled=true，§270）。

## 7. App State Matrix（§430）

| App.status | Product.status | Entitlement | UserApp.enabled | 用户结果 |
| --- | --- | --- | --- | --- |
| ACTIVE | ACTIVE | ACTIVE | ENABLED | 正常可用 |
| ACTIVE | ACTIVE | - | - | 可购买（有商品时） |
| ACTIVE | DISABLED | ACTIVE | ENABLED | 老用户可用，新用户不可购买（§78） |
| DISABLED（后端注册表） | ACTIVE | - | - | **购买被拒 APP_DISABLED（§72/§151）** |
| - | - | ACTIVE | DISABLED | 已拥有但用户停用（数据/资格保留，§75/§142） |

## 8. Migration Matrix（§431）

| Local | Server | Result |
| --- | --- | --- |
| 有记录 | 无记录 | **上传创建**（§45/§331） |
| 无记录 | 有记录 | 采用 server（§47） |
| 一致 | 一致 | 保持 |
| enabled=true | enabled=false | **server 优先 false**（§46），除非用户本次显式启用 |
| API 失败 | - | 保留 local cache + syncPending（§49-52/§130-132） |

## 9. Security Matrix（§434）

| 对象 | Source of Truth | 防护 |
| --- | --- | --- |
| AppKey | 前端 registry + 后端 OfficialAppRegistry | 后端 upsert 拒绝非法 appKey（INVALID_APP，测试覆盖 §345）；前端商品 appKey 白名单过滤（§351-352） |
| Route | 前端静态 AppDefinition.route | 本地已知路由，禁止远程执行（§234-235，现状天然满足） |
| Capability | 元数据 | 不构成权限、无沙箱（§8/§51） |
| Product 价格 | 后端 product 表 | 请求体忽略 priceCoin（Architecture-2） |
| Entitlement | 后端 entitlement 表 | owned 不来自 coin>=price |
| UserApp | **后端 user_app**（本阶段） | localStorage 仅缓存（§227/§243）；userId 全 SecurityContext（§256-260） |
| AppStatus | 后端 OfficialAppRegistry | 购买门禁（APP_DISABLED） |

## 10. 测试（§374-377/§441）

| 测试 | 覆盖 | 结果 |
| --- | --- | --- |
| UserAppIntegrationTest（5 例） | 默认初始化幂等 / upsert 幂等 / 非法 appKey 拒绝 / 非法状态归一化 / 用户隔离（A/B 不串，§327） | ✅ |
| PurchaseAppDisabledIntegrationTest（1 例） | App 不可用（未知 appKey）→ APP_DISABLED + 完整回滚（Coin/Order/Entitlement/UserApp/Ledger 全不变，§336/§342） | ✅ |
| StorePurchaseIntegrationTest（扩展） | 购买后 UserApp installed+enabled=true（§341）；余额不足时 UserApp 不创建（§342） | ✅ |
| **全量 mvn test** | **71 tests / 0 failures / 0 errors**（基线 65 → +6） | ✅ |
| 前端 build / vue-tsc | build ✅；TS **74（基线持平，零新增）** | ✅ |

## 11. 偏离设计（§445 记录）

| 文档设计 | 实际 | 原因 |
| --- | --- | --- |
| APP_DISABLED 测试用 @MockBean | 改用真实 OfficialAppRegistry + 未知 appKey | @MockBean 会新建 Spring 上下文触发 MyBatis-Plus 静态配置冲突；未知 appKey → isActive=false → APP_DISABLED 语义一致 |
| 离线冲突算法 | 最小方案：乐观更新 + 失败保留本地 + syncPending 标记，下次同步 server 优先 | §133 明确不做复杂离线冲突 |
| last_open_at | 建列但不写（§209-211） | 避免每次打开写库 |

## 12. 遗留问题

1. **未实机浏览器验证**（跨设备、迁移、离线兜底）——建议 `npm run dev` 走「登录→应用中心→启用/停用→购买→打开」。
2. 前端无单测框架（package.json 无 test 脚本），migration 合并逻辑未做自动化前端测试（已由后端集成测试覆盖数据侧）。
3. 74 个历史 TS 错误未清零（非本阶段引入，§355）。
4. Reward Toast、每日上限粒度细化等仍为后续增强项。

## 13. 下一阶段建议

1. 实机验证 UserApp 跨设备同步与迁移（最重要）。
2. 前端单测框架（Vitest）补 stores/apps migration 测试。
3. 清零 74 个历史 TS 错误。
