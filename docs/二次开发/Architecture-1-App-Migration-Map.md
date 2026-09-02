# Architecture-1 · App Migration Map（功能迁移表）

> 迁移原则：旧功能 → Mini App Definition，**不改变既有业务数据与 URL**（§90/§135）。
> 「卸载」= 停用/隐藏，绝不删除数据（§29/§125）。

## 一、迁移总表

| 旧功能 | 归类 | App Key | 新入口 Route | 旧 Route（不变） | 默认安装 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| 闪卡 | Mini App | flashcard | `/learning/flashcards` | `/learning/flashcards` | ✅ | ACTIVE |
| 智能测验 | Mini App | quiz | `/learning/quiz` | `/learning/quiz` | ✅ | ACTIVE |
| 错题本 | Mini App | mistake | `/mistakes` | `/mistakes` | ✅ | ACTIVE |
| 番茄钟 | Mini App | focus | `/learning/pomodoro` | `/learning/pomodoro` | ✅ | ACTIVE |
| 费曼故事 | Mini App | feynman | `/workbench/story` | `/workbench/story` | ❌ | ACTIVE |
| 康奈尔笔记 | Mini App | cornell | `/workbench/notes` | `/workbench/notes` | ❌ | ACTIVE |
| 记忆宫殿 | Mini App | memory-palace | `/workbench/palace` | `/workbench/palace` | ❌ | ACTIVE |
| 知识图谱 | Mini App | knowledge-graph | `/learning/knowledge-graph` | `/learning/knowledge-graph` | ❌ | ACTIVE |
| 代码练习 | Mini App | coding | `/learning/code-practice` | `/learning/code-practice` | ❌ | ACTIVE |
| 思维导图 | Mini App | mindmap | `/mindmap` | `/mindmap` | ❌ | ACTIVE |
| 绘图 | Mini App | drawing | `/drawing` | `/drawing` | ❌ | ACTIVE |
| 智能写作 | Mini App | ai-writing | `/learning/writing` | `/learning/writing` | ❌ | ACTIVE |
| 笔记 | Mini App | notes | `/notes` | `/notes` | ❌ | ACTIVE |
| 知识库/文档/AI 问答/学习中心等 | Core | — | — | 不变 | — | — |

## 二、导航迁移

| 旧导航 | 新导航 | 说明 |
| --- | --- | --- |
| 学习 ▸ 闪卡 / 智能测验 / 错题本 / 番茄钟 | 学习 保留原项 + **应用 ▸ /apps** | 原二级项保留（§136），同时新增「应用」顶级入口（§48-50，不把 13 个 App 全部展开到侧栏） |
| — | 首页 ▸ 我的应用（最多 6 个，Registry 驱动） | §93/§162 |
| — | /apps 应用中心（我的应用/全部 + 分类筛选） | §31/§100 |

## 三、旧 URL 兼容清单（全部不变，无需 redirect）

```
/learning/flashcards  /learning/quiz  /mistakes  /learning/pomodoro
/workbench/story      /workbench/notes /workbench/palace  /learning/knowledge-graph
/learning/code-practice  /mindmap  /drawing  /learning/writing  /notes
```

以上 URL 全部保持可访问（§135/§149 Deep Link 直达，不强制经过 /apps）。

## 四、新用户 / 历史用户

| 场景 | 行为 |
| --- | --- |
| 新用户首次进入 | 首次 hydrate 时自动安装默认 4 个 App（闪卡/练习/错题/专注），幂等（§88/§124） |
| 历史用户 | 首次进入同样补齐默认 4 个 App；`localStorage` 中已有设置**绝不覆盖**（§110） |
| 停用某 App | 仅 `enabled=false` 隐藏；学习数据（闪卡/错题/复习等）全部保留（§29/§125） |
| 重新启用 | `enabled=true`，数据恢复可见（§30） |
| 用户切换/登出 | App 状态按 userId 隔离重置，不串号（§194-195） |
