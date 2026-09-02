# Architecture-5 · 执行报告（Learning Experience 2.0）

> 实施依据：Architecture-5 需求（§0-290）。日期：2026-08-30。
> 执行顺序：5.1 Home → 5.2 LearningCenter → 5.3 Result/NextAction → 5.4 Context → 5.5 Today Summary → 5.6 TypeScript → 5.7 实机验证 → 5.8 回归 → **停止**（§290）。
> **未 commit / push**（§272）。

## 1. Home 重构

- **信息架构重排（§3/§58）**：欢迎横幅 → 今日学习状态 → 下一步（第一视觉 §5）→ 今日任务 → 待复习+薄弱（并排）→ 当前学习路径 → 我的应用 → 继续学习；
- **删除 5 处「虚荣/伪造」**（§53-56）：课程进度环（伪造公式）、周目标环（weekTarget=14 伪造）、「为你推荐」滚动（N+1）、「推荐学习」静态卡、「继续学习」假进度 0%；
- 主 CTA 与下一步卡片共用 `actionToCta()`（§8/§108）；数据全部并行加载（§47）；
- 文件：`frontend/src/views/Home.vue`。

## 2. LearningCenter

- 第一屏主流程收尾加入「下一步」卡片（§225）；右栏原「推荐下一步」移除（避免重复）；
- 新增「待复习」具体知识点 chips（§23：Home 只显示数量，学习中心显示明细，点击直达掌握度页）；
- mainCta 继续与 Home 同源（§9-11）；
- 文件：`frontend/src/views/LearningCenterPage.vue`。

## 3. NextAction

- 唯一 Resolver `learning/action-priority.ts` 不变（§6-7/§12）；新增 `knowledgeName` 上下文字段；
- CTA 状态机抽为纯函数 `components/workflow/actionState.ts`（§142 可单测），NextActionCard 消费（组件变薄）；
- 文案统一（§110-112）：打开 / 重新启用 / 获取 / 兑换 · N Coin / 去学习赚取 / 暂不可用 / 加载中…。

## 4. Result（完成面板）

- 新建共享 `components/workflow/LearningResultPanel.vue`（§80）：完成 / 结果 / 奖励 / 学习状态 / 下一步，垂直排列（§122）；
- Quiz 判分后 + Flashcard 会话结束后统一接入（§84-85）；Mistake 标记掌握接入真实奖励 toast（§86 最小接入）；
- 展示纯函数 `components/workflow/resultFormat.ts`（§144 可单测）：无奖励不显示、无前后快照只显示「掌握度已更新」。

## 5. Mini App Context

- `knowledgeId` / `knowledgeName` 经 route query 透传（§115-117/§238）；
- FlashCardsHub / SmartQuiz 顶部「正在学习：X」上下文条（§229），可清除、无则不显示；
- 当前 API 不支持按知识过滤 → 保持现状（§232-233）。

## 6. Today Summary

- 首页「今日学习状态」：今日任务 X/Y · 待复习 X · 薄弱 X · 当前路径 XX%（§215-216）；
- 今日奖励 +X XP · +Y Coin：`GET /wallet/ledger` 聚合（reward_ledger 真源 §38），**非前端累加（§40）**；
- 文件：`frontend/src/views/Home.vue`（loadTodayReward + 今日奖励行）。

## 7. App Store

- 保持「能力市场」定位（§25/§228），首页不再直接展示商城入口权重；
- Next Action → 未安装付费 → `/apps?redeem=<appKey>` 兑换后自动打开（Architecture-4 深链，本阶段复用）。

## 8. Wallet

- Coin/XP/Level 横幅轻量展示（§62），服务端真源（§181）；不放大为金币主页（§26/§63）。

## 9. Reward

- 后端 RewardEngine 计算不变（§182）；`rewardToast` 返回 diff 供完成面板复用；无 diff 静默（§139-140/§210）。

## 10. Mastery

- 仍为 Core Service（§183）；完成面板只显示「掌握度已更新」，不伪造前后快照（§16-20）。

## 11. Review

- 待复习为 Next Action 最高优先级（§43）；复习后 30 分钟循环保护（§260-264）。

## 12. TypeScript

- **baseline = 74 → final = 0（清零）**，new errors = 0（§265 达成并超额）；
- 全程未使用 `any` 大面积替代 / `@ts-ignore` / `@ts-expect-error` / 关闭 strict（§160-163）；
- 按模块清理：未使用导入/变量（TS6133）→ 缺失类型导入 → 类型不匹配 → 真实逻辑错误（如 `chaptersByDoc` 前端聚合、`deleteConfig`→`deleteAll`、`res.data`→`res`、`coreAnalogy`→`metaphor`、`MistakePageResult.list`→`records`）；
- 处理了 3 个 vue-tsc 模板 ref 误报（ToastHost 改内联箭头、CommandPalette/ShareCard 移除死绑定）；LeaderboardPanel 双 script 块合并怪癖 → 内联渲染组件迁入 script setup。

