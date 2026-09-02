# Architecture-5 · Learning UX（学习中心与完成面板体验）

> 日期：2026-08-30。目标（§279）：完成一个学习动作后，用户知道发生了什么、获得了什么、
> 掌握度有没有更新、下一步做什么；未安装能力能自然进入 Store，Coin 不足能自然回去学习。

## 1. Learning Center 收敛（§22-23/§225-226）

第一屏「今日行动」主流程（自上而下）：

```
标题 + 主 CTA（Resolver 驱动，与 Home 同源 §9-11）
今日完成进度条（真实 tasks）
待复习（ReviewDueCard）
薄弱知识（WeakPointCard）
当前学习路径（真实路径 + 进度）
待复习具体知识点 chips（§23：Home 只显示「待复习 5」，学习中心显示 AQS / CAS 明细）
下一步（NextActionCard，流程收尾 §225）
```

**Home = 决策（数量） / Learning Center = 计划（明细）**（§23-24）：Home 显示待复习数量，
学习中心把具体知识点做成可点击 chips（点击直达该知识点掌握度页）。

## 2. 完成面板（Result → NextAction，§76-80/§84-85/§136-138）

统一组件 `components/workflow/LearningResultPanel.vue`（不建第二套 §106）：

```
完成
本次复习 12 张 / 正确率 80% · 答对 8/10   ← 真实结果（无则不显示 §81-82）
奖励 +20 XP · +10 Coin                   ← 真实钱包 diff（Reward 后端计算 §142）
学习状态：掌握度已更新                    ← 真实事件链（无前后快照不伪造 §139-140）
下一步（NextActionCard）                 ← 用户点击才跳转（§101-104）
```

接入点：
- **Quiz（SmartQuiz）**：判分后显示结果面板（§84）—— 结果 + 奖励 + 学习状态 + 下一步（错题 → [查看错题] / 全对 → 继续探索）；`setLastCompleted('QUIZ')` 循环保护（§261-262）。
- **Flashcard（FlashCardsHub）**：会话结束（最后一张评分完成）显示结果面板（§85）—— 复习数量 + 会话奖励 diff + 下一步；`setLastCompleted('FLASHCARD')`。
- **Mistake（Mistakes）**：标记掌握 = 真实学习行为（MISTAKE_REVIEW 事件）→ 真实奖励 toast（§86，最小接入，不大改）。

## 3. Reward 反馈（§109/§138/§198-199）

- `economy/rewardToast.ts`：动作前后各拉一次真实钱包，diff 即真实奖励，toast 轻量展示「+X XP · +Y Coin」；
- **返回 RewardDiff 给完成面板复用**，保证 toast 与面板数字一致、全部来自后端（§302-305 禁止前端 credit）；
- 无前后值不伪造（§139-140）。

## 4. 掌握度展示（§16-20/§139-140）

- 不伪造 `42% → 55%`（后端完成接口未返回快照）；
- 面板只显示「掌握度已更新」（由真实 LearningEvent → Reward 链推断：有奖励即事件已触发、掌握度已重算）；
- Home 显示「薄弱知识 3 个」而非精确百分比（§21）。

## 5. Mini App 上下文（§114-117/§229-233/§234-235）

- 不新建 Context Service / 全局大对象（§235-237）；
- NextActionCard 打开 App 时经 **route query** 透传 `knowledgeId` / `knowledgeName`（§238）；
- FlashCardsHub / SmartQuiz 顶部显示「正在学习：X」上下文条（§229），可清除；无上下文则不显示；
- 当前 API 不支持按知识过滤 → 保持现状（§232-233），不做后端大重构。

## 6. CTA 文案统一（§110-112）

状态机纯函数 `components/workflow/actionState.ts`（可单测 §142-143）：

| 状态 | 文案 |
| --- | --- |
| 已拥有（installed+enabled） | [打开] |
| 已安装·停用 | [重新启用]（无需再次购买） |
| 未安装·免费 | [获取] |
| 未安装·付费·Coin 够 | [兑换 · N Coin] |
| 未安装·付费·Coin 不足 | [去学习赚取]（还差 N Coin） |
| App 平台停用 | [暂不可用] |
| 商品加载中 | [加载中…]（防止付费 App 被误当免费安装 §306-308） |

## 7. 完成页 → 下一步的闭环（§78/§100-104）

- 完成面板内嵌 NextActionCard，用户点击才跳转（§101-104，禁止自动跳转 §134）；
- 下一步可直达已装 App、可引导到 Store 兑换（`/apps?redeem=`）、Coin 不足回学习中心（§67-70）；
- 不强制回首页（§118-120），保持局部连续学习体验。
