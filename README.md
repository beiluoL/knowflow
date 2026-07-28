# knowflow · AI 知识库与学习平台

一个面向学习场景的**知识库 + AI 学习平台**：用户可浏览 / 搜索 / 上传文档，借助 AI 对话答疑，并沿着系统化的学习路径（章节 + 闪卡 + 测验 + 写作 + 代码练习）持续学习，辅以等级、经验、能量、连续打卡等游戏化激励；管理员可在后台统一管理用户、文档、知识库与 AI 配置。

> 仓库名 `knowflow`（knowledge + flow）。代码中包名、表名等沿用 `knowflow` 前缀，与仓库名一致。

## 功能特性

- **用户与鉴权**：注册 / 登录（JWT）、GitHub/微信 OAuth 第三方登录、个人资料；成长体系含等级、经验、能量、连续打卡天数。
- **文档与知识库**：文档浏览、详情、多级分类、全文搜索、上传；支持收藏与阅读进度（进度百分比 / 阅读时长 / 上次阅读时间）。
- **AI 对话**：多轮会话管理，回答可附带文档引用（溯源到知识库内容）；支持 Markdown 渲染（表格/代码块/引用块）、万字长文生成、AI 设置弹窗（11 个大模型提供商可选）。
- **学习中心**：学习路径与章节（章节可挂载文档与闪卡）、闪卡复习（SM-2 间隔重复算法）、学习任务（带经验奖励 / 能量消耗）；并提供智能测验、智能写作、代码练习、复习计划等学习工具。
- **管理后台**：概览看板、用户管理、文档管理、知识库管理、AI 对话配置、闪卡管理、社区管理。
- **用户自配大模型**：用户可在 AI 设置中手动输入 API Key（支持 DeepSeek/硅基流动/OpenAI/通义千问/阿里云百炼/智谱AI/月之暗面/字节豆包/腾讯混元/百度文心/Anthropic/自定义），也可使用平台提供的订阅模型。

## 技术栈

| 层   | 技术                                                                          |
| --- | --------------------------------------------------------------------------- |
| 前端  | Vue 3 + TypeScript + Vite + Pinia + Vue Router + Axios + TailwindCSS        |
| 后端  | Spring Boot 3.2 + MyBatis-Plus + Spring Security + JWT + SpringDoc（Swagger） |
| 数据库 | 默认 H2 内存库（已预留 MySQL 驱动，可平滑切换）                                               |
| 构建  | Maven（后端）/ npm（前端）                                                          |

## 页面架构（36 页）

本项目按用户角色与访问入口分为三种布局，通过 `route.meta.layout` 动态切换：

| 布局 | 说明 | 适用页面 |
|---|---|---|
| `c`（C 端） | 固定顶部导航 + 全屏内容区（无侧边栏） | 19 个用户体验前台页面 |
| `b`（B 端） | 左侧边栏（可折叠）+ 顶部栏 + 可滚动内容区 | 13 个管理后台页面 |
| `none` | 无布局壳 | 4 个通用系统页面（入口/登录/重定向/404） |

### C 端用户体验前台（19 页）

| 模块 | 页面 | 路由 |
|---|---|---|
| **发现与浏览** | 知识库首页 | `/knowledge` |
|  | 文档详情 | `/doc/:id` |
|  | 分类浏览 | `/categories` |
|  | 搜索结果 | `/search` |
| **学习中心** | 学习中心首页 | `/learning/center` |
|  | 学习路径列表 | `/learning/paths` |
|  | 路径详情 | `/learning/path/:id` |
|  | 章节学习 | `/learning/chapter/:id` |
|  | 代码练习 | `/learning/code-practice` |
|  | 学习闪卡 | `/learning/flashcards` |
|  | 复习计划 | `/learning/review` |
|  | 学习报告 | `/learning/report` |
|  | 沉浸式学习模式（无顶栏） | `/learning/mode` |
| **AI 助手** | 智能问答对话 | `/chat` |
|  | 智能写作（与 B 端共享） | `/learning/writing` |
|  | 智能出题（与 B 端共享） | `/learning/quiz` |
| **个人空间** | 个人中心 | `/profile` |
|  | 收藏夹 | `/favorites` |
|  | 笔记管理 | `/notes` |
|  | 错误本 | `/mistakes` |
|  | 消息中心 | `/notifications` |
|  | 社区讨论 | `/community` |

### B 端管理后台（13 页）

| 模块 | 页面 | 路由 |
|---|---|---|
| **数据概览** | 系统概览 | `/admin/overview` |
| **内容管理** | 知识库管理 | `/admin/knowledge` |
|  | 文档管理 | `/admin/docs` |
|  | 新增文档 | `/admin/docs/new` |
|  | 编辑文档 | `/admin/docs/:id/edit` |
|  | 上传文档 | `/admin/upload` |
|  | 知识库与标签 | `/admin/tags` |
|  | 知识卡片管理 | `/admin/flashcards` |
| **AI 与生成** | 智能出题（共享） | `/admin/quiz` |
|  | 智能写作（共享） | `/admin/writing` |
|  | 对话配置 | `/admin/chat-config` |
| **用户与运营** | 用户管理 | `/admin/users` |
|  | 社区管理 | `/admin/community` |

### 通用系统页面（4 页）

| 页面 | 路由 | 说明 |
|---|---|---|
| 统一入口 | `/portal` | C 端 / B 端分流引导 |
| 登录 / 注册 | `/login` `/register` | 账号登录与注册 |
| 重定向 | `/redirect` | 路由过渡页 |
| 404 | `*` | 错误页 |

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
│   │   ├── views/         # 页面（C 端 19 + B 端 13 + 通用 4 = 36 页）
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

- [DATABASE.md](./DATABASE.md) — 数据库表结构、字段定义、索引设计（遵循《阿里巴巴 Java 开发手册》）
- [DEPLOY.md](./DEPLOY.md) — 生产环境部署指南（jar / Nginx / Docker / systemd）
- [审核提交说明.md](./审核提交说明.md) — 微信小程序审核提交说明

## 说明

- 根目录 `.gitignore` 已忽略 `node_modules/`、`backend/target/`、IDE 配置（`.idea/`、`.trae/`）、本地代理记忆（`.workbuddy/`）等，避免把依赖与编译产物提交到仓库。
- 前端 `README.md` 仅作子模块说明，项目整体说明以本文件为准。
