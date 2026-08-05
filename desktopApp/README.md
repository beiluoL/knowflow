# KnowFlow 学习工作台 · macOS 桌面应用

把 Web 项目中的「学习工作台」模块（收集箱 → 康奈尔笔记 → 间隔重复/记忆宫殿 → 费曼故事 四模块闭环）独立为 **macOS 专用**桌面应用。

## 技术选型（均为各维度最推荐方案）

| 层 | 技术 | 说明 |
|----|------|------|
| 桌面外壳 | **Tauri 2** | Rust 极薄壳 + macOS 原生 WKWebView，体积小、内存省 |
| 后端 | **Node.js + TypeScript + Fastify** | 重写 Web 端 `WorkbenchController`，14 个端点 |
| 数据 | **SQLite (better-sqlite3, WAL)** | 单文件本地库，离线优先、隐私可控 |
| 访问层 | **Drizzle ORM** | 类型安全 SQL，似 MyBatis |
| 前端 | **Vue 3 + Vite + vue-router** | 复用 `/workbench` 端点契约，history 路由 |

通信：Node 后端**同源托管** Vue `dist` 与 `/api/*`，窗口加载 `http://127.0.0.1:<port>`，
零 CORS、vue-router 保持 history 模式、无需 WebSocket。生产由 Tauri 启动 Node 侧车实现。

## 目录结构

```
desktopApp/
├── src-api/         # Node 后端（Fastify + SQLite + Drizzle）
│   ├── src/routes/  # 8 张表对应 14 个端点 + SM-2 + 遗忘曲线
│   ├── src/services/sm2.ts  # SM-2 算法（与 Web 端逐位一致）
│   └── src/db/      # schema + 建表 + WAL
├── src-ui/          # Vue 3 前端（7 个视图：总览/收集箱/笔记/复习/宫殿/回忆/故事）
├── src-tauri/       # Tauri 2 macOS 外壳（Rust 侧车启动 Node 后端）
├── scripts/         # prepare-bin.sh 生成 Node 侧车二进制
└── package.json     # 编排脚本
```

## 快速开始（开发，无需 Rust）

```bash
# 1. 安装依赖
npm --prefix src-api install
npm --prefix src-ui install

# 2. 同时启动前端(5173)与后端(8787)，浏览器打开 http://localhost:5173
npm run dev:all
```
前端经 Vite 代理把 `/api` 转发到本地 Node 后端，即可完整使用四模块功能。

## 打包为 macOS .app（需 Rust 工具链）

```bash
# 1. 安装 Rust（一次性，约 1-2 分钟）
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"

# 2. 安装 Tauri CLI（已写入根 package.json）
npm install

# 3. 生成 Node 侧车二进制（复制本机 node）
bash scripts/prepare-bin.sh

# 4. 生成应用图标（准备一张 1024x1024 png）
npm run tauri icon /path/to/icon.png

# 5. 构建 .app / .dmg
npm run tauri build
```
产物在 `src-tauri/target/release/bundle/macos/`。

## 与现有 Web 项目的数据交互

- 桌面端数据**本地优先**存于 `~/Library/Application Support/com.knowflow.desktop/` 下 SQLite。
- 从 Web 迁移：在 Web 端导出 `workbench-export` JSON（新增只读端点即可），桌面端「导入」映射本地表。
- 分类：本地 `categories` 表（已预置 未分类/工作/学习/生活），也可从 Web 导入。

## 原生能力（Tauri，macOS）

桌面壳 `src-tauri/src/lib.rs` 在白屏壳基础上增加了三项原生能力，**业务代码零改动**：

### 1. 原生菜单
macOS 标准菜单栏：
- **KnowFlow**（App 菜单）：关于 / 检查更新… / 去学习复习 / 复习提醒：开（可切换）/ 退出
- **视图**：重新加载页面（等效 `location.reload()`）

「去学习复习」与点击复习提醒通知都会向渲染进程发 `navigate` 事件，前端 `App.vue` 监听后 `router.push('/reviews')`。

### 2. 复习提醒通知（后台轮询）
- 后台独立线程每 **30 分钟** 轮询后端 `GET /api/workbench/reviews/due-count`。
- 有待复习卡片时弹 **macOS 原生通知**（标题/正文含卡片样例）。
- 点击通知：聚焦窗口并跳到复习页。
- 菜单「复习提醒：开/关」可随时开关，状态由共享 `Arc<Mutex<bool>>` 维护。

> 轮询走 `ureq`（纯 Rust HTTP，无 OpenSSL 依赖），只请求计数端点，开销极小。

### 3. 自动更新
- 集成 `tauri-plugin-updater`，菜单「检查更新…」与前端侧边栏「检查更新」按钮共用 `check_for_update` 命令。
- 检测到新版本自动下载并安装，完成后弹通知。
- **当前 `tauri.conf.json` 中 `updater.active=false`**：因自动更新需要你自己的 ed25519 签名密钥与发布端点（属个人密钥，不能提交到仓库）。激活步骤见下方「启用自动更新」。

## 启用自动更新（一次性，发布前做）

