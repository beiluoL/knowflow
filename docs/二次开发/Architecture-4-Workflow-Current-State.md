# Architecture-4 · Workflow Current State（数据来源盘点）

> 日期：2026-08-30。本文件是 Architecture-4 第一阶段扫描结果（§15-18），
> 盘点「学习中心 / 下一步推荐」当前已有的能力、数据来源、页面、API 与可复用性。
> 结论先行：**Review / Weak / Task / Path / LearningEvent / Reward / Mastery / UserApp 数据全部真实存在且可复用，
> Workflow 不需要新增任何表与微服务（§7-11）。**

## 1. 盘点方法

- 读取 Architecture-3 执行报告与 10 份设计文档；
- 扫描 `frontend/src/views/LearningCenterPage.vue`、`Home.vue`、`SmartQuiz.vue`、`FlashCardsHub.vue`、`AppCenterPage.vue`；
- 扫描 `frontend/src/api/learning.ts`、`stores/apps.ts`、`stores/wallet.ts`、`economy/types.ts`；
- 扫描后端 `LearningEventServiceImpl`（事件+奖励钩子）、`RewardRule`、`LearningServiceImpl`（enrolled/progress 填充）。

## 2. 数据来源矩阵（§18 格式）

| 当前能力 | 数据来源 | 页面 | API | Service | 是否可复用 |
| --- | --- | --- | --- | --- | --- |
| 今日待复习 | `knowledge_mastery`（learningStatus=REVIEW_REQUIRED） | Home / LearningCenter（ReviewDueCard） | `GET /learning/mastery/review-required` | KnowledgeMasteryService | ✅ 复用 |
| 薄弱知识点 | `knowledge_mastery`（learningStatus=WEAK，weaknessTypes） | Home / LearningCenter（WeakPointCard） | `GET /learning/mastery/weak` | KnowledgeMasteryService | ✅ 复用 |
| 全部掌握度 | `knowledge_mastery` | Home / MasteryDashboard | `GET /learning/mastery` | KnowledgeMasteryService | ✅ 复用 |
| 今日任务 | `learning_task`（status 0/1） | Home / LearningCenter / TaskCenter | `GET /learning/tasks` | LearningTaskService | ✅ 复用 |
| 当前学习路径 | `learning_path` + `learning_user_path`（enrolled/progress 已填充） | LearningCenter / PathDetail | `GET /learning/paths` | LearningServiceImpl（已登录批量填充 enrolled/progress） | ✅ 复用 |
| 到期闪卡 | 闪卡 SRS（due 过滤） | FlashCardsHub「推荐复习」 | `GET /learning/flashcards/due` | FlashcardService | ✅ 复用 |
| 学习行为事实 | `learning_event`（追加写） | LearningEvents | `GET /learning/events` | LearningEventService | ✅ 复用 |
| 学习奖励 | `wallet` + `reward_ledger`（append-only） | WalletPage | `GET /wallet` | WalletService / RewardEngine | ✅ 复用 |
| 用户应用状态 | `user_app`（后端真源） | AppCenterPage / Home 我的应用 | `GET/PUT /api/apps/user-apps` | UserAppService | ✅ 复用 |
| 付费商品 | `product`（5 个付费 App） | AppCenterPage 付费精选 | `GET /store/products` / `POST /store/purchase` | StoreService / PurchaseService | ✅ 复用 |
| 钱包余额 | `wallet` | AppCenterPage / WalletPage | `GET /wallet` | WalletService | ✅ 复用 |

## 3. 「下一步」现在在哪里计算（§16-17）

