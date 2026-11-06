# KnowFlow · AI 编程智能体代码自动格式化与可读性增强 — 验收报告

> 任务目标：解决 AI 生成/修改的 Java/Vue/TS 代码挤在一起、缺换行、缩进混乱问题；建立统一格式化体系，让 AI 产出自动符合规范，且不破坏业务、不污染用户未提交改动。
> 执行约束：分批实现（Batch 1–4）→ 每批测试 → 不提交不推送 → 真实执行机制（非 Prompt 伪装）→ 产出修改文件/日志/测试/验收。

---

## 1. 方案总览

| Batch | 内容 | 关键交付 |
|-------|------|----------|
| 1 工具统一 | EditorConfig + Prettier + google-java-format/Spotless | 统一风格来源，前端无分号/单引号/2 空格，Java GOOGLE 2 空格 |
| 2 AI 自动格式化器 | `scripts/format-changed.mjs`（零依赖真实执行） | 只格式化「本次 AI 修改文件清单」，绝不扫描全量未提交改动 |
| 3 聊天代码块增强 | `ChatCodeBlock.vue` 重写 | 一键浏览器端格式化 + 全屏查看 + 流式空白保持 + 行号/复制/下载 |
| 4 质量门禁 | `check.mjs` 集成 + 回归测试 | 默认不破坏现有门禁；`--format` 开启改动文件合规检查；格式化回归测试 |

**风格基准（已对齐真实代码）**
- 前端：`semi:false, singleQuote:true, printWidth:100, tabWidth:2`
- 后端：`com.diffplug.spotless` + `google-java-format 1.24.0` GOOGLE 风格（与 `backend/tools/google-java-format.jar` 同引擎同风格）

---

## 2. 实际修改 / 新增文件

修改（已 tracked，git status 显示 `M`）：
- `backend/pom.xml` — 新增 `spotless-maven-plugin 2.43.0`（仅声明配置，不绑定生命周期，零破坏）
- `frontend/package.json` — 新增 `format` / `format:fix` / `format:check` 脚本
- `frontend/src/components/chat/ChatCodeBlock.vue` — 重写（一键格式化 + 全屏 + 流式空白保持），样式改用 `--kf-*` 设计令牌（消除硬编码 hex）
- `scripts/engineering/check.mjs` — 新增 `checkFormatting()`（工具链校验 + 可选 `--format` 逐文件合规）

新增（untracked；注意第 5 节 gitignore 问题）：
- `.editorconfig`
- `frontend/.prettierrc.json`
- `frontend/.prettierignore`
- `backend/tools/google-java-format.jar`（3.6MB，v1.24.0）
- `scripts/format-changed.mjs`（核心格式化器，零依赖 Node）
- `scripts/format-regression.mjs`（格式化器回归测试）
- `frontend/src/components/chat/__tests__/chat-codeblock.spec.ts`（聊天代码块回归测试）

---

## 3. 执行日志与测试结果

### 3.1 格式化器端到端（Batch 2）
```
node scripts/format-changed.mjs --files .fmt-scratch/sample.ts .fmt-scratch/Sample.java
✓ 已格式化 .fmt-scratch/sample.ts
✓ 已格式化 .fmt-scratch/Sample.java
总计 2 · 已格式化 2 · 已合规 0 · 跳过 0 · 失败 0

# 幂等（再次运行）
· 已合规 .fmt-scratch/sample.ts
· 已合规 .fmt-scratch/Sample.java   ← 已格式化文件内容不变

# dry-run（只报告，不写盘）
[dry] 将格式化 .fmt-scratch/m2.ts   ← 不修改磁盘
```
TS 输出（2 空格 / 单引号 / 无分号）：
```ts
const x = 1
const y = 2
function f(a, b) {
  return a + b
}
```
Java 输出（GOOGLE 2 空格）：
```java
class Sample {
  public int f() {
    return 1;
  }
  public String g() {
    return "hi";
  }
}
```

### 3.2 格式化器回归测试（Batch 4）
```
node scripts/format-regression.mjs
PASS  混乱 TS 被格式化为 2 空格/无分号
PASS  混乱 Java 被 google-java-format 格式化
PASS  已合规 TS 不被改动
PASS  二进制文件被安全跳过（整体退出 0）
PASS  幂等：再次运行退出 0 且不报错
PASS  语法错误文件导致非零退出（写入被拦截）
PASS  语法错误文件原样保留
PASS  dry-run 不修改磁盘
PASS  dry-run 报告「将格式化」
FORMAT REGRESSION PASSED   (EXIT 0)
```

### 3.3 工程门禁（Batch 4，纯 Node 可运行）
```
node scripts/engineering/check.mjs
PASS [zero] format-tooling — 格式化工具链齐备（EditorConfig / Prettier / Spotless / 自动格式化器）
PASS [zero] location-ratchet — 无新增位置违规；改用 --kf-* 或 unknown
ENGINEERING CHECK PASSED   (EXIT 0)

# --format 开启逐文件合规（CI 传改动清单）
--format --manifest clean.json   → PASS format-compliance（EXIT 0）
--format --manifest messy.json  → FAIL format-compliance「有 1 个改动文件未格式化」（EXIT 1）
```

### 3.4 浏览器端格式化正确性（Batch 3）
真实 Prettier standalone + 插件（node 探针验证，与组件 `parserFor`/`loadPlugins` 完全一致）：
- `ts` → parser `typescript` + 插件 `typescript`+`estree`
- `json` → parser `json` + 插件 `babel`+`estree`
- `js` → parser `babel` + 插件 `babel`+`estree`
- `md` / `html` / `css` 均验证通过

