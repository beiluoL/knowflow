# knowflow · AI 知识库与学习平台

一个面向学习场景的**知识库 + AI 学习平台**：用户可浏览 / 搜索 / 上传文档，借助 AI 对话答疑，并沿着系统化的学习路径（章节 + 闪卡 + 测验 + 写作 + 代码练习）持续学习，辅以等级、经验、能量、连续打卡等游戏化激励；管理员可在后台统一管理用户、文档、知识库与 AI 配置。

> 仓库名 `knowflow`（knowledge + flow）。代码中包名、表名等沿用 `knowflow` 前缀，与仓库名一致。

## 功能特性

- **用户与鉴权**：注册 / 登录（JWT + 登出令牌黑名单）、GitHub/微信 OAuth 第三方登录、个人资料；成长体系含等级、经验、能量、连续打卡天数（均基于真实数据聚合）。
- **文档与知识库**：文档浏览 / 详情 / 多级分类 / 全文搜索 / 上传；收藏与阅读进度（百分比 / 时长 / 上次阅读时间）；文档支持 **AI 生成摘要** 与 **AI 一键生成闪卡**（落库可复习）。
- **知识库协作**：知识库支持 **OWNER / EDITOR / READER 三级成员权限**、搜索邀请已注册用户或邮箱邀请；支持批量导入文档（.zip/.md/.txt/.json）与导出知识库为 ZIP。
- **AI 对话**：多轮会话管理、RAG 检索增强（回答附带文档引用可溯源）、**多模型切换**；Markdown 渲染（表格/代码块/引用块）。
- **用户自配大模型**：用户可在 AI 设置中输入自己的 API Key（支持 DeepSeek/硅基流动/OpenAI/通义千问/阿里云百炼/智谱AI/月之暗面/字节豆包/腾讯混元/百度文心/Anthropic/自定义），也可使用平台订阅模型；配置持久化（`sys_user_ai_config`），Key 返回时脱敏。
- **学习中心**：学习路径与章节（可挂载文档与闪卡，完成章节幂等计数）、闪卡复习（**SM-2 间隔重复算法**）、学习任务（经验奖励 / 能量消耗）、复习计划、沉浸式学习模式、番茄钟；**AI 个性化学习路径**（按目标/水平/时长生成并持久化缓存，可「采用」落地为真实路径 + 章节并自动报名跟踪进度）。
- **错题本**：测验答错 / 闪卡「不认识」自动归集（同题幂等去重），支持标记掌握、分类筛选与统计（本周新增 / 今日待复习）。
- **代码练习**：内置代码题库（JavaScript/TypeScript/Python/Java/SQL），在线 Playground 做题、测试用例校验、提交/通过统计；管理端题库 CRUD 与发布/下架。
- **智能测验与写作**：多题型题库（单选/多选/填空/判断/简答）+ **AI 按知识库/文档出题**；智能写作含 AI 评分反馈、实时预览、导出 PDF / Markdown。
- **数据可视化**：学习热力图（GitHub 式按日活跃度）、掌握分布看板（闪卡/错题掌握度）、**知识图谱**（分类/文档层级图、技术栈依赖图、概念图解、**AI 抽取的实体关系图**等多视图 SVG 可视化）。
- **社区与消息**：帖子 / 评论 / 点赞（幂等切换）、精华帖；消息通知中心（未读数 / 单条已读 / 一键全部已读）；即时通讯（学习小组群聊 + 单聊私信，含 @提及 / 撤回 / 已读游标，详见 `消息功能技术方案.md`）。
- **管理后台**：概览看板（真实统计：用户增长/健康度/活动流）、用户管理、文档管理（发布/草稿/废弃、批量删除/移动）、知识库与成员管理、学习路径与章节管理（**AI 生成路径/章节内容**）、闪卡管理（**AI 批量生成**）、代码题库、测验题库（**AI 出题**）、自定义图标（SVG 上传）、对话配置、社区管理。

## 技术栈