| 位置 | 现状 | 问题 |
| --- | --- | --- |
| Home `dynamicCta` | 手写规则：继续上次文档 → 待复习 → 待办 → 学习中心 | 与 LearningCenter `mainCta` 两套规则，不统一（§69） |
| LearningCenter `mainCta` | 手写规则：待复习 → 路径 → 任务 → 浏览路径 | 同上 |
| LearningCenter 右栏「推荐下一步」 | 静态推荐（继续路径/闪卡/练习/AI），**非数据驱动** | 与 Review/Weak 卡片重复、无原因、无循环保护 |
| Home 右区「推荐学习」 | 静态 aiRecs（闪卡/代码/文档/AI） | 静态，非真实数据 |

**结论**：存在「多处各自推荐」的历史状态，Architecture-4 建立**唯一 Resolver**（`learning/action-priority.ts`）统一优先级。

## 4. App → LearningEvent 现状分类（§38-40）

| App | key | 分类 | 真实学习行为 | LearningEvent | Reward | Mastery |
| --- | --- | --- | --- | --- | --- | --- |
| 闪卡 | flashcard | **A** | 评分（SM-2） | FLASHCARD_REVIEWED ✅ | 4XP/1Coin（日限60）✅ | 后端引擎更新 ✅ |
| 练习 | quiz | **A** | 提交判分 | QUESTION_ANSWERED ✅ | 5XP/1Coin（日限50）✅ | 后端引擎更新 ✅ |
| 错题 | mistake | **A** | 标记掌握 | MISTAKE_REVIEW ✅ | 8XP/2Coin（日限30）✅ | 后端引擎更新 ✅ |
| 记忆宫殿 | memory-palace | **A** | 主动回忆会话完成 | RECALL_COMPLETED ✅ | 15XP/3Coin（日限10）✅ | 后端引擎更新 ✅ |
| 代码练习 | coding | **A** | 提交代码 | CODE_SUBMITTED ✅（passed 才奖励） | 10XP/2Coin（日限20）✅ | 后端引擎更新 ✅ |
| 专注 | focus | B | 番茄钟会话 | 无对应事件类型（§46 不强加） | - | - |
| 费曼 | feynman | D | 故事保存（内容型） | 无（§48 不因保存发奖励） | - | - |
| 康奈尔 | cornell | D | 笔记保存（内容型） | 无 | - | - |
| 知识图谱 | knowledge-graph | C | 查看图谱 | KNOWLEDGE_VIEWED（§47 不发奖励） | 无规则 | - |
| 思维导图 | mindmap | C | 工具 | 无 | - | - |
| 绘图 | drawing | C | 纯工具 | 无（§40 不强行定义） | - | - |
| 智能写作 | ai-writing | C | AI 工具 | 无（§49 不因点击生成发奖励） | - | - |
| 笔记 | notes | C | 工具 | 无 | - | - |

**A 类（闭环最完整）**：flashcard / quiz / mistake —— 作为本阶段 MVP Workflow 首选（§96/§202）。

## 5. 已确认可复用链路（后端，无需改动）

```text
Quiz submit        → QUESTION_ANSWERED → RewardEngine.onEvent（同事务）→ wallet/ledger → Mastery 引擎
Flashcard review   → FLASHCARD_REVIEWED → RewardEngine.onEvent → wallet/ledger → Mastery 引擎
Mistake markMastered → MISTAKE_REVIEW → RewardEngine.onEvent → wallet/ledger → Mastery 引擎
Purchase           → 校验 isActive → 扣款 → Order/Entitlement → UserApp(installed+enabled) 同事务
```

## 6. 缺口（本阶段要补的）

| 缺口 | 本阶段方案 |
| --- | --- |
| 无统一 Next Action 决策入口 | 前端 Resolver `learning/action-priority.ts`（§70/§177） |
| 完成页无「下一步」 | Quiz 结果区 / 闪卡会话完成面板接入 NextActionCard（§76-78） |
| 推荐到未安装 App 无获取/兑换引导 | NextActionCard Store 集成（§87-92/§123-126） |
| 无循环保护 | `lastCompleted`（会话级 sessionStorage，§260-264） |
| 无轻量奖励反馈 | `economy/rewardToast.ts` 钱包 diff（§109/§198） |
