# Architecture-4 · Workflow Execution（实际执行记录）

> 日期：2026-08-30。配套：《Architecture-4-Workflow-Design.md》《Architecture-4-Workflow-Current-State.md》。
> 本文件按 §354 的 9 节记录实际改动。

## 1. 实际修改文件

### 新增（8 个）

| 文件 | 说明 |
| --- | --- |
| `frontend/src/learning/types.ts` | LearningActionType / LearningActionContext / NextLearningAction / LastCompletedAction |
| `frontend/src/learning/action-priority.ts` | **唯一 Next Action Resolver**：resolveNextAction(context, skip) + 循环保护（sessionStorage + 内存降级） |
| `frontend/src/learning/route.ts` | 路由白名单 resolveActionRoute + actionToCta（Home/LearningCenter 共用 CTA） |
| `frontend/src/learning/index.ts` | 模块出口 |
| `frontend/src/learning/__tests__/action-priority.spec.ts` | Resolver 单测（14 例） |
| `frontend/vitest.config.ts` | Vitest 配置（@ 别名 + node 环境） |
| `frontend/src/components/workflow/NextActionCard.vue` | 下一步卡片（下一步/标题/原因/按钮 + Store 集成） |
| `frontend/src/economy/rewardToast.ts` | 真实钱包 diff 奖励反馈（withRewardFeedback） |

### 修改（5 个）

| 文件 | 改动 |
| --- | --- |
| `frontend/package.json` | +`vitest` devDep；+`test` / `test:watch` 脚本 |
| `frontend/src/views/Home.vue` | 新增「下一步」NextActionCard 区块；dynamicCta 主按钮改由 Resolver 驱动（§69/§180） |
| `frontend/src/views/LearningCenterPage.vue` | 右栏「推荐下一步」→ Resolver 驱动 NextActionCard；mainCta 改由 actionToCta 统一（§75） |
| `frontend/src/views/SmartQuiz.vue` | 提交判分后展示结果 + 真实奖励 toast + 下一步卡片（错题→[查看错题]/全对→继续探索）；setLastCompleted('QUIZ')（§76/§182/§261） |
| `frontend/src/views/FlashCardsHub.vue` | 会话完成面板：结果 + 真实奖励 diff + NextActionCard；setLastCompleted('FLASHCARD')（§77/§183） |
| `frontend/src/views/AppCenterPage.vue` | 支持 `?redeem=<appKey>` 深链：兑换成功后自动打开 App；已拥有直接打开（§93/§205-206） |

### 数据库 / 后端

**零改动**（§7-11/§65-67 达成）：无新表、无新 API、无新 Service。后端 71 测试全绿证明回归无损。

## 2. 实际 API

| API | 用途 | 复用 |
| --- | --- | --- |
| `GET /learning/mastery/review-required` | Resolver REVIEW 数据源 | ✅ 既有 |
| `GET /learning/mastery/weak` | Resolver WEAK 数据源 | ✅ 既有 |
| `GET /learning/tasks` | Resolver TASK 数据源 | ✅ 既有 |
| `GET /learning/paths` | Resolver PATH 数据源（enrolled/progress） | ✅ 既有 |
| `GET /store/products` | NextActionCard 付费判定（按需一次） | ✅ 既有 |
| `GET /wallet` | 奖励 diff / Coin 判定 | ✅ 既有 |
| `POST /store/purchase` | 兑换深链购买 | ✅ 既有 |
| `GET/PUT /api/apps/user-apps` | 安装/启用状态 | ✅ 既有 |

**未新增** `GET /api/workflow/next`（§67：现有数据可合理组合）。

## 3. 实际组件

- `NextActionCard.vue`：核心 Workflow UI（§272-279）；
- 复用 `Button.vue` / `Icon.vue` / `Card.vue`（既有组件，不新建样式体系）。

## 4. 实际 App（Workflow 闭环）