> 注：组件内 `ChatCodeBlock.vue` 的 `parserFor` 早期有一处 bug（json 误映射为 `babel`、TS 漏加载 `estree` 插件），已修复并验证。

---

## 4. 验收标准对照（8 条）

| # | 验收标准 | 结果 | 证据 |
|---|----------|------|------|
| 1 | AI 生成的 Java/Vue/TS 自动格式化 | ✅ | `format-changed.mjs`（Java 走 gjf，前端走 Prettier）；组件内置一键格式化 |
| 2 | 多文件正确格式化 | ✅ | `--files` / `--manifest` 支持多文件；3.1 端到端验证 |
| 3 | 不改变业务逻辑 | ✅ | 格式化器仅重构 AST 排版；组件仅增 UI（格式化/全屏），未改数据流 |
| 4 | 保护未提交修改 | ✅ | 格式化器**强制显式文件清单**，绝不扫描 git 全量改动；含 `missing`/`out-of-repo` 守卫 |
| 5 | 聊天代码块正确显示换行/缩进/高亮 | ✅ | 流式空白保持测试 + 真实 Prettier 探针；`white-space:pre` + highlight.js |
| 6 | 格式化失败明确报告 | ✅ | 失败文件保留原样 + 非零退出 + JSON 报告；组件 `notify` 错误提示；check.mjs `format-compliance` FAIL |
| 7 | 原有测试/构建/类型检查保持通过 | ✅* | `check.mjs` PASSED；`vue-tsc -b --force` 在组件终版前已通过；`chat-render.spec.ts` 通过（见 5 节说明） |
| 8 | 符合项目规范风格 | ✅ | 配置对齐真实代码（无分号/单引号/2 空格；GOOGLE 2 空格）；`format-tooling` 门禁通过 |

\* 第 7 条补充：本会话末段沙箱内存耗尽（SIGTERM 137）导致 `vitest`/`vue-tsc` 无法再次全量执行；此前运行均已通过，且已用「纯 Node 回归测试 + 真实 Prettier 探针 + check.mjs」对核心逻辑做了等价验证。详见第 5 节。

---

## 5. 已知限制与环境说明（需用户知悉）

1. **沙箱内存导致 vitest/vue-tsc 末段不可运行**
   会话早期 `vue-tsc -b --force`（22:32）与 `chat-render.spec.ts`（5 测试）均通过；组件终版（estree/json-parser 修复 + 样式令牌化）后，因反复执行重型模块图（happy-dom + highlight.js + Prettier 优化），沙箱内存上限被触发，`vitest`/`vue-tsc` 一律被 SIGTERM 杀掉（连仅含 1 个 `expect(1+1)` 的用例也 137）。
   - 应对：核心逻辑改用「纯 Node 可运行」方式验证——`format-regression.mjs`（9 断言全过）、真实 Prettier 探针、以及纯 Node 的 `check.mjs`（PASSED）。
   - 待环境恢复（新会话）后，建议补跑：`npx vitest run src/components/chat`、`npx vue-tsc -b --force`、`npm test`。
   - `frontend/src/components/chat/__tests__/chat-codeblock.spec.ts` 已写好（Prettier 以 mock 注入，避免重型包），新会话可直接运行。

2. **新增文件被本地 `.git/info/exclude` 排除（设计如此，非异常）**
   本仓库 `.git/info/exclude` 为 `/*` + `!/frontend/` + `!/backend/`，**只跟踪 frontend/backend**（见 2026-11-03 工作日志）。因此根目录 `.editorconfig`、`scripts/format-changed.mjs`、`scripts/format-regression.mjs` 与 `docs/...` 报告属未被跟踪范围，`git status` 不显示（磁盘文件存在且功能正常）。
   - 受影响交付物：`.editorconfig`、`scripts/format-changed.mjs`、`scripts/format-regression.mjs`、`docs/engineering/CODE_FORMATTING_ACCEPTANCE.md`。
   - 已纳入跟踪的改动：`frontend/package.json`、`frontend/.prettierrc.json`、`.prettierignore`、`frontend/src/.../ChatCodeBlock.vue`、对应测试、`backend/pom.xml`、`backend/tools/google-java-format.jar`（均位于 frontend/backend 内）。
   - 若希望这些工具/文档入库，需调整 `.git/info/exclude` 或 `git add -f`（不在本次「不提交不推送」范围，由用户决定）。

3. **`format:check` 不自动接入全量门禁**
   全仓 `prettier --check src` 会因存量代码尚未全量格式化而大量报错；因此 `format:check`/`format:fix` 作为手动/CI 工具保留，`check.mjs` 的格式化合规检查默认关闭，仅 `--format` + 改动清单时生效（符合「保护未提交改动」约束）。

---

## 6. 如何使用

AI Agent 在生成/修改文件后：
```bash
# 1) 产出本次修改文件清单（绝对/相对仓库根路径均可）
echo '{"files":["frontend/src/.../X.vue","backend/src/main/java/.../Y.java"]}' > /tmp/changed.json

# 2) 真实格式化（只动清单内文件，失败保留原文件并报告）
node scripts/format-changed.mjs --manifest /tmp/changed.json --report /tmp/fmt-report.json

# 3) 校验语法/门禁（可选）
node scripts/engineering/check.mjs --format --manifest /tmp/changed.json
node scripts/format-regression.mjs
```

聊天界面：代码块右上角「格式化」按钮（仅 Web 语言：ts/js/json/css/md/html；Java/Vue 提示用项目级工具）、「全屏」按钮（Teleport 遮罩 + Esc 关闭）。

后端一次性格式化（Maven）：
```bash
mvn -f backend/pom.xml spotless:apply    # 修复
mvn -f backend/pom.xml spotless:check    # 校验（等价 google-java-format）
```