| 层   | 技术                                                                          |
| --- | --------------------------------------------------------------------------- |
| 前端  | Vue 3 + TypeScript + Vite + Pinia + Vue Router + Axios + TailwindCSS        |
| 后端  | Spring Boot 3.2 + MyBatis-Plus + Spring Security + JWT + SpringDoc（Swagger） |
| 数据库 | 默认 H2 内存库（已预留 MySQL 驱动，可平滑切换）                                               |
| 构建  | Maven（后端）/ npm（前端）                                                          |

## 页面架构（约 50 页）

本项目按用户角色与访问入口分为三种布局，通过 `route.meta.layout` 动态切换：

| 布局 | 说明 | 适用页面 |
|---|---|---|
| `c`（C 端） | 固定顶部导航 + 全屏内容区（无侧边栏） | 用户体验前台页面 |
| `b`（B 端） | 左侧边栏（可折叠）+ 顶部栏 + 可滚动内容区 | 管理后台页面（19 个路由） |
| `none` | 无布局壳 | 登录 / OAuth 回调 / 重定向 / 404 |

### C 端用户体验前台

| 模块 | 页面 | 路由 |
|---|---|---|
| **发现与浏览** | 首页 | `/` |
|  | 知识库首页 | `/knowledge` |
|  | 文档列表 | `/docs` |
|  | 文档详情 | `/doc/:id` |
|  | 分类浏览 | `/categories` |
|  | 搜索结果 | `/search` |
| **学习中心** | 学习中心（报告/热力图/掌握分布） | `/learning/center` |
|  | 学习路径列表 | `/learning/paths` |
|  | 路径详情 | `/learning/path/:id` |
|  | 章节学习 | `/learning/chapter/:id` |
|  | 代码练习列表 | `/learning/code-practice` |
|  | 代码 Playground（在线做题） | `/learning/code-practice/:id` |
|  | 学习闪卡 | `/learning/flashcards` |
|  | 知识图谱 | `/learning/knowledge-graph` |
|  | 复习计划 | `/learning/review` |
|  | 沉浸式学习模式（无顶栏） | `/learning/mode` |
|  | 番茄钟 | `/learning/pomodoro` |
| **AI 助手** | 智能问答对话（多模型切换 / RAG 溯源 / 分享） | `/chat` |
|  | 智能出题 | `/learning/quiz` |
|  | 智能写作（AI 评分 / 预览 / 导出 PDF・MD） | `/learning/writing` |
| **个人空间** | 工作台（任务中心） | `/tasks` |
|  | 个人中心 | `/profile` |
|  | 收藏夹 | `/favorites` |
|  | 笔记管理 / 笔记编辑 | `/notes` `/notes/new` `/notes/:id/edit` |
|  | 错题本 | `/mistakes` |
|  | 成就 | `/achievements` |
|  | 知识库称号 | `/kb-titles` |
|  | 打卡 | `/check-in` |
|  | 消息中心 | `/notifications` |
|  | 社区讨论 | `/community` |
| **挑战与互动** | 编程挑战（闯关赛道 / 关卡） | `/challenge` `/challenge/:id` |
|  | 学习小组（IM 群聊 / 资料 / 公告） | `/study-group` |
|  | 私信（单聊会话 / 已读游标 / 撤回） | `/messages` |

### B 端管理后台（19 个路由，需 ADMIN）

| 模块 | 页面 | 路由 |
|---|---|---|
| **数据概览** | 系统概览 | `/admin/overview` |
| **内容管理** | 知识库管理 | `/admin/knowledge` |
|  | 分类管理（含成员/导入导出） | `/admin/categories` |
|  | 文档管理 | `/admin/docs` |
|  | 新增 / 编辑文档 | `/admin/docs/new` `/admin/docs/:id/edit` |
|  | 上传文档 | `/admin/upload` |
|  | 知识库与标签 | `/admin/tags` |
|  | 知识卡片管理（AI 批量生成） | `/admin/flashcards` |
| **学习管理** | 学习路径管理（AI 生成路径） | `/admin/learning-paths` |
|  | 章节新增 / 编辑（AI 生成内容） | `/admin/learning/chapters/new` `/admin/learning/chapters/:id/edit` |
|  | 代码题库管理 | `/admin/code-questions` |
|  | 测验题库管理（AI 出题） | `/admin/quiz` |
| **AI 与生成** | 智能写作（共享） | `/admin/writing` |
|  | 对话配置 | `/admin/chat-config` |
| **系统与运营** | 用户管理 | `/admin/users` |
|  | 图标管理（自定义 SVG） | `/admin/icons` |
|  | 社区管理 | `/admin/community` |

