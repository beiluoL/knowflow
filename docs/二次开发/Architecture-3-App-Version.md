# Architecture-3 · App Version（版本元数据设计）

> 目标：为未来第三方发布链路预留版本概念（§20/§72），**第一版只做元数据，不建表、不做下载/更新（§23/§93-94）**。

## 1. 现状（真实）

- 每个 AppDefinition 有 `version: string`（当前全为 `'1.0.0'`，前端静态）。
- 无 AppVersion 实体/表/API。

## 2. 数据模型评估（§21-24/§213）

| 项 | 第一版 | 未来（V2+） |
| --- | --- | --- |
| AppDefinition.version | ✅ 已够（展示/审计） | — |
| app_version 表 | ❌ 不建（§213） | 需要时再建 |
| 版本升级/下载/回滚 | ❌（§93-94/§252-253） | 未来 |

未来 `app_version` 候选字段（仅设计，§21）：

```sql
id / app_key / version / status / release_note / published_at
```

## 3. 版本显示（§114）

- App Detail / ProductCard 显示 `v1.0.0`（来自 AppDefinition.version，真实存在）。
- Changelog 只设计字段（release_note），本阶段不展示（§115）。

## 4. 未来第三方发布链路（§72/§281）

```
Developer → App Draft → App Version → Security Review → App Review
   → Publish → Catalog → Product → Purchase → Entitlement → UserApp
```
- 本阶段只把链路写清楚，不实施（§76/§173-177：submit/review/publish/rollback 均为 Future）。

## 5. 结论

- **AppDefinition.version 元数据 = MVP 足够**；版本表、版本 API、更新系统全部 Future（§212-213）。
- 不新增任何 API：`App Update API / Download API / Runtime API` 第一版**一律不需要**（§92-94）。
