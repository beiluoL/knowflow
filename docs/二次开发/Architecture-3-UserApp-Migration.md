# Architecture-3 · UserApp Migration（UserApp 后端化迁移方案）

> 现状：`stores/apps.ts` 用 localStorage（key=`knowflow:apps:<userId>`）存 installed/enabled/sortOrder。
> 目标（Architecture-3.2）：后端 `user_app` 为真源，localStorage 降级为缓存（§155/§226-228）。

## 1. 为什么需要后端化（§34-35 评估）

| 需求 | localStorage 能否满足 | 后端化 |
| --- | --- | --- |
| 跨设备同步（Device A/B 状态一致，§157） | ❌ | ✅ |
| APP_DISABLED 后端门禁（Architecture-2 遗留 ③，§47-48） | ❌ | ✅（后端可判断 App 可用性） |
| 用户状态审计 / 多端一致排序 | ❌ | ✅ |
| 单机轻量使用 | ✅ | — |

**结论：UserApp 后端化为 P1 必做**（跨设备 + APP_DISABLED 门禁两个真实诉求）。

## 2. 数据模型（§38，仅新增 1 张表）

```sql
CREATE TABLE IF NOT EXISTS user_app (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    app_key VARCHAR(50) NOT NULL COMMENT 'AppDefinition.key',
    installed INT DEFAULT 1,
    enabled INT DEFAULT 1,
    sort_order INT DEFAULT 0,
    last_open_at TIMESTAMP NULL COMMENT '最近打开（未来推荐用，§159）',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    UNIQUE KEY uk_user_app (user_id, app_key)
);
```
- **唯一约束 (user_id, app_key)**（§107 幂等）；H2 + MySQL + migration 三处同步（§141/§245-246 约定）。

## 3. API（最小集，§153-154）

| Method | Path | 说明 |
| --- | --- | --- |
| GET | /api/apps/user-apps | 当前用户 App 状态列表（SecurityContext） |
| PUT | /api/apps/user-apps/{appKey} | 更新状态（body: {enabled, installed, sortOrder}） |

- 只允许改自己的（§26/§100）；不新建 install/enable/disable 三套接口（§153 最小集）。

## 4. 迁移流程（§155-156）

```
首次登录（前端）
  1. 读 localStorage（旧状态）
  2. GET /api/apps/user-apps（服务端状态）
  3. 合并规则（见下）
  4. PUT 上传合并结果（幂等 upsert）
  5. localStorage 降级为缓存（每次启动以后端为准）
```

### 合并规则（§156，冲突策略：服务端优先）

| 本地 | 服务端 | 结果 |
| --- | --- | --- |
| installed=true | 无记录 | **创建**（服务端新增，用户本地已装） |
| enabled=true | enabled=false | **服务端优先**（服务端 enabled=false → 结果 false；除非用户本次显式启用） |
| 无记录 | enabled=true | 采用服务端 |
| 两边一致 | — | 不变 |

- **首次迁移：服务端优先；无服务端记录：采用本地状态**（§156 推荐，已内嵌于上表）。
- 幂等：`INSERT ... ON DUPLICATE KEY`（或先查后插），重复调用结果一致（§109/§124）。

## 5. 兼容与回滚（§37/§292）

- **老用户本地状态不丢**：首次登录自动上传合并（§37）。
- **回滚方案**：后端 UserApp 上线失败 → 前端保留 localStorage 直读兜底（feature flag 控制走 API 还是本地），**不丢状态**（§292）。
- 本地 App 数据（闪卡/错题等业务数据）与 UserApp 状态无关，任何迁移都不触碰（§98）。

## 6. UserApp 与 Entitlement 边界（§8/§39）

- `Entitlement`（服务端）= 拥有资格（购买授予，只增不改删）。
- `UserApp`（后端化后服务端）= 启用/显示状态（可停用/重启用）。
- **停用 App = enabled=false，不删 Entitlement、不删业务数据**（§47/§88/§97）；重新启用无需再购买（§89）。
- 不能用 UserApp 替代 Entitlement（§39）。

## 7. 前端改造（§225-228）

- `stores/apps.ts`：改为「API → Pinia → localStorage cache」；localStorage 不再是真源（§226）。
- 登出/切号：清空 Pinia 状态（既有逻辑保留），后端状态按 userId 天然隔离（§291-292）。
- 离线降级：网络失败时回退本地缓存展示（读，不改写）。

## 8. 影响面

- 数据库：+1 表（user_app，P0/P1，§211/§285）。
- 后端：+1 entity/mapper + UserAppService + AppsController（GET/PUT）。
- 前端：stores/apps.ts 改造 + AppCenter/Home 读取路径不变（store 接口兼容）。
- 风险：迁移冲突（服务端优先规则）、离线兜底——均有方案。