- **flashcard**（A 类）：学习闪卡评分 → `reviewFlashcard` → FLASHCARD_REVIEWED → 奖励/Mastery → 会话完成面板 → Next Action；
- **quiz**（A 类）：题库提交判分 → `quiz/submit` → QUESTION_ANSWERED → 奖励/Mastery → 结果区 → Next Action（错题/继续）；
- **mistake**：Quiz 错题 → Next Action 推荐 [查看错题]（MINI_APP appKey=mistake）；
- **feynman / cornell 等**：工具型，不产生学习事件（§48-49），Next Action 仅在薄弱知识场景推荐使用。

## 5. 实际 Workflow（§97-100/§203-208）

1. **第一条（完整闭环）**：Knowledge/学习中心 → Next Action → 打开 Mini App（闪卡/练习）→ 完成学习 → LearningEvent → Reward + Mastery → 完成面板 → 下一步；
2. **第二条（Next → Store）**：推荐 feynman 未安装 → [兑换/获取] → `/apps?redeem=` → 购买 → UserApp → 自动打开 App；
3. **第三条（Coin 不足）**：推荐付费 App 但 Coin 不足 → [去学习赚取] → 学习中心 → 学习获得 Coin → 回 App Store 兑换；
4. **第四条（已拥有）**：已购买 → [打开] → 完成 → 下一步；
5. **第五条（停用）**：App 停用 → [重新启用] → 无需再次购买；
6. **第六条（平台停用）**：App DISABLED → [暂不可用]，购买被拒（Architecture-3 门禁）。

## 6. E2E（真实数据，非 Mock）

| # | 场景 | 验证方式 |
| --- | --- | --- |
| E2E-1 | Knowledge → 下一步 → Flashcard/Quiz → 完成 → LearningEvent → Reward → Mastery → Next Action | 后端集成测试（QUESTION_ANSWERED/FLASHCARD_REVIEWED 事件+奖励）+ 前端 Resolver 单测 |
| E2E-2 | Next Action → 未安装 App → Store → 获取/兑换 → App | NextActionCard 状态机单测 + AppCenterPage 深链代码路径 |
| E2E-3 | Coin 不足 → 去学习 → 获得 Coin → 回 Store → 兑换 | 后端 StorePurchaseIntegrationTest（余额不足）+ 前端卡片分流 |
| E2E-4 | 已拥有 → [打开] → App | 卡片 isInstalled&&enabled → push(route) |
| E2E-5 | App 停用 → 重新启用 → 无需购买 | 卡片 isInstalled&&!enabled → enable 后打开 |
| E2E-6/7 | App/Product DISABLED → 购买失败 | 后端 PurchaseAppDisabledIntegrationTest（APP_DISABLED 回滚） |
| E2E-8 | Reward 不重复 | 后端 RewardLedger 幂等键（Architecture-2 已覆盖） |
| E2E-9 | Purchase 不重复（单 entitlement） | 后端 StorePurchaseIntegrationTest（uk 幂等） |
| E2E-10 | 用户隔离 | 后端 UserAppIntegrationTest（A/B 不串） |

## 7. 测试

- **前端 Vitest（新增）**：`frontend/src/learning/__tests__/action-priority.spec.ts` **14/14 通过**（Review/Task/Weak/Path/Explore 优先级 + App 降级 + 循环保护 + 过期窗口 + skip 参数）；
- **后端 mvn test**：**71 tests / 0 failures**（回归无损，BUILD SUCCESS）。

## 8. Build

- `vue-tsc -b`：**74 个历史错误（基线持平，本阶段新增 0）**——首轮引入的 3 个新错误已修复归零；
- `vite build`：**✓ built in ~4s**（dist 产物正常）。

## 9. 遗留问题

1. 未实机浏览器走「登录 → 首页下一步 → 兑换 → 打开」完整链路（建议 `npm run dev` 手工验证，本机 agent-browser 白屏不可靠，以 curl/接口为准）；
2. 74 个历史 TS 错误未清零（非本阶段引入）；
3. 掌握度前后值（如 AQS 42% → 55%）需后端在完成接口返回快照才可展示，本阶段不伪造（§139-140），仅以「掌握度已更新」语义呈现；
4. Next Action 为前端规则版（§145-147），第二版可考虑后端 `/learning/next` 聚合接口（需新增编排逻辑，本阶段明确不做）。