### 通用系统页面

| 页面 | 路由 | 说明 |
|---|---|---|
| 登录 / 注册 | `/login`（`/register` 重定向至注册页签） | 账号登录与注册 |
| OAuth 回调 | `/oauth/callback` | GitHub / 微信登录回跳 |
| 统一入口 | `/portal`（重定向至 `/tasks`） | 登录后工作台 |
| 重定向 / 404 | `/redirect` `*` | 路由过渡页 / 错误页 |

## 后端接口总览（31 个控制器）

统一前缀 `/api`，完整接口文档见 Swagger（`/swagger-ui.html`）。

### 面向用户（22 个）

| 控制器 | 根路径 | 能力 |
|---|---|---|
| AuthController | `/api/auth` | 登录 / 注册 / 登出（黑名单）/ 当前用户 / GitHub・微信 OAuth |
| UserController | `/api/user` | 个人资料、学习统计 |
| AiConfigController | `/api/ai-config` | 用户自建 AI Key 配置（脱敏返回）、平台模型列表 |
| DocController | `/api/docs` | 文档列表 / 详情 / 收藏 / 进度 / 推荐 / **AI 摘要** / **AI 生成闪卡** |
| CategoryController | `/api/categories` | 分类树 |
| ChatController | `/api/chat` | 会话 CRUD、发送消息（RAG + 大模型）、**可用模型列表** |
| LearningController | `/api/learning` | 路径 / 章节 / 报名 / 闪卡 SM-2 复习 / 任务 / **热力图**（`/stats/daily-activity`）/ **掌握分布**（`/stats/mastery`） |
| CodeQuestionController | `/api/code-questions` | 已发布代码题列表 / 详情 / 提交统计 |
| MistakeController | `/api/mistakes` | 错题列表 / 标记掌握 / 幂等添加 / 统计 |
| CommunityController | `/api/community` | 帖子 / 评论 / 点赞（幂等切换） |
| NotificationController | `/api/notifications` | 消息列表 / 已读 / 未读数 |
| KnowledgeController | `/api/knowledge` | 知识图谱数据（分类图/技术栈图/概念图解/实体关系图；`/entity-graph` 查询、`/extract` 触发 AI 抽取） |
| QuizController | `/api/quiz` | 智能测验：按知识库/文档出题、提交判分、答题记录、错题同步 |
| CheckInController | `/api/checkin` | 每日打卡（签到 / 补卡 / 连续天数与奖励） |
| AchievementController | `/api/achievements` | 成就列表与解锁、用户成就进度 |
| RankController | `/api/ranking` | 积分 / 经验排行榜 |
| CodeChallengeController | `/api/challenges` | 编程挑战赛道与关卡、提交判题、排行榜、个人战绩（榜单/详情匿名可读，提交与战绩需登录） |
| StudyGroupController | `/api/study-groups` | 学习小组 CRUD、成员管理、群公告、邀请 |
| PrivateMessageController | `/api/im/private` | 私信会话与消息、已读游标、撤回 |
| CodeWorkspaceController | `/api/code/workspace` | 代码工作区（多文件 / 沙箱运行） |
| CodeRunController | `/api/code` | 单文件代码在线运行（沙箱执行） |
| UserSearchController | `/api/users` | 用户搜索（按昵称/邮箱，用于小组邀请等） |

### 管理后台（9 个，ADMIN / 知识库 Owner 鉴权）