```bash
# 1. 生成签名密钥对（私钥 tauri.key 务必离线保管，公钥填回配置）
npx tauri signer generate

# 2. 把输出的「公钥」粘贴到 src-tauri/tauri.conf.json 的 plugins.updater.pubkey
#    并把 "active": false 改为 true

# 3. 用 CI（tauri-action）发布 GitHub Release 时，会生成 latest.json + 签名文件，
#    并把 endpoints 改成你的仓库地址：
#    "endpoints": ["https://github.com/<你>/<仓库>/releases/latest/download/latest.json"]
```

## 已知说明

- 端口固定 `8787` 并仅绑定 `127.0.0.1`；生产由 Tauri 单实例锁避免重复启动。
- macOS 分发需 Apple Developer ID 签名 + 公证（`notarytool`），否则 Gatekeeper 拦截；本仓库未包含证书。
- Mac App Store 暂不推荐（本地 SQLite + 文件访问受沙箱限制）。

## 运行打包后的 .app（未公证）

本机构建产物是 **ad-hoc 签名**（主程序）；侧车 `server` 复用 Node.js 官方的 Developer ID 签名。
在**本机或同机**直接双击通常即可打开；若被 Gatekeeper 拦截，执行：

```bash
xattr -cr "src-tauri/target/release/bundle/macos/KnowFlow 学习工作台.app"
open "src-tauri/target/release/bundle/macos/KnowFlow 学习工作台.app"
```

若要分发给他人，必须先做 Apple Developer ID 签名 + `notarytool` 公证（见上方「启用自动更新」附近的签名说明）。

## 构建验证记录（2026-08-06，arm64 macOS）

`tauri build` 已在本机（Rust 1.97.1 + Xcode）跑通，产出
`src-tauri/target/release/bundle/macos/KnowFlow 学习工作台.app`（arm64，约 220MB）。

验证点到为止（GUI 窗口需真实显示环境，以下为后端+资源链路实测）：

- 用打包内的 `server` 侧车 + `Resources/api/index.js` + `Resources/web` 真实拉起后端；
- `GET /api/workbench/overview` 返回真实 JSON；
- 同源首页 `GET /` 返回 `index.html`（http 200）；
- `POST /api/workbench/captures` 建卡成功并计入总览。
- 因后端依赖 `better-sqlite3`（原生模块），`tauri.conf.json` 的 `bundle.resources`
  已额外包含 `../src-api/node_modules → api/node_modules`，否则打包后 `require` 会失败。

### 实现注意点（与原先预期的差异）

1. **通知点击不会自动跳转复习页**：`tauri-plugin-notification` 2.3.x 桌面端 builder
   无 `on_click` 回调，点击通知仅由系统原生聚焦应用到前台。跳转复习页请走菜单
   「去学习复习」或侧边栏导航。（代码已改用 `app.notification().builder()...` 写法。）
2. **`download_and_install` 需两个闭包**：updater 2.10.1 签名为
   `download_and_install(on_chunk, on_download_finish)`，已修正。
3. 菜单「关于」`PredefinedMenuItem::about` 需第三个 `AboutMetadata` 参数（传 `None`）。

### 故障复盘：双击 .app 无任何界面（2026-08-06）

**现象**：双击 `KnowFlow 学习工作台.app` 后 Dock 图标弹一下即消失，完全没有窗口。

**根因**：`src-tauri/tauri.conf.json` 的 `plugins.shell` 配置里写了非法字段 `execute`
（以及 `sidecar`）。当前 `tauri-plugin-shell` 2.3.5 的 Config 结构体**仅有一个 `open` 字段**
（`#[serde(deny_unknown_fields)]` 会拒绝未知字段）。该错误在插件初始化阶段就 panic：

```
PluginInitialization("shell", "Error deserializing 'plugins.shell' within your Tauri configuration: unknown field `execute`, expected `open`")
```

由于 panic 发生在任何窗口创建之前，所以**根本不会渲染 GUI**，与「白屏/错误页」是两回事。

**修复**：`plugins.shell` 只保留 `"open": false`（侧车启用不靠这里，而是靠
`bundle.externalBin` + 能力权限 `shell:allow-spawn`）。改完后重新 `tauri build` 即恢复。

**排查方法（Mac 上若再遇「启动即退出」）**：在终端直接运行主程序即可看到 panic 堆栈：

```bash
"/Applications/KnowFlow 学习工作台.app/Contents/MacOS/knowflow-desktop"
# 或本地路径：
"src-tauri/target/release/bundle/macos/KnowFlow 学习工作台.app/Contents/MacOS/knowflow-desktop"
```

**仍无界面时的二次排查**：若已越过插件初始化但仍空白，多半是后端侧车未就绪/崩溃。
检查：① 日志 `~/Library/Logs/KnowFlow`（如有写入）；② 终端 `lsof -i:8787` 看端口是否监听；
③ 直接跑侧车验证：`Contents/MacOS/server Contents/Resources/api/index.js --port 8787
--web-dir Contents/Resources/web --data-dir /tmp/test`。


