# learnbase · AI 知识库与学习平台

一个面向学习场景的**知识库 + AI 学习平台**：用户可浏览 / 搜索 / 上传文档，借助 AI 对话答疑，并沿着系统化的学习路径（章节 + 闪卡 + 测验 + 写作 + 代码练习）持续学习，辅以等级、经验、能量、连续打卡等游戏化激励；管理员可在后台统一管理用户、文档、知识库与 AI 配置。

> 仓库名 `learnbase`（learning + knowledge base）。原项目目录名为 `zhishiku`，代码中包名、表名等仍沿用 `zhishiku` 前缀，属历史命名，不影响使用。

## 功能特性

- **用户与鉴权**：注册 / 登录（JWT）、个人资料；成长体系含等级、经验、能量、连续打卡天数。
- **文档与知识库**：文档浏览、详情、多级分类、全文搜索、上传；支持收藏与阅读进度（进度百分比 / 阅读时长 / 上次阅读时间）。
- **AI 对话**：多轮会话管理，回答可附带文档引用（溯源到知识库内容）。
- **学习中心**：学习路径与章节（章节可挂载文档与闪卡）、闪卡复习、学习任务（带经验奖励 / 能量消耗）；并提供智能测验、智能写作、代码练习、复习计划等学习工具。
- **管理后台**：概览看板、用户管理、文档管理、知识库管理、AI 对话配置、闪卡管理。

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3 + TypeScript + Vite + Pinia + Vue Router + Axios + TailwindCSS |
| 后端 | Spring Boot 3.2 + MyBatis-Plus + Spring Security + JWT + SpringDoc（Swagger） |
| 数据库 | 默认 H2 内存库（已预留 MySQL 驱动，可平滑切换） |
| 构建 | Maven（后端）/ npm（前端） |

## 目录结构

```
zhishiku/
├── backend/               # Spring Boot 后端（端口 8080）
│   ├── src/main/java/com/zhishiku/   # 控制器 / 服务 / 实体 / 配置
│   ├── src/main/resources/           # application.yml、schema.sql、data.sql
│   └── pom.xml
├── frontend/              # Vue 3 前端
│   ├── src/
│   │   ├── views/         # 页面（首页 / 文档 / 对话 / 学习中心 / 管理后台 …）
│   │   ├── api/           # 接口封装
│   │   ├── stores/        # Pinia 状态
│   │   ├── router/        # 路由
│   │   └── components/    # 公共组件
│   └── package.json
└── README.md
```

## 环境要求

- **JDK 17**
- **Node.js 20+**（前端使用 Vite 8）
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
- H2 控制台：<http://localhost:8080/h2-console>（JDBC URL：`jdbc:h2:mem:zhishiku`）
- 如需切换 MySQL：在 `backend/src/main/resources/application.yml` 中修改 `spring.datasource` 配置即可

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev                  # 开发服务器（默认 http://localhost:5173）
# 生产构建：npm run build      # 产物输出到 frontend/dist/
```

## 默认账号

数据库已预置测试账号（密码统一为 `admin123`）：

| 用户名 | 密码 | 角色 |
|---|---|---|
| `admin` | `admin123` | 管理员（ADMIN） |
| `user1` | `admin123` | 普通用户（USER） |
| `user2` | `admin123` | 普通用户（USER） |

> 生产环境请务必修改默认密码与 `application.yml` 中的 JWT 密钥。

## 代码仓库（双端同步）

本项目同时托管于 GitHub 与 Gitee，远程别名分别为 `github` 与 `gitee`：

```bash
git push github     # 推送到 GitHub（SSH）
git push gitee      # 推送到 Gitee（HTTPS）
```

## 部署

生产环境部署（后端 jar / MySQL、前端构建 + Nginx 反代、Docker 一键部署、systemd 守护等）请见 [DEPLOY.md](./DEPLOY.md)。

## 说明

- 根目录 `.gitignore` 已忽略 `node_modules/`、`backend/target/`、IDE 配置（`.idea/`、`.trae/`）、本地代理记忆（`.workbuddy/`）等，避免把依赖与编译产物提交到仓库。
- 前端 `README.md` 仅作子模块说明，项目整体说明以本文件为准。