| 控制器 | 根路径 | 能力 |
|---|---|---|
| AdminOverviewController | `/api/admin/overview` | 看板统计（用户增长 / 健康度 / 活动流） |
| AdminUserController | `/api/admin/users` | 用户 CRUD、重置密码 |
| AdminDocController | `/api/admin/docs` | 文档 CRUD、发布 / 草稿 / 废弃、批量删除 / 移动 |
| AdminCategoryController | `/api/admin/categories` | 知识库 CRUD、批量导入（zip/md/txt/json）、导出 ZIP |
| AdminKbMemberController | `/api/admin/kb-members` | 知识库成员：搜索邀请 / 邮箱邀请 / 角色变更（OWNER/EDITOR/READER）/ 移除 |
| AdminLearningController | `/api/admin/learning` | 路径 / 章节 / 闪卡 CRUD 与发布；**AI 生成路径、章节内容、批量闪卡** |
| AdminCodeQuestionController | `/api/admin/code-questions` | 代码题库 CRUD、发布 / 下架 |
| AdminQuizQuestionController | `/api/admin/quiz-questions` | 多题型题库 CRUD、发布 / 下架、**AI 出题** |
| AdminIconController | `/api/admin/icons` | 自定义 SVG 图标上传 / 删除 |

## 设计系统

- **主色**：`#3B6FE0`（学术蓝）
- **CSS 变量前缀**：`--kb-*`（如 `--kb-primary`、`--kb-card`、`--kb-border`），全局 CSS Variables，禁止硬编码颜色
- **字体栈**：Noto Sans SC + Inter
- **图标系统**：自实现 [Icon.vue](./frontend/src/components/ui/Icon.vue)，覆盖 60+ Lucide 图标
- **排版层级**：`kb-h1` / `kb-h2` / `kb-h3` / `kb-h4` / `kb-body` / `kb-body-sm` / `kb-caption`
- **响应式**：桌面端（≥1280px）为主，移动端兼容至 `sm`（640px）

## 目录结构

```
knowflow/
├── backend/               # Spring Boot 后端（端口 8080）
│   ├── src/main/java/com/knowflow/   # 控制器 / 服务 / 实体 / 配置
│   ├── src/main/resources/           # application.yml、schema.sql、data.sql
│   └── pom.xml
├── frontend/              # Vue 3 前端（端口 5173）
│   ├── src/
│   │   ├── views/         # 页面（顶层约 38 页 + admin/ 14 页）
│   │   │   └── admin/     # B 端管理后台页面
│   │   ├── components/
│   │   │   ├── layout/    # 布局组件（CLayout / AppShell / CTopNav / BTopbar / Sidebar）
│   │   │   └── ui/        # 基础组件（Icon / Button / Card / Input / Avatar 等）
│   │   ├── api/           # 接口封装（统一 apiGet/apiPost 类型化）
│   │   ├── stores/        # Pinia 状态（auth / notification）
│   │   ├── router/        # 路由配置（含 meta.layout 布局约定）
│   │   ├── data/          # 静态 mock 数据
│   │   ├── types/         # TS 类型定义
│   │   └── utils/         # 工具函数（toast 等）
│   └── package.json
├── miniprogram/           # 微信小程序（独立子项目，见 `审核提交说明.md`）
├── design/                # 原始设计稿（HTML）
├── DATABASE.md            # 数据库设计文档
├── DEPLOY.md              # 部署指南
└── README.md              # 本文件
```

## 环境要求

- **JDK 17**
- **Node.js 20+**（前端使用 Vite）
- **Maven 3.8+**（或用项目内置 `backend/.mvn` 包装器）

## 快速开始

### 1. 启动后端

```bash
cd backend
./mvnw spring-boot:run        # 或：mvn spring-boot:run
```

