# 前端 · learnbase

本项目的前端模块，基于 **Vue 3 + TypeScript + Vite** 构建。

> 整体项目说明（功能、技术栈、启动方式、默认账号等）请见根目录 [`../README.md`](../README.md)。

## 常用命令

```bash
npm install      # 安装依赖
npm run dev      # 启动开发服务器（默认 http://localhost:5173）
npm run build    # 生产构建（vue-tsc 类型检查 + vite 打包，输出到 dist/）
npm run preview  # 预览构建产物
```

## 目录概览

- `src/views/`：页面（首页、文档详情、搜索、AI 对话、学习中心、管理后台等）
- `src/api/`：后端接口封装（Axios）
- `src/stores/`：Pinia 状态管理
- `src/router/`：路由配置
- `src/components/`：公共 UI 组件与布局
