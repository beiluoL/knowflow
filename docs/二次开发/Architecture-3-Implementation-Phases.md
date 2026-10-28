# Architecture-3 · Implementation Phases（实施阶段）

> 每阶段：目标 / 修改 / 数据库 / API / 前后端 / 风险 / 测试。**本报告只设计，实施等待指令。**

## Architecture-3.1 · App Platform 收敛（AppDefinition = Manifest）
- 目标：确认 AppDefinition 即 Manifest（元数据），registry 保持静态；状态三态化（ACTIVE/DISABLED/LEGACY）。
- 修改：前端 `apps/types.ts` 微调（可选：AppStatus 收敛注释；publisherType 概念标注 OFFICIAL）。
- 数据库：无。API：无。后端：无。
- 风险：低。测试：前端 build / vue-tsc。

## Architecture-3.2 · UserApp 后端化（P1 核心，§34-38/§155）
- 目标：`user_app` 表 + UserAppService + GET/PUT `/api/apps/user-apps`；localStorage 降级为缓存；迁移合并（服务端优先）。
- 修改：后端 +entity/mapper/Service/Controller（apps 模块）；前端 `stores/apps.ts` 改造（API→Pinia→cache）；AppCenter/Home 读取路径不变。
- 数据库：**+1 表 user_app**（H2+MySQL+migration 同步，uk user_id+app_key）。
- API：GET/PUT /api/apps/user-apps（最小集，只查/改自己）。
- 风险：P1（迁移冲突、离线兜底）。测试：迁移幂等、服务端优先合并、登出隔离、跨设备（模拟两用户）。

## Architecture-3.3 · Catalog 落地（纯前端）
- 目标：应用中心「精选/我的应用/全部 + 学习/AI/工具/游戏 分类」统一（§106/§141-143）；appKey 白名单过滤。
- 修改：前端 AppCenterPage 重构视图层；economy/constants 复用。
- 数据库：无。API：无（复用 store/products，§90）。后端：无。
- 风险：P2（UI）。测试：390px / Dark / 分类筛选 / owned 状态。

## Architecture-3.4 · App Version 元数据（展示）
- 目标：App Detail 显示 v1.0.0（AppDefinition.version）；CHANGELOG 字段只设计不展示。
- 修改：前端 App Detail 区（可选）。
- 数据库：无（§213）。API：无。
- 风险：P3。测试：展示一致性。

## Architecture-3.5 · Capability 元数据对齐
- 目标：按 App-Capability 文档核对/补齐 13 个 App 的 capabilities 声明（纯元数据，无执行）。
- 修改：前端 `apps/registry.ts`。
- 数据库：无。API：无。后端：无。
- 风险：P3。测试：类型检查。

## Architecture-3.6 · Developer Platform（**Future，不实施**）
- 目标：仅文档（publisherType、App Draft→Version→Review→Publish→Catalog 链路、Signed Package→Sandbox）。
- 不建表、不建 API、不建模块（§206-208/§255）。

## 实施顺序（§297）

```
1 App Registry 收敛（3.1）
2 UserApp 后端化（3.2）← 跨设备 + APP_DISABLED 门禁的关键
3 Catalog UI（3.3）
4 App/Product 关系复查（沿用 1:N 概念，当前 1:1）
5 Capability metadata（3.5）
6 Version metadata（3.4）
7 旧数据迁移（3.2 内：localStorage→user_app）
8 Security 加固（appKey/route 白名单复查）
9 Regression（UI-1~9 + Architecture-1/2 全量）
```

## 与 Architecture-2 遗留的关系（§298-300）

| Architecture-2 遗留 | 分类 | 处理 |
| --- | --- | --- |
| 经济闭环未实机验证 | NON-BLOCKER | 实机验证（建议 3.1 前做一次） |
| Reward Toast 未接 | NON-BLOCKER / FUTURE | 保留后续增强 |
| **APP_DISABLED 后端门禁** | **BLOCKER for 3.2** | **UserApp 后端化后解决（3.2）** |
| 每日上限粒度 | FUTURE | 不阻塞 |
| 74 个历史 TS 错误 | NON-BLOCKER（历史） | 建议独立专项 |