## 13. Vitest

- **2 个测试文件 / 27 用例全通过**（Architecture-4 的 14 + 本阶段新增 13）；
- 新增：CTA 状态机（§142-143：owned / free / paid-redeem / insufficient / disabled / enable / loading）、
  结果面板展示（§144：奖励有/无、掌握度更新判定）。

## 14. E2E

项目未安装 Playwright（§150：不强行引入）。改用：
- **Vitest**（Resolver / CTA / Result 纯逻辑 27 用例）；
- **实机 curl 链路**（§151-155，见 17）。

## 15. Build

- `vite build`：**✓ built ~3.3s**（生产产物正常）。

## 16. Backend Test

- `mvn test`：**71 tests / 0 failures / 0 errors / BUILD SUCCESS**（后端零改动，回归无损）。

## 17. 实机验证（§152-158）

启动后端 8080 + 真实数据，curl 走通：

| 步骤 | 结果 |
| --- | --- |
| 登录 admin | ✅ token 正常 |
| 闪卡列表（6 张真实卡） | ✅ |
| 闪卡评分 quality=5 | ✅（FLASHCARD_REVIEWED 事件） |
| 钱包 diff | ✅ **+4 XP +1 Coin**（Reward 真实计算） |
| 今日奖励流水 | ✅ 2 条 CREDIT（COIN 1 / XP 4，reasonLabel「复习闪卡」）→ Today Summary 数据源验证 |
| 购买 feynman（Coin 不足） | ✅ code=2001「余额不足，还差 200 Coin，去学习赚取更多金币」→ 去学习赚取引导（§91-92） |
| review-required / weak | ✅ 真实返回（新种子用户为空属正常） |
| paths / products / user-apps | ✅ 4 路径 / 5 商品（feynman 200 等）/ 4 默认应用 |

**完整学习闭环实证**：学习（闪卡评分）→ LearningEvent（FLASHCARD_REVIEWED）→ Reward（+4XP/+1Coin 落 wallet+ledger）→ Coin 不足 → 去学习赚取。

## 18. Regression（§261）

- UI-1~9 / Architecture-1~4 全部功能的展示路径未破坏（Home 删减区块均属「虚荣/伪造/静态」类，§53-56）；
- 核心算法未动：Mastery / SRS / Reward / LearningEvent（§367）；Quiz / Flashcard / Mistake / Knowledge / Graph 未重写（§368）；
- 前端 vue-tsc 0 错误 + vitest 27 绿 + vite build ✓；后端 71 绿。

## 19. 遗留问题

1. **浏览器手工验证未做全**：本机 agent-browser 白屏不可靠，实机验证以 curl 链路为准；
   建议用户 `npm run dev` 走「登录 → 首页下一步 → 打开闪卡 → 评分 → 完成面板下一步 → 兑换费曼」完整 UI 流（§153）；
2. 掌握度前后快照（42% → 55%）仍需后端在完成接口返回，本阶段不伪造（§139-140）；
3. Quiz 题库种子为空（H2 种子无已发布题目）→ 实机验证改走闪卡闭环；生产环境有真实题目即可走 Quiz 闭环；
4. `chaptersByDoc` 为前端聚合实现（后端无独立端点），仅文档详情「进入章节学习」按需触发；
5. E2E 自动化（Playwright）未引入（§150：不强行），后续需要时再补。

---

## 最终产品验收（§279）

| 问题 | 回答 |
| --- | --- |
| 打开首页知道下一步？ | ✅ 第一视觉即「下一步」卡片 + 横幅主 CTA 同源 |
| 能一键开始？ | ✅ NextActionCard [开始/打开/继续学习] 直达 |
| 完成后知道发生了什么？ | ✅ 完成面板：结果 + 奖励 + 学习状态 + 下一步 |
| 知道获得什么？ | ✅ 真实钱包 diff（+X XP · +Y Coin） |
| 知道掌握度更新？ | ✅ 「学习状态：掌握度已更新」（真实事件链，不伪造快照） |
| 知道下一步？ | ✅ 完成面板内嵌 NextActionCard |
| 未安装能力自然进 Store？ | ✅ [兑换 · N Coin] → `/apps?redeem=` → 购买后自动打开 |
| Coin 不足自然回学习？ | ✅ [去学习赚取] → 学习中心（§92） |
| 购买后自然进入 App？ | ✅ redeem 深链兑换成功自动打开 |
| 退出回来能继续？ | ✅ 后端真源恢复（user_app/wallet/entitlement）+ Resolver 循环保护 |