- 默认端口：`8080`
- 数据库：H2 内存库，启动时会自动执行 `schema.sql` 建表、`data.sql` 写入初始数据
- API 文档（Swagger）：<http://localhost:8080/swagger-ui.html>
- H2 控制台：<http://localhost:8080/h2-console>（JDBC URL：`jdbc:h2:mem:knowflow`，用户名 `sa`，密码空）
- 如需切换 MySQL：在 `backend/src/main/resources/application.yml` 中修改 `spring.datasource` 配置即可
- **AI 功能配置**：复制 `backend/src/main/resources/application-local.example.yml` 为 `application-local.yml`（已被 gitignore，不会提交），填入真实 `ai.api-key` / `ai.base-url` / `ai.model`（也支持环境变量 `AI_API_KEY` 等覆盖）。未配置时 AI 对话 / 摘要 / 出题等功能不可用；用户也可在前端「AI 设置」中自行填入个人 Key

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev                  # 开发服务器（默认 http://localhost:5173）
# 生产构建：npm run build      # 产物输出到 frontend/dist/
```

### 3. 访问入口

| 入口 | 地址 | 说明 |
|---|---|---|
| 平台统一入口 | <http://localhost:5173/portal> | C 端 / B 端分流引导（推荐首访） |
| C 端首页 | <http://localhost:5173/> | 用户体验前台 |
| B 端管理后台 | <http://localhost:5173/admin/overview> | 需管理员账号登录 |
| Swagger 文档 | <http://localhost:8080/swagger-ui.html> | 后端接口文档 |
| H2 控制台 | <http://localhost:8080/h2-console> | 数据库可视化 |

## 默认账号

数据库已预置测试账号（密码统一为 `admin123`，BCrypt 加密）：

| 用户名  | 密码         | 角色         |
|----|------------|------------|
| `admin` | `admin123` | 管理员（ADMIN） |
| `user1` | `admin123` | 普通用户（USER） |
| `user2` | `admin123` | 普通用户（USER） |

> 生产环境请务必修改默认密码与 `application.yml` 中的 JWT 密钥。

## 前后端接口约定

- **统一前缀**：所有后端接口以 `/api` 开头，前端 Axios `baseURL` 为 `/api`，开发态由 Vite 代理到 `http://localhost:8080`。
- **统一响应**：`ApiResult<T>{ code, message, data }`；分页响应 `PageResult{ records, total, pageNum, pageSize, pages }`（注意分页数据字段是 `records` 不是 `list`）。
- **JWT 鉴权**：请求拦截器自动附加 `Authorization: Bearer <token>`；401 自动跳登录；登录态由 `useAuthStore` 管理。
- **请求层类型化**：前端统一走 `apiGet<T>` / `apiPost<T>` / `apiPut<T>` / `apiDelete<T>`（见 `src/api/request.ts`），禁止页面内裸调 `axios`。
- **路由守卫**：`meta.requiresAuth` 标记需登录页面；`meta.requiresAdmin` 标记需管理员权限页面（B 端全部需要）。

## 代码仓库（双端同步）

本项目同时托管于 GitHub 与 Gitee，远程别名分别为 `github` 与 `gitee`：

```bash
git push github     # 推送到 GitHub（SSH）
git push gitee      # 推送到 Gitee（HTTPS）
```

## 部署

生产环境部署（后端 jar / MySQL、前端构建 + Nginx 反代、Docker 一键部署、systemd 守护等）请见 [DEPLOY.md](./DEPLOY.md)。

## 相关文档

- [DATABASE.md](./DATABASE.md) — 数据库表结构（41 张表）、字段定义、索引设计（遵循《阿里巴巴 Java 开发手册》）
- [DEPLOY.md](./DEPLOY.md) — 生产环境部署指南（jar / Nginx / Docker / systemd）
- [OAUTH.md](./OAUTH.md) — GitHub / 微信 第三方 OAuth 登录接入指南
- [消息功能技术方案.md](./消息功能技术方案.md) — IM（学习小组群聊 / 单聊私信）权威技术文档
- [docs/PRD-功能需求文档.md](./docs/PRD-功能需求文档.md) — 产品功能需求文档
- [审核提交说明.md](./审核提交说明.md) — 微信小程序审核提交说明

## 说明

- 根目录 `.gitignore` 已忽略 `node_modules/`、`backend/target/`、IDE 配置（`.idea/`、`.trae/`）、本地代理记忆（`.workbuddy/`）等，避免把依赖与编译产物提交到仓库。
- 前端 `README.md` 仅作子模块说明，项目整体说明以本文件为准。
