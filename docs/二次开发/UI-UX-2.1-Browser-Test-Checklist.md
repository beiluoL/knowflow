# UI/UX 2.1 · Browser Test Checklist（真人浏览器验收清单）

> 本阶段我（AI）以 `npm run build` + `npm run test`（88/88）+ 模板结构核对完成验证门，**无法真人观察渲染像素**（沙箱 `agent-browser` 白屏）。
> 以下清单供你在本地 `npm run dev` 真实浏览器逐项走查。逐项勾选，发现问题回到代码修正后重跑测试门。

## 0. 环境
- 前端：`SERVER__PORT=8080 npm --prefix frontend run dev -- --host` → http://localhost:5173/
- 账号：admin / user1 / user2（密码 `admin123`）；H2 内存库重启回 `data.sql` 种子。

## 1. 全局导航（5 入口）
- [ ] 首页 / 学习 / 知识 / AI / 应用 五个一级入口可见且可点
- [ ] 移动端底部导航 ≤5 Tab，社区/我的 在头像菜单
- [ ] 暗色模式切换后五入口与顶栏正常

## 2. 已统一 Mini App（逐页检查 Header / Back / Context / Actions / Content / Loading / Error / Empty）
| App | 路由 | 返回 | 标题 | 上下文 | 动作 | 内容 | 备注 |
|-----|------|------|------|--------|------|------|------|
| 练习 Quiz | /learning/quiz | [ ] | [ ] | [ ]（NextAction 进入显示知识名） | [ ] | [ ] | 2.0 已接入 |
| 闪卡 Flashcard | /learning/flashcards | [ ] | [ ] | [ ] | [ ] | 4 StatCard 仍显示 | 上下文由 shell |
| 错题 Mistake | /mistakes | [ ] | [ ] | — | 开始错题练习 | [ ] | PageHeader→shell |
| 费曼 Feynman | /workbench/story | [ ] | [ ] | — | 写个故事 | eyebrow+loop-nav 保留 | h1 去重 |
| 康奈尔 Cornell | /workbench/notes | [ ] | [ ] | — | 新建笔记 | [ ] | |
| 记忆宫殿 Memory Palace | /workbench/palace | [ ] | [ ] | — | 间隔重复+新建宫殿 | [ ] | |
| 代码练习 Coding | /learning/code-practice | [ ] | [ ] | — | — | [ ] | |
| 知识图谱 KG | /learning/knowledge-graph | [ ] | [ ] | — | 工具栏留内容 | 画布全屏 | |
| 思维导图 Mindmap | /mindmap | [ ] | [ ] | — | — | 左栏+画布 | |
| 绘图 Drawing | /drawing | [ ] | [ ] | — | — | 左栏+画布 | |
| 笔记 Notes | /notes | [ ] | [ ] | — | — | 左栏+编辑 | |
| 智能写作 AI Writing | /learning/writing | [ ] | [ ] | — | 编辑器工具条留内容 | AI 编辑器 | h1 去重 |
| 专注 Focus | /learning/pomodoro | —（沉浸式） | — | — | 原 topbar | — | 有意不套壳 |

## 3. 主链路（§155）
- [ ] Home → NextAction → Mini App（如闪卡/测验，上下文正确透传）
- [ ] Mini App 完成 → LearningResultPanel（结果/奖励/状态/下一步）
- [ ] Result → NextAction（下一步可继续 / 返回首页 / 留当前页，§116）

## 4. App Store → Mini App（§156-160）
- [ ] Apps → Detail → 打开（已拥有）
- [ ] 免费 App：获取 → 打开
- [ ] 付费 App：兑换 → 打开；Coin 不足 → 引导学习（§161）
- [ ] 停用 App：重新启用

## 5. 用户切换（§162-163）
- [ ] A 退出 → B 登录，UserApp 状态不串

## 6. 响应式（§164）
- [ ] 390px：Header / Context / Content / CTA / Result 无横向溢出、按钮不被遮挡、Context 折行
- [ ] 768px：平板布局正常
- [ ] 1440px：内容不过窄（按 App 类型宽度）

## 7. Dark / Keyboard / 状态（§165-169）
- [ ] 暗色：全 App 无硬编码白底/黑字漏出
- [ ] 键盘：Tab 可达返回/动作，Enter 触发，Esc 可用，聚焦环明显
- [ ] Loading：打开有数据请求的 App 见 Skeleton/animate-pulse
- [ ] Empty：清空数据见空态
- [ ] Error：人为断开 API（停后端）见错误提示 + 重试

## 8. 回归（不退化）
- [ ] 后端 `mvn test` 71/71 不受影响（本阶段未改后端）
- [ ] `npm run test` ≥80 全绿；`npm run build` PASS；`vue-tsc` 0 错误
