# 前端 · knowflow

本项目的前端模块，基于 **Vue 3 + TypeScript + Vite + Pinia + TailwindCSS** 构建。

> 整体项目说明（功能、技术栈、启动方式、默认账号等）请见根目录 [`../README.md`](../README.md)。

## 常用命令

```bash
npm install      # 安装依赖
npm run dev      # 启动开发服务器（默认 http://localhost:5173）
npm run build    # 生产构建（vue-tsc 类型检查 + vite 打包，输出到 dist/）
npm run preview  # 预览构建产物
```

## 页面架构

前端共 **36 个页面**，通过 `route.meta.layout` 在三种布局间动态切换：

| 布局 | 布局组件 | 适用页面 |
|---|---|---|
| `c`（C 端） | `CLayout.vue` + `CTopNav.vue` | 19 个用户体验前台（顶部导航 + 全屏内容，无侧边栏） |
| `b`（B 端） | `AppShell.vue` + `Sidebar.vue` + `BTopbar.vue` | 13 个管理后台（左侧边栏 + 顶部栏 + 内容区） |
| `none` | 无 | 4 个通用系统页面（入口/登录/重定向/404） |

> 智能出题、智能写作等工具页 C 端 / B 端共享，通过不同路由入口渲染对应布局。

## 目录概览

```
src/
├── views/                 # 页面（C 端 19 + B 端 13 + 通用 4 = 36 页）
│   └── admin/             # B 端管理后台页面
├── components/
│   ├── layout/            # 布局组件
│   │   ├── CLayout.vue    # C 端布局壳
│   │   ├── CTopNav.vue    # C 端顶部导航
│   │   ├── AppShell.vue   # B 端布局壳
│   │   ├── Sidebar.vue    # B 端侧边栏（可折叠）
│   │   └── BTopbar.vue    # B 端顶部栏
│   └── ui/                # 基础 UI 组件
│       ├── Icon.vue       # 内置 Lucide 图标（60+）
│       ├── Button.vue / Card.vue / Input.vue / Avatar.vue / Badge.vue
│       ├── PageHeader.vue / Pagination.vue / EmptyState.vue / SkeletonList.vue
│       └── ToastHost.vue  # 全局轻提示
├── api/                   # 后端接口封装（统一 apiGet/apiPost 类型化）
├── stores/                # Pinia 状态（auth / notification）
├── router/                # 路由配置（含 meta.layout 布局约定 + 鉴权守卫）
├── data/                  # 静态 mock 数据
├── types/                 # TS 类型定义
├── utils/                 # 工具函数（toast 等）
├── style.css              # 全局样式（--kb-* CSS 变量 + 排版层级）
└── App.vue                # 应用根组件（按 route.meta.layout 切换布局）
```

## 设计系统

- **主色**：`#3B6FE0`（学术蓝），通过 `--kb-primary` 全局变量定义
- **CSS 变量前缀**：`--kb-*`，禁止硬编码颜色
- **排版层级**：`kb-h1` / `kb-h2` / `kb-h3` / `kb-h4` / `kb-body` / `kb-body-sm` / `kb-caption`
- **字体栈**：Noto Sans SC + Inter
- **图标**：自实现 [Icon.vue](./src/components/ui/Icon.vue)，覆盖 60+ Lucide 图标

## 接口约定

- **统一前缀**：Axios `baseURL` 为 `/api`，开发态由 Vite 代理到 `http://localhost:8080`
- **统一响应**：`ApiResult<T>{ code, message, data }`；分页 `PageResult{ records, total, pageNum, pageSize, pages }`
- **请求层类型化**：统一走 `apiGet<T>` / `apiPost<T>` / `apiPut<T>` / `apiDelete<T>`，禁止页面内裸调 `axios`
- **JWT 鉴权**：请求拦截器自动附加 `Bearer` token，401 自动跳登录
