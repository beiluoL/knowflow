# KnowFlow UI 设计系统（单一真实来源）

> **版本**：v2.0
> **更新日期**：2026-08-28
> **用途**：作为 KnowFlow 全部前端 UI / 界面设计决策的单一真实来源（Single Source of Truth）。涵盖项目定位、设计令牌、排版层级、组件规范、布局约定，以及 137 个 `.vue` 文件的界面规范覆盖计划与进度。所有新增 / 修改的 UI 页面与组件必须严格遵循本文档。
>
> **合并说明**：本文档由 `docs/vibecoding/ui-design-prompt.md`（v1.0 设计系统参考）与 `doc/改造与提交/前端界面规范覆盖计划.md`（界面规范覆盖计划）合并而成，原两文件已废弃删除。

---

## 一、项目核心定位

KnowFlow 是一个 **AI 驱动的全链路知识学习平台**，构建「智能学习门户 → 沉浸式学习助手 → 实战演练场 → 学习成果度量 → 社区激励」的完整闭环。

**技术栈**：
- 前端：Vue 3 `<script setup lang="ts">` + Composition API + Pinia + Vue Router + Tailwind CSS
- 后端：Spring Boot 3.2 + MyBatis-Plus + H2/MySQL + JWT + WebSocket
- AI：OpenAI 兼容接口（用户级 API Key 自配置，支持 11+ 提供商）

> 完整已实现功能模块与待开发功能见「附录 A / 附录 B」。

---

## 二、设计令牌（Design Tokens）—— 单一真实来源

所有颜色、字号、间距、圆角必须使用以下 **--kb-\* CSS 变量**，禁止写死十六进制色值或像素值。令牌定义来源：`frontend/src/style.css`。

### 2.1 颜色系统

| 变量名 | 值 | 用途 |
|--------|-----|------|
| `--kb-primary` | `#3B6FE0` | **品牌主蓝**：按钮、链接、激活态、强调边框 |
| `--kb-primary-foreground` | `#FFFFFF` | 主色背景上的文字 |
| `--kb-highlight` | `#FF6B35` | **高光暖橘**：成就解锁、连续打卡火焰、AI 推荐徽章、升级庆祝（仅用于小面积强调，80/20 主辅关系） |
| `--kb-highlight-soft` | `rgba(255, 107, 53, 0.10)` | 高光软背景（徽章底色） |
| `--kb-highlight-border` | `rgba(255, 107, 53, 0.30)` | 高光边框 |
| `--kb-accent` | `#10B981` | 成功 / 通过绿（AC 状态、正确答案） |
| `--kb-destructive` | `#EF4444` | 危险 / 错误红（WA 状态、删除操作） |
| `--kb-warning` | `#F59E0B` | 警告黄（TLE 状态、待处理） |
| `--kb-background` | `#F7F8FA` | 页面背景 |
| `--kb-card` | `#FFFFFF` | 卡片 / 容器背景 |
| `--kb-sidebar` | `#F0F2F5` | 侧边栏背景（略深于内容区） |
| `--kb-muted` | `#E8ECF1` | 禁用态 / 分隔条背景 |
| `--kb-border` | `#E2E6EC` | 边框 / 分割线 |
| `--kb-foreground` | `#1A1D23` | 主要文字 |
| `--kb-muted-foreground` | `#6B7280` | 次要 / 辅助文字 |

**深色模式**（`data-theme="dark"`）：所有变量自动切换为深色色板，代码无需改动。

### 2.2 字体系统

| 变量名 | 字体 | 用途 |
|--------|------|------|
| `--font-sans` | Noto Sans SC（中文）+ system-ui | **正文默认**：功能型文字、菜单、表单 |
| `--font-serif` | Noto Serif SC（衬线） | **H1/H2/H3 展示标题**：杂志感大标题 |
| `--font-display` | Noto Serif SC, 900 | **Hero 大标题**：比 serif 更重更紧 |
| `--font-mono` | JetBrains Mono | **代码 / 数字**：等宽带连字 |

### 2.3 字号阶梯（Typography Scale）

| 层级 | 变量 | 字号 / 行高 / 字重 | 场景 |
|------|------|---------------------|------|
| H1 | `--kb-fs-h1` | 40px / 1.2 / 700 | Hero 大标题（serif） |
| H2 | `--kb-fs-h2` | 32px / 1.25 / 700 | 页面主标题（serif） |
| H3 | `--kb-fs-h3` | 24px / 1.3 / 600 | 区块标题（serif） |
| H4 | `--kb-fs-h4` | 20px / 1.4 / 600 | 卡片标题（sans，默认） |
| body-lg | `--kb-fs-body-lg` | 16px / 1.6 / 400 | 正文大 |
| **body-md** | `--kb-fs-body-md` | **14px / 1.6 / 400** | **正文默认 / 导航菜单** |
| body-sm | `--kb-fs-body-sm` | 13px / 1.5 / 400 | 辅助正文 / 下拉菜单 |
| caption | `--kb-fs-caption` | 12px / 1.5 / 500 | 标签 / 分组说明 |
| xs | `--kb-fs-xs` | 11px / 1.4 / 500 | 角标 / 时间 |

### 2.4 图标尺寸阶梯（偶数语义化）

| 语义 | 变量 | 像素 | 使用场景 |
|------|------|------|----------|
| xxs | `--kb-icon-xxs` | 10px | 列表内点 / 角标 |
| xs | `--kb-icon-xs` | 12px | 面包屑 / Tag |
| sm | `--kb-icon-sm` | 14px | 下拉箭头 / 辅助图标 |
| **md** | `--kb-icon-md` | **16px** | **导航菜单 / 表单 / 默认值** |
| lg | `--kb-icon-lg` | 18px | 移动端菜单 / 按钮图标 |
| xl | `--kb-icon-xl` | 20px | Logo / 通知铃铛 / 顶部操作 |
| 2xl | `--kb-icon-2xl` | 24px | 卡片 / Hero 区 |
| 3xl | `--kb-icon-3xl` | 32px | 空状态 / 插画 |

### 2.5 间距（4px 网格）

| 语义 | 值 |
|------|-----|
| space-1 | 4px |
| space-2 | 8px |
| space-3 | 12px |
| **space-4** | **16px（默认）** |
| space-5 | 20px |
| space-6 | 24px |
| space-8 | 32px |

### 2.6 圆角与阴影

| 变量 | 值 | 场景 |
|------|-----|------|
| `--kb-radius-sm` | 6px | 小按钮 / Tag |
| `--kb-radius-md` | 10px | **默认（卡片 / 输入框）** |
| `--kb-radius-lg` | 16px | 大卡片 / Modal |
| `--shadow-card` | `0 2px 8px 0 rgba(0,0,0,0.04)` | 卡片默认阴影 |
| `--shadow-card-hover` | `0 8px 24px 0 rgba(0,0,0,0.08)` | 卡片 hover 阴影 |

---

## 三、排版层级（Typography Hierarchy）

### 3.1 杂志感标题工具类

```html
<h1 class="kb-display">Hero 大标题</h1>     <!-- 44px 900 衬线 + 紧字距 -->
<h2 class="kb-h1">页面主标题</h2>            <!-- 32px 700 衬线 -->
<h3 class="kb-h2">区块标题</h3>              <!-- 24px 600 衬线 -->
<h4 class="kb-h3">小节标题</h4>              <!-- 20px 600 衬线 -->
<h5 class="kb-h4">卡片标题</h5>              <!-- 20px sans -->
<p class="kb-body">正文文字</p>               <!-- 14px sans 默认 -->
<p class="kb-body-sm">辅助文字</p>            <!-- 13px 灰 -->
<span class="kb-caption">CAPTION LABEL</span> <!-- 12px 大写 + 字距 -->
```

### 3.2 工具类与令牌速查表

#### 文字颜色

| 工具类（Tailwind） | 映射变量 | 色值 |
|--------------------|----------|------|
| `text-primary-500` / `text-primary-600` | `--kb-primary` | #3B6FE0 |
| `text-success-500` | `--kb-accent` | #10B981 |
| `text-danger-500` / `text-danger-600` | `--kb-destructive` | #EF4444 |
| `text-warning-500` | `--kb-warning` | #F59E0B |
| `text-gray-400/500` | `--kb-muted-foreground` | #6B7280 |
| `text-gray-700/800/900` | `--kb-foreground` | #1A1D23 |
| 自定义高光 `.highlight-text` | `--kb-highlight` | #FF6B35 |

#### 背景颜色

| 工具类 | 映射变量 |
|--------|----------|
| `bg-white` | `--kb-card` |
| `bg-gray-50/100` | `--kb-muted` |
| `bg-primary-500` | `--kb-primary` |
| `bg-primary-500/10` / `bg-primary-50` | 主色软背景 |
| 自定义 `.highlight-badge` | `--kb-highlight-soft` + border |

#### 边框

| 工具类 | 映射变量 |
|--------|----------|
| `border-gray-200` | `--kb-border` (#E2E6EC) |
| `border-primary-500` | `--kb-primary` |
| `focus:ring-primary-500/30` | `0 0 0 3px rgba(59,111,224,0.1)` |

---

## 四、组件规范

所有页面优先使用 `@/components/ui/` 下的现有组件，**禁止自行造轮子**。

### 4.1 基础组件

| 组件 | 路径 | Props / 用法 |
|------|------|-------------|
| **Icon** | `@/components/ui/Icon.vue` | `name`（iconfont 类名，如 `icon-AIbeikezhushou`），`size`（语义化：xxs/xs/sm/md/lg/xl/2xl/3xl 或数字） |
| **Button** | `@/components/ui/Button.vue` | `variant`（primary/secondary/ghost/destructive），`size`（sm/md/lg），`loading` |
| **Card** | `@/components/ui/Card.vue` | 默认卡片容器，含 hover 阴影过渡；具名插槽：`#header`、`#default`、`#footer` |
| **Badge** | `@/components/ui/Badge.vue` | `variant`（primary/success/warning/danger/highlight） |
| **Avatar** | `@/components/ui/Avatar.vue` | `src` / `name`（fallback 首字母），`size`（sm/md/lg） |
| **Input** | `@/components/ui/Input.vue` | 封装输入框，支持 `prefix`/`suffix` 插槽 |
| **Progress** | `@/components/ui/Progress.vue` | `value`（0-100），`variant`（primary/success/warning） |
| **Pagination** | `@/components/ui/Pagination.vue` | `:total`、`:pageNum`、`:pageSize`、`@change` |
| **EmptyState** | `@/components/ui/EmptyState.vue` | `:title`、`:description`、`:icon` |
| **SkeletonList** | `@/components/ui/SkeletonList.vue` | `:count`、`:rows` 骨架屏 |
| **PageHeader** | `@/components/ui/PageHeader.vue` | `:title`、`:subtitle`、`#extra` 插槽（操作按钮） |

### 4.2 表单控件规范（统一视觉）

所有 `<select>` 统一加 `.kb-select` 类：
- 38px 高度 / 10px 圆角 / `--kb-border` 边框
- focus 主色高亮环（3px 透明度 10%）
- 内联 SVG 箭头，无需额外资源
- 紧凑变体：`.kb-select-sm`（32px，工具栏用）

筛选按钮组：
```html
<div class="kb-filter-group">
  <button class="kb-filter-btn active">全部 <span class="filter-count">12</span></button>
  <button class="kb-filter-btn">已完成</button>
</div>
```

自定义弹出下拉（非原生 select）：
```html
<div class="kb-dropdown">
  <button class="kb-dropdown-trigger">
    <span class="trigger-label">选择章节</span>
    <Icon name="chevron-down" class="trigger-arrow" />
  </button>
  <div class="kb-dropdown-panel">
    <div class="kb-dropdown-item">选项1</div>
    <div class="kb-dropdown-item selected">选项2</div>
  </div>
</div>
```

### 4.3 Toast 与对话框

**严禁使用原生对话框**（`window.alert/confirm/prompt`），统一使用：
```typescript
import { notify, confirmDialog, promptDialog, getApiError } from '@/utils/toast'

notify('操作成功', 'success')       // toast 提示：success/error/warning/info
confirmDialog({ title: '确认删除？', content: '此操作不可撤销' })  // 确认弹窗 → Promise<boolean>
promptDialog({ title: '重命名', defaultValue: 'xxx' })            // 带输入框 → Promise<string|null>
```

Error catch 统一使用 `catch(e: unknown)` + `getApiError(e)` 获取消息。

### 4.4 高光时刻工具类（暖橘专用）

```html
<span class="highlight-badge">🔥 连续 7 天</span>   <!-- 徽章：软背景 + 边框 -->
<span class="highlight-text">+120 XP</span>        <!-- 文字染色 -->
<div class="highlight-glow particle-burst is-bursting"></div> <!-- 粒子爆开动效 -->
```

### 4.5 代码块全局样式（Markdown / Chat 共享）

```html
<div class="code-block-wrapper">
  <div class="code-block-header">
    <span class="code-lang">javascript</span>
    <button class="code-copy-btn">📋 复制</button>
  </div>
  <div class="code-block-body">
    <div class="code-line-numbers"><span class="code-line-num">1</span></div>
    <pre><code class="hljs language-js">...</code></pre>
  </div>
</div>
```

- 深色背景 `#1a1d23` + 10px 圆角边框
- 左侧独立行号列（不可选中，深色背景）
- JetBrains Mono + highlight.js github-dark 主题
- 右上角复制按钮，点击后 2 秒显示「已复制」绿色提示

### 4.6 动效系统

#### 4.6.1 入场动效（Staggered Reveal）

```html
<div class="reveal-stagger">
  <div class="reveal-item" style="--reveal-index: 0">卡片1</div>
  <div class="reveal-item" style="--reveal-index: 1">卡片2</div>
</div>
```

- 子项按 `--reveal-index` 顺序延迟淡入上滑（每项 60ms 间隔）
- 动画时长 0.5s ease-out

#### 4.6.2 成就解锁粒子爆开

```html
<div class="particle-burst is-bursting">
  <span class="highlight-badge">🏆 新成就解锁！</span>
</div>
```

- CSS-only 实现，8 方向粒子扩散
- 配合 `.highlight-pulse` 脉冲阴影

#### 4.6.3 全局无障碍守护

```css
@media (prefers-reduced-motion: reduce) {
  /* 自动禁用所有动画与过渡，避免前庭不适 */
}
```

### 4.7 代码风格硬约束（MANDATORY）

#### 4.7.1 Vue 组件模板

```vue
<script setup lang="ts">
// 1. import：先 type 再 runtime
import type { Ref, InjectionKey } from 'vue'   // ✅ 必须带 type 前缀
import { ref, computed, onMounted } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import { useAuthStore } from '@/stores/auth'
import { notify, getApiError } from '@/utils/toast'

// 2. defineProps / defineEmits（如需要）
const props = defineProps<{
  title: string
  count?: number
}>()
const emit = defineEmits<{
  'update:modelValue': [value: string]
  'submit': []
}>()

// 3. 响应式状态
const loading = ref(false)
const data = ref<SomeType[]>([])

// 4. 计算属性
const isEmpty = computed(() => data.value.length === 0)

// 5. 方法（async/await + catch 规范）
async function fetchData() {
  loading.value = true
  try {
    const res = await someApi.getList({ pageNum: 1, pageSize: 20 })
    data.value = res.list
  } catch (e: unknown) {                                  // ✅ catch(e: unknown)，禁止 any
    notify(getApiError(e), 'error')                        // ✅ 统一用 getApiError
  } finally {
    loading.value = false
  }
}

// 6. 生命周期
onMounted(fetchData)
</script>

<template>
  <div class="space-y-4">
    <!-- ✅ 连字符类名用引号包裹 -->
    <div :class="{ 'send-error': hasError, 'is-active': active }">
      <!-- ✅ 严格使用 Icon/Button/Card 等 UI 组件 -->
      <PageHeader :title="props.title" />
      <SkeletonList v-if="loading" :count="5" :rows="3" />
      <EmptyState v-else-if="isEmpty" title="暂无数据" />
      <Card v-else>
        <ul class="reveal-stagger">
          <li
            v-for="(item, idx) in data"
            :key="item.id"
            class="reveal-item p-4"
            :style="{ '--reveal-index': idx }"
          >
            {{ item.name }}
          </li>
        </ul>
      </Card>
    </div>
  </div>
</template>
```

#### 4.7.2 API 层规范

- 所有请求封装在 `frontend/src/api/*.ts` 中
- 使用 `request.ts` 封装的 axios 实例（自动带 JWT、统一错误处理）
- TypeScript 类型安全：定义 `XxxVO` / `XxxDTO` interface

#### 4.7.3 Markdown 处理规范

1. **渲染配置**：`markdown-it` 设置 `breaks: true`（单行换行转 `<br>`）
2. **换行还原**：数据库存储的字面量 `\n` 必须通过 `normalizeNewlines()` 还原为真实换行符再渲染
3. **TOC 锚点**：h2/h3 元素 id 统一使用 `heading-${seq}` 序号（`heading-1`、`heading-2`...），确保目录跳转正确

#### 4.7.4 Mermaid 图配置

```typescript
// ❌ 不支持 CSS 变量，必须写实际色值
mermaid.initialize({
  theme: 'base',
  themeVariables: {
    primaryColor: '#3B6FE0',      // ✅ 十六进制，不能用 var(--kb-primary)
    primaryTextColor: '#FFFFFF',
    primaryBorderColor: '#2F59B3',
    lineColor: '#6B7280',
    fontFamily: 'Noto Sans SC',
  }
})
```

### 4.8 界面规范五原则与落地动作

> 改造硬约束（红线）：整体 UI 风格不可改变，只补一致性与缺失反馈；不碰 `<script>` / 逻辑 / 共享全局文件；不改配色与布局结构。

| 原则 | 落地动作（最小侵入） |
|---|---|
| **① 布局间距** | 魔法间距（14/10/18px 等）收敛到 4px 网格（`gap-2/3/4/5/6`、`p-4/6`）；圆角映射 `--kb-radius-*`；视觉层次不变 |
| **② 字体排版** | 写死字号映射 `--kb-fs-h1~h4` / `--kb-fs-body-md`(14) / `--kb-fs-body-sm`(13) / `--kb-fs-caption`(12)；等宽用 `--font-mono`；数字列加 `tabular-nums` |
| **③ 交互反馈**（最高优先级） | 每个可交互元素补 `hover`+`active`+`focus-visible` 三态；`<div @click>` 死按钮转 `<button type="button">`，复杂结构加 `role="button" tabindex="0"` + `@keydown.enter.prevent="($event.target as HTMLElement).click()"`；状态变化加 `transition-colors` |
| **④ 图标图形** | 尺寸统一 16/20/24px 刻度；装饰图标 `aria-hidden="true"`；统一用 `<Icon>` 或 `lucide-vue-next`，不混用 |
| **⑤ 响应式** | 含文字 flex 子项加 `min-w-0`/`flex-1`；长文本 `truncate`/`.text-ellipsis`；必要处 `flex-wrap`；内边距 `px-4 md:px-6`；桌面布局不变 |

**色值令牌化**：写死偏离色（`#3b82f6`、旧绿 / 橙 / 红等）替换为 `--kb-primary` / `--kb-accent` / `--kb-warning` / `--kb-destructive`。

---

## 五、布局约定（Layout Convention）

Vue Router `meta.layout` 决定页面外壳：

### 5.1 C 端布局（`meta.layout: 'c'`）
- **结构**：固定顶部导航栏（Topbar.vue，高度 56px）+ 全屏内容区
- **顶栏内容**：Logo + 知识库导航下拉 + 搜索框 + 主题切换 + 通知铃铛 + 用户菜单
- **内容区背景**：`.kb-region-content`（纵向渐变，顶部亮底部沉）
- **适用页面**：首页、知识库、学习路径、社区、任务中心、学习报告等

### 5.2 B 端布局（`meta.layout: 'b'`）
- **结构**：左侧固定侧边栏（Sidebar.vue，宽 240px 可折叠）+ 顶部栏（BTopbar.vue）+ 可滚动内容区
- **侧边栏**：分组导航（知识库管理、文档管理、用户管理等），激活项主色填充
- **适用页面**：所有 `/admin/*` 路由（管理后台）

### 5.3 无布局（`meta.layout: 'none'`）
- **结构**：全屏独立页面，无顶栏 / 侧栏
- **适用场景**：登录页、沉浸式学习模式（FocusMode/LearningMode）、代码 Playground、404
- **背景**：通常自定义深色 / 渐变色背景

### 5.4 功能区背景语义化类

| 类名 | 视觉效果 | 场景 |
|------|----------|------|
| `.kb-region-topbar` | 卡片色 + 底部分割线 | 顶栏区域 |
| `.kb-region-sidebar` | 侧栏色 + 右部分割线 | 侧边栏区域 |
| `.kb-region-content` | 纵向渐变（卡→背景） | 主内容区 |
| `.kb-region-accent` | 主色径向渐变氛围 | Hero / AI 对话 signature 区 |

---

## 六、界面规范覆盖计划与进度

> 目标：将**全部**前端页面 / 组件逐步拉回项目设计系统（`frontend/src/style.css` 的 `--kb-*` 令牌 + 共享类），满足「界面开发五原则」（①布局间距 ②字体排版 ③交互反馈 ④图标图形 ⑤响应式）。

### 6.1 进度总览

| 状态 | 文件数 | 占比 | 说明 |
|---|---|---|---|
| ✅ 已合规 | ~118 | 86% | 见 §6.2 |
| 🟡 待改造 | ~19 | 14% | §6.3.8 纯展示组件（不单列，随页面改造自然合规） |
| 合计 | 137 个 `.vue` | 100% | `views/` 84 + `components/` 53（审计实测 137） |

> 审计指标定义：`sh`=采用设计系统共享类；`hov`=hover 态；`foc`=focus-visible 态；`div`=`<div @click>` 死按钮数；`ta`=`transition-all` 反模式次数；`luc`/`ic`=图标用法。判定原则 3 缺失的最强信号是 `foc==0` 且页面含可交互元素。

### 6.2 已合规清单（无需重复改造）

#### 6.2.1 2026-07-29 一轮（P0–P5，约 25 文件）
- **页面**：Login、Home、Chat、LearningCenter、SmartQuiz、FlashCards、ChapterLearn、LearningReport、NotesManage、NoteEdit、SmartWriting、CodePractice、KnowledgeGraph、ReviewPlan、TaskCenter、CheckIn、Profile
- **组件**：CTopNav、Icon、Button、Badge（Topbar / BTopbar 已做 console 清理与部分可访问性，但 `BTopbar` 实测 `foc=0` 仍有 1 处死按钮，见 §6.3.7-G）

#### 6.2.2 2026-08-09 一轮（`9b461fe`，8 个大页面）
CodeAgent、CodePlayground、KnowledgeImport、ObsidianImport、FlashCardsHub、StudyGroup、Messages、BackgroundSettings

#### 6.2.3 2026-08-11 一轮（社区模块 6 文件）
`views/Community.vue`、`views/PostDetail.vue`、`views/PostEdit.vue`、`components/community/CommentItem.vue`、`components/community/CommentInput.vue`、`components/community/CommentList.vue`

#### 6.2.4 2026-08-12 一轮（工作台模块 10 文件）
`views/Workbench.vue`、`views/WorkbenchReview.vue`、`views/WorkbenchRecall.vue`、`views/WorkbenchCapture.vue`、`views/WorkbenchPalace.vue`、`views/WorkbenchPalaceEdit.vue`、`views/WorkbenchNotes.vue`、`views/WorkbenchStory.vue`、`views/WorkbenchStoryEdit.vue`、`views/WorkbenchNoteEdit.vue`

#### 6.2.5 2026-08-13 一轮（管理后台 admin/*，16 文件，含 8 个原审计漏登项）
原 §6.3.5 仅登记 8 个文件；本轮发现 `admin/` 实际有 16 个视图文件、其余 8 个完全漏登（含 `transition-all` 与死按钮），一并补齐。
- **原登记 8 个**：`views/admin/DocManagement.vue`(2076,4 死按钮)、`views/admin/Overview.vue`、`views/admin/FileMgmt.vue`(1 死按钮)、`views/admin/IconManagement.vue`(2 死按钮)、`views/admin/TagManagement.vue`(2 死按钮)、`views/admin/LearningPathMgmt.vue`(1 死按钮)、`views/admin/CommunityManage.vue`、`views/admin/CodeQuestionMgmt.vue`(1 死按钮)
- **漏登补齐 8 个**：`views/admin/KnowledgeMgmt.vue`(1240,7 死按钮)、`views/admin/QuizMgmt.vue`(1060,2 死按钮)、`views/admin/CategoryMgmt.vue`(767,2 死按钮)、`views/admin/FlashcardMgmt.vue`(531,1 死按钮)、`views/admin/ChatConfig.vue`、`views/admin/UserManagement.vue`、`views/admin/ChapterEdit.vue`(872)、`views/admin/DatabaseSettings.vue`(557)

#### 6.2.6 2026-08-14 一轮（账户/杂项 §6.3.6 + 智能体/沉浸式/布局 §6.3.7，共 32 文件）
> 本轮覆盖剩余全部「账户/杂项」与「智能体/沉浸式/布局」界面，闭环 §6.3.6 / §6.3.7。

- **§6.3.6 账户/杂项（9 文件）**：`views/Favorites.vue`(1 死按钮)、`views/Mistakes.vue`、`views/ImportTemplates.vue`(2 死按钮模态遮罩)、`views/Notifications.vue`(1 死按钮)、`views/FocusMode.vue`、`views/KBTitle.vue`、`views/NotFound.vue`、`views/Redirect.vue`、`views/OAuthCallback.vue`
- **§6.3.7 智能体/沉浸式/布局（23 文件）**：`components/layout/BTopbar.vue`(1 死按钮)、`components/agent/`(AgentWorkflowPanel / AgentToolConfirm / AgentToolPanel / AgentCallChain)、`components/immersive/`(AchievementToast / BreakGuide / BuddyMode / DeepMode / DualRingProgress / FlowMode / GraphSidebar / InsightsPanel / LeaderboardPanel / MiniChatCopilot / PomodoroMode / SettingsPanel / ShareCard / SpacedMode / ThemePanel，含 SpacedMode 2 死按钮、GraphSidebar 3 死按钮)、`components/ShareCard.vue`(根级，与 immersive 同名不同文件)、`components/Learning/DagGraph.vue`、`components/WhiteNoisePlayer.vue`
- 本轮共转换 **12 处死按钮**（Favorites / Notifications / ImportTemplates×2 / BTopbar 各 1；BuddyMode / DeepMode 各 1；GraphSidebar 3；SpacedMode 2），补齐约 150+ 处 `focus-visible` 三态 + `transition-colors`，清除 1 处 `transition-all`（Mistakes SVG 进度环 → `transition-[stroke-dashoffset]`）；纯模态遮罩 `@click.stop`/`@click.self` 屏障按规则保留不转按钮。`AchievementToast` / `DualRingProgress` 无交互元素，经核查已合规，无需改动。
- 构建验证：`npm run build` 仍 171 处预存 `<script>` 错误，本批改造**零新增**（grep 校验无 `$event.target.click()` / `@keydown.space` 反模式）。

#### 6.2.7 2026-08-15 补漏（`views/CodeAgent.vue` 3 处漏网死按钮）
> §6.2.2（2026-08-09）已将 CodeAgent 整体标合规，但复盘扫描发现模型卡片内 3 个可点击 `div`（`model-icon` / `model-info` / `model-card-params`，均 `@click.stop="openParamsModal(m)"`）未转可键盘访问。本轮补齐：三处均加 `role="button"` + `tabindex="0"` + `@keydown.enter.prevent.self="($event.target as HTMLElement).click()"` + 焦点环 + `transition-colors`。
> 注：`model-card-params` 内含真实 `<button>` 子元素，故 keydown 加 `.self` 防双重触发；`model-card-actions`（`@click.stop` 纯屏障，含 3 真按钮）与两处 `modal-overlay @click.self` 按规则保留不转。构建验证：`npm run build` 仍 171 处预存错误，零新增。

### 6.3 待改造清单（按模块 + 优先级）

> 优先级说明：
> - **P0**：用户高频核心页 + 高偏离（死按钮多 / 完全未用设计令牌 / 体量大）→ 优先覆盖
> - **P1**：次级页 / 中体量 / 部分已较好（少量补 `foc`/`hover` 即可）
> - **P2**：低频 / 简单 / 纯展示（可随页面改造自然合规，不单列强制）

#### 6.3.1 知识库模块（最高频，建议最先覆盖）—— 全部 P0 ✅ 2026-08-10 已全部合规
| 文件 | 行数 | div死按钮 | focus缺失 | 主要问题 |
|---|---|---|---|---|
| `views/KnowledgeHome.vue（✅ 2026-08-10）` | 2140 | 1 | ✅ | 体量大、未补交互三态、图标混用 |
| `views/KnowledgeUpload.vue（✅ 2026-08-10）` | 1792 | 1 | ✅ | 死按钮、缺 focus 态 |
| `views/DocEdit.vue（✅ 2026-08-10）` | 1779 | 1 | ✅ | 死按钮、缺 focus；⚠️ 另有 `v-html` 不转义安全隐患（与样式无关，单列跟踪） |
| `views/DocDetail.vue（✅ 2026-08-10）` | 992 | 0 | ✅ | 已有部分 hover，缺 focus |
| `views/LocalReader.vue（✅ 2026-08-10）` | 2835 | 0 | ✅ | **全站最大文件**，纯阅读器，缺交互态/令牌化 |
| `views/SearchResult.vue（✅ 2026-08-10）` | 961 | 0 | ✅ | 完全未补 focus |
| `views/Categories.vue（✅ 2026-08-10）` | 735 | 0 | ✅ | `sh=0` 完全未用设计令牌，最偏离 |
| `views/DocCreate.vue（✅ 2026-08-10）` | 746 | 0 | ✅ | 缺 focus |
| `views/KnowledgeCreate.vue（✅ 2026-08-10）` | 782 | 0 | ✅ | 缺 focus |
| `views/UploadDoc.vue（✅ 2026-08-10）` | 819 | 0 | ✅ | 缺 focus |
| `views/Docs.vue（✅ 2026-08-10）` | 373 | 0 | ✅ | `transition-all=2` 反模式、缺 focus |

#### 6.3.2 学习模块 ✅ 2026-08-10 已全部合规
| 文件 | 行数 | div | foc缺失 | 优先级 | 主要问题 |
|---|---|---|---|---|---|
| `views/LearningPaths.vue（✅ 2026-08-10）` | 1207 | 2 | ✅ | P0 | 死按钮×2、缺 focus |
| `views/PathDetail.vue（✅ 2026-08-10）` | 1213 | 0 | ✅ | P0 | 缺 focus |
| `views/Challenge.vue（✅ 2026-08-10）` | 378 | 0 | ✅ | P0 | `transition-all=1`、缺 focus |
| `views/ChallengePlay.vue（✅ 2026-08-10）` | 702 | 1 | ✅ | P0 | 死按钮、缺 focus |
| `views/WeeklyReport.vue（✅ 2026-08-10）` | 1400 | 0 | ✅ | P0 | 体量大、缺 focus |
| `views/LearningReportView.vue（✅ 2026-08-10）` | 448 | 0 | ❌(foc=3) | P1 | 已较好，少量补 hover 即可 |
| `views/LearningPrefs.vue（✅ 2026-08-10）` | 515 | 0 | ✅ | P1 | 缺 focus |
| `views/Achievement.vue（✅ 2026-08-10）` | 451 | 0 | ✅ | P2 | 偏展示 |
| `views/Certificate.vue（✅ 2026-08-10）` | 417 | 0 | ✅ | P2 | 偏展示 |
| `views/LearningMode.vue（✅ 2026-08-10）` | 437 | 0 | ✅ | P2 | `transition-all=2` |

#### 6.3.3 社区模块 —— 全部 P0/P1 ✅ 2026-08-11 已全部合规
| 文件 | 行数 | div | foc缺失 | 优先级 | 主要问题 |
|---|---|---|---|---|---|
| `views/Community.vue（✅ 2026-08-11）` | 359 | 0 | ✅ | P0 | 原 `<div @click>` 死按钮（帖子卡片）已转 `role=button`+`tabindex`+回车/空格可达；发布/排序/分类/热门/关注等可交互元素补齐 `focus-visible` 三态+`active`；`bg-blue-50`→`bg-primary-50` 统一主色软背景 |
| `views/PostDetail.vue（✅ 2026-08-11）` | 264 | 0 | ✅ | P0 | 返回/返回社区/点赞按钮补 `focus-visible` 三态+`active` |
| `views/PostEdit.vue（✅ 2026-08-11）` | 154 | 0 | ✅ | P1 | 返回/发布/分类/取消/发布帖子按钮补 `focus-visible` 三态+`active` |
| `components/community/CommentItem.vue（✅ 2026-08-11）` | 346 | 0 | ✅ | P1 | 已有 `focus-visible`，将 `ring-primary-200`/`ring-danger-200` 归一化为 `ring-[var(--kb-ring)]`/`ring-[var(--kb-destructive)]` + `ring-offset-2` |
| `components/community/CommentInput.vue（✅ 2026-08-11）` | 134 | 0 | ✅ | P1 | 同上，焦点环令牌归一化 |
| `components/community/CommentList.vue（✅ 2026-08-11）` | 194 | 0 | ✅ | P1 | 同上，排序按钮焦点环令牌归一化 |

#### 6.3.4 工作台模块（Workbench*，普遍 `div=1 foc=0`）—— 全部 ✅ 2026-08-12 已全部合规
| 文件 | 行数 | div | 优先级 | 主要问题 | 改造说明 |
|---|---|---|---|---|---|
| `views/WorkbenchReview.vue（✅ 2026-08-12）` | 724 | 0 | P0 | 死按钮、缺 focus | 2 处死按钮（quiz-face / drawer-mask）转 `role=button`+`tabindex`+回车可达；19 个交互元素补 `focus-visible` 三态+`transition-colors`；装饰图标加 `aria-hidden` |
| `views/WorkbenchRecall.vue（✅ 2026-08-12）` | 949 | 0 | P0 | 死按钮、缺 focus | 2 处死按钮（drawer-mask / session-card）转 `role=button`+`tabindex`；15 个交互元素补 focus ring；图标 `aria-hidden` |
| `views/WorkbenchCapture.vue（✅ 2026-08-12）` | 473 | 0 | P0 | 死按钮、缺 focus | 1 处死按钮（drawer-mask）转 `role=button`；17 个交互元素补 focus ring |
| `views/Workbench.vue（✅ 2026-08-12）` | 800 | 0 | P1 | 缺 focus | 无死按钮；5 个可点击卡片/按钮补 `focus-visible` 三态+`transition-colors` |
| `views/WorkbenchPalace.vue（✅ 2026-08-12）` | 388 | 0 | P1 | 死按钮、缺 focus | 2 处死按钮（palace-card / drawer-mask）转 `role=button`；14 个交互元素补 focus ring |
| `views/WorkbenchPalaceEdit.vue（✅ 2026-08-12）` | 352 | 0 | P1 | 死按钮、缺 focus | 4 处死按钮（canvas / loci-marker / loci-item / drawer-mask）转 `role=button`，含子元素者加 `.self` 防冒泡；16 个交互元素补 focus ring |
| `views/WorkbenchNotes.vue（✅ 2026-08-12）` | 101 | 0 | P1 | 死按钮、缺 focus | 1 处死按钮（note-card）转 `role=button`；6 个交互元素补 focus ring；`@click.stop` 冒泡屏障不转按钮 |
| `views/WorkbenchStory.vue（✅ 2026-08-12）` | 360 | 0 | P1 | 死按钮、缺 focus | 1 处死按钮（story-card）转 `role=button`；7 个交互元素补 focus ring |
| `views/WorkbenchStoryEdit.vue（✅ 2026-08-12）` | 142 | 0 | P1 | 缺 focus | 无死按钮；9 个交互元素（按钮/表单控件）补 focus ring |
| `views/WorkbenchNoteEdit.vue（✅ 2026-08-12）` | 911 | 0 | P1 | 缺 focus | 无死按钮；19 个交互元素（按钮/工具栏/表单控件）补 focus ring |

#### 6.3.5 管理后台（admin/*，✅ 2026-08-13 全量 16 文件）
> 2026-08-13 改造说明：原审计仅登记 8 个文件，实测 `admin/` 共 16 个视图文件，其余 8 个（KnowledgeMgmt / QuizMgmt / CategoryMgmt / FlashcardMgmt / ChatConfig / UserManagement / ChapterEdit / DatabaseSettings）为漏登项，本轮一并补齐。全部 16 个文件已改造完成（死按钮转可键盘访问 + 交互元素补 `focus-visible` 三态 + 清除 `transition-all` 反模式）。构建验证：`npm run build` 仍 171 处预存 `<script>` 错误，stash 回退对比确认本批改造**零新增错误**。

| 文件 | 行数 | div死按钮 | 优先级 | 改造结果 |
|---|---|---|---|---|
| `views/admin/DocManagement.vue` | 2076 | 4 | P0 | ✅ 4 死按钮转 role=button；38 交互元素补 focus 三态 |
| `views/admin/Overview.vue` | 1009 | 0 | P1 | ✅ 7 交互元素补 focus 三态 + aria-hidden |
| `views/admin/FileMgmt.vue` | 809 | 1 | P1 | ✅ 1 死按钮 + 16 交互元素 focus 三态 |
| `views/admin/IconManagement.vue` | 1054 | 2 | P1 | ✅ 2 死按钮 + 16 交互元素 focus 三态 |
| `views/admin/TagManagement.vue` | 683 | 2 | P1 | ✅ 2 死按钮 + 12 交互元素 focus 三态 |
| `views/admin/LearningPathMgmt.vue` | 1134 | 1 | P2 | ✅ 1 死按钮 + 33 交互元素 focus 三态；清 1 transition-all |
| `views/admin/CommunityManage.vue` | 545 | 0 | P2 | ✅ 12 交互元素 focus 三态规范化 |
| `views/admin/CodeQuestionMgmt.vue` | 702 | 1 | P2 | ✅ 1 死按钮(遮罩) + 27 交互元素 focus 三态 |
| `views/admin/KnowledgeMgmt.vue` | 1240 | 7 | P1 | ✅ 7 死按钮 + 32 交互元素 focus 三态；清 9 transition-all |
| `views/admin/QuizMgmt.vue` | 1060 | 2 | P1 | ✅ 2 死按钮(遮罩) + 36 交互元素 focus 三态；清 3 transition-all |
| `views/admin/CategoryMgmt.vue` | 767 | 2 | P1 | ✅ 2 死按钮(遮罩) + 12 交互元素 focus 三态；清 3 transition-all |
| `views/admin/FlashcardMgmt.vue` | 531 | 1 | P1 | ✅ 1 死按钮 + 15 交互元素 focus 三态；清 1 transition-all |
| `views/admin/ChatConfig.vue` | 471 | 0 | P1 | ✅ 14 交互元素 focus 三态；清 2 transition-all |
| `views/admin/UserManagement.vue` | 437 | 0 | P2 | ✅ 10 交互元素 focus 三态规范化 |
| `views/admin/ChapterEdit.vue` | 872 | 0 | P2 | ✅ 9 交互元素 focus 三态规范化 |
| `views/admin/DatabaseSettings.vue` | 557 | 0 | P2 | ✅ 9 交互元素 focus 三态规范化 |

#### 6.3.6 账户 / 杂项 —— 全部 ✅ 2026-08-14 已全部合规
> 2026-08-14 改造说明：9 个文件全部完成（死按钮转可键盘访问 + 交互元素补 `focus-visible` 三态 + 清除 `transition-all`）。共 4 处死按钮转换（Favorites×1 / ImportTemplates×2 / Notifications×1）、约 60 处 focus 三态补齐；`Mistakes` 清 1 处 `transition-all`。构建验证：`npm run build` 仍 171 处预存 `<script>` 错误，本批**零新增**。

| 文件 | 行数 | div | foc缺失 | 优先级 | 主要问题 |
|---|---|---|---|---|---|
| `views/Favorites.vue` | 724 | 1 | ✅ | P0 | 死按钮、缺 focus |
| `views/Mistakes.vue` | 392 | 0 | ✅ | P0 | `transition-all=1`、缺 focus |
| `views/ImportTemplates.vue` | 795 | 2 | ✅ | P1 | 死按钮×2、缺 focus |
| `views/Notifications.vue` | 466 | 0 | ✅ | P1 | 缺 focus |
| `views/FocusMode.vue` | 861 | 1 | ✅ | P1 | 死按钮、缺 focus |
| `views/KBTitle.vue` | 678 | 0 | ✅ | P2 | `sh=31` 已较合规，低优先 |
| `views/NotFound.vue` | 255 | 0 | ✅ | P2 | 低频 |
| `views/Redirect.vue` | 69 | 0 | ✅ | P2 | 低频 |
| `views/OAuthCallback.vue` | 184 | 0 | ✅ | P2 | 低频 |

#### 6.3.7 智能体 / 沉浸式 / 布局组件 —— 全部 ✅ 2026-08-14 已全部合规（实测 23 文件）
> 2026-08-14 改造说明：原审计仅按清单登记约 16 个；实测 `components/immersive/` 共 15 个视图、`components/agent/` 4 个、`components/layout/BTopbar.vue` 1 个、根级 `ShareCard.vue` / `Learning/DagGraph.vue` / `WhiteNoisePlayer.vue` 各 1 个，合计 23 个文件，本轮全部覆盖。共 8 处死按钮转换（BTopbar×1 / BuddyMode×1 / DeepMode×1 / GraphSidebar×3 / SpacedMode×2）、约 90+ 处 focus 三态补齐；纯模态遮罩（`@click.stop`/`@click.self` 屏障）按规则保留不转按钮。`AchievementToast` / `DualRingProgress` 经核查无交互元素、已合规，无需改动。构建验证：`npm run build` 仍 171 处预存 `<script>` 错误，本批**零新增**。

| 文件 | 行数 | div | foc缺失 | 优先级 | 主要问题 |
|---|---|---|---|---|---|
| `components/layout/BTopbar.vue` | 257 | 1 | ✅ | P1 | 死按钮、缺 focus（同为导航栏，应补齐三态） |
| `components/agent/AgentWorkflowPanel.vue` | 347 | 1 | ✅ | P1 | 死按钮、缺 focus |
| `components/agent/AgentToolConfirm.vue` | — | 0 | ✅ | P2 | 偏展示，待抽空核查 |
| `components/agent/AgentToolPanel.vue` | — | 0 | ✅ | P2 | 偏展示，待抽空核查 |
| `components/agent/AgentCallChain.vue` | — | 0 | ✅ | P2 | 偏展示，待抽空核查 |
| `components/immersive/*`（BuddyMode / DeepMode / FlowMode / InsightsPanel / LeaderboardPanel / MiniChatCopilot / PomodoroMode / SettingsPanel / ShareCard / SpacedMode / ThemePanel / GraphSidebar / BreakGuide / DualRingProgress） | 260–1203 | 0–1 | ✅ | P1 | 多为 `foc=0`，代入公式补齐 hover/active/focus 即可 |
| `components/ShareCard.vue` | 523 | 0 | ✅ | P2 | 偏展示 |
| `components/Learning/DagGraph.vue` | 778 | 0 | ✅ | P2 | 图谱组件，待核查 |
| `components/WhiteNoisePlayer.vue` | 531 | 0 | ✅ | P2 | 播放器，待核查 |

#### 6.3.8 纯展示组件（不单列，随页面改造自然合规）
`Card`、`Avatar`、`Progress`、`SkeletonList`、`EmptyState`、`PageHeader`、`Celebration`、`Pagination`、`CategoryTreeSelect`、`MermaidDiagram`、`FileTreeNode`、`PreviewTreeNode`、`GlobalBackground` 等——本身无可交互元素或仅为容器，页面合规后即随之合规。

### 6.4 执行节奏建议

1. **每轮 2–4 个文件**，优先 P0（§6.3.1 知识库 + §6.3.4 工作台高偏离项 + §6.3.5 的 DocManagement）。
2. **并行子代理**逐文件改造（只改 `<template>` + `<style scoped>`），统一收口后跑一次 `npm run build` 验证不引入新错误。
3. **每轮结束本地 `git commit`**（按项目「大改造自动本地提交、不推送」规则）；**push 逐次确认**。
4. **文档同步**：每完成一轮，在本文档对应行标注 ✅ 并追加日期；同步更新 `前端待修复问题清单.md` 的「下一步建议」。

### 6.5 验证方式

```bash
# 本地预览（Vite 不做类型检查，直接看效果）
cd frontend && SERVER__PORT=8080 npx vite --host --port 5173
# 打开 http://localhost:5173/

# 编译验证（注意：全项目仍有 171 处预存 TS 错误，与本改造无关；
# 本改造硬性要求是「不引入任何新错误」）
cd frontend && npm run build
```

### 6.6 关联文档
- `前端待修复问题清单.md` —— 2026-07-29 一轮的详细修复记录
- `消息功能技术方案.md` —— IM 模块权威文档（Messages.vue 已在 8/9 一轮合规）
- `frontend/src/style.css` —— 设计系统唯一令牌来源（`--kb-*` + 共享类）

---

## 附录 A：已实现功能模块清单（共 29+ 项）

### A.1 智能学习门户（首页 + 知识库 + 学习路径）

| 功能 | 路由 | 页面 | 说明 |
|------|------|------|------|
| **首页** | `/` | Home.vue | Hero Banner（沉浸工作台入口）+ AI 学习建议卡片（动态加载/支持 regenerate）+ 学习快捷入口 |
| **知识库首页** | `/knowledge` | KnowledgeHome.vue | 知识库列表 + 分类导航 + 成员管理（OWNER/EDITOR/READER 角色） |
| **文档上传** | `/knowledge/upload` | KnowledgeUpload.vue | 拖拽上传 + Apache Tika 解析（PDF/DOC/DOCX/PPT/TXT/MD，50MB 上限，60s 超时，50 万字符截断） |
| **文档详情** | `/doc/:id` | DocDetail.vue | Markdown 渲染（含 breaks: true 换行支持）+ 代码块高亮复制 + TOC 锚点（heading-${seq}）+ 阅读进度追踪 + 收藏 |
| **知识图谱** | `/learning/knowledge-graph` | KnowledgeGraph.vue | 4 个 Tab：分类图谱 / 文档图谱 / 概念图解（AI 生成，含重新生成按钮，按 user+concept 缓存）/ **知识实体图**（AI 从文档抽取 kg_entity/kg_relation，按实体类型着色、关系连线） |
| **学习路径列表** | `/learning/paths` | LearningPaths.vue | 路径浏览 + **AI 个性化生成**（goal+level+dailyMinutes 3 输入，生成后缓存，支持 regenerate）+ 采用/删除 |
| **路径详情** | `/learning/path/:id` | PathDetail.vue | 章节列表 + **DAG 依赖图可视化**（dagre 分层布局 + 自绘 SVG，拖拽/缩放/悬停高亮/全屏，按完成/可学/锁定三态着色） + 报名入口 |
| **章节学习** | `/learning/chapter/:id` | ChapterLearn.vue | Markdown 正文 + **互动讲义**（```js-run / ```quiz-run 约定语法，代码对接后端多语言沙箱 Py/Java/C++/JS，测验支持单选/多选/填空/拖拽 4 题型，即时判分）+ 嵌入视频（`[video](url)` iframe 渲染 + 进度上报）+ 右侧相关概念推荐 + 内嵌测验 |

### A.2 沉浸式学习助手（AI 对话 + 伴学）

| 功能 | 路由 | 页面 | 说明 |
|------|------|------|------|
| **AI 对话** | `/chat` | Chat.vue | ChatGPT/Claude 风格：左侧会话列表 + 中间对话流（Markdown 渲染 + 代码块 + Mermaid 图）+ 右侧提问目录 + 左下角 AI 设置入口（11 个提供商自配 Key）+ 多模态图片输入（vision 模型）+ 语音输入（Web Speech API） |
| **代码 Agent** | `/coding/agent` | CodeAgent.vue | 编程专用 AI 助手：Agent 工具调用链可视化 + 工具确认面板 |
| **智能出题** | `/learning/quiz` | SmartQuiz.vue | 基于闪卡 AI 生成选择题/多选题/判断题/填空题/简答题，自动判分 + 简答题 AI 评分（gradeEssay 大模型批改 + 降级宽松匹配） |

### A.3 实战演练场（代码 + 挑战）

| 功能 | 路由 | 页面 | 说明 |
|------|------|------|------|
| **代码练习列表** | `/learning/code-practice` | CodePractice.vue | 代码题分类浏览 + 难度筛选 |
| **代码 Playground** | `/learning/code-practice/:id` | CodePlayground.vue | 全屏 IDE：Monaco 编辑器 + **真实多语言运行沙箱**（后端进程直执行 Py/Java/JS/C++，支持 stdin/超时强杀）+ 可重置用户工作区（多文件项目）+ 调试 Tab（Python AST 插桩逐行追踪）+ AI 编程助手内联面板（运行报错一键 AI 解释）+ 能力评估 Tab（动态测试用例 + 静态检查 + AI 能力报告）+ 代码异常一键归集错题本 |
| **编程挑战** | `/challenge` | Challenge.vue | 赛道浏览（10 关闯关）+ 积分/星级/解锁状态 + 排行榜（真实后端） |
| **挑战闯关** | `/challenge/:id` | ChallengePlay.vue | 逐关解锁 + 浏览器判题 + 实时积分 |

### A.4 学习成果度量衡

| 功能 | 路由 | 页面 | 说明 |
|------|------|------|------|
| **学习中心（总览）** | `/learning/center` | LearningReport.vue | 学习热力图（120 天活动）+ 掌握分布看板（知识库维度）+ 分类掌握度雷达图 + 薄弱点识别（<60% 自动 weak 标记）+ 统计概览（学习时长/文档数/路径数）+ 宠物卡片（喂食/玩耍/专注获经验，等级升级 exp≥maxExp→level+1+maxExp×1.5）+ 分享进度按钮（Canvas 绘制分享卡片下载） |
| **学习报告（独立页）** | `/learning/report` | LearningReportView.vue | 详细学习数据 + 周/月维度切换 |
| **周报自动生成** | `/weekly-report` | WeeklyReport.vue | **AI 自动生成周报**：summary + achievements + suggestions 注入真实统计数据，最近 8 周列表，支持手动 regenerate，降级模板拼接 |
| **错题本** | `/mistakes` | Mistakes.vue | 错题列表 + 筛选（已掌握/未掌握）+ 标记掌握 + 按错误类型归类（含代码异常自动归集）+ 关联知识库文档 |
| **闪卡大厅** | `/learning/flashcards` | FlashCardsHub.vue | 闪卡创建 + SM-2 间隔重复复习算法 + 闪卡掌握度追踪 |
| **数字证书** | `/certificate/:id` | Certificate.vue | 路径完成自动颁发（唯一验证码 KC-xxx）+ Canvas 生成证书图下载 + 匿名 verify 核验接口 |
| **成就系统** | `/achievements` | Achievement.vue | 成就列表（已解锁/未解锁）+ 进度条 + 分类筛选（学习/社交/特殊）+ 解锁时间线 + 自动解锁触发（阅读/路径完成/签到等事件） |

### A.5 社区与激励体系

| 功能 | 路由 | 页面 | 说明 |
|------|------|------|------|
| **每日打卡** | `/check-in` | CheckIn.vue | 签到 + 签到日历（当月格子热力）+ 连续天数奖励 EXP/Energy |
| **社区广场** | `/community` | Community.vue | 帖子列表（分类筛选）+ 发布入口 + 点赞/评论/收藏 |
| **帖子详情** | `/community/post/:id` | PostDetail.vue | 帖子正文 + 评论树 + 评论输入（@提及）+ 点赞交互 |
| **学习小组** | `/study-group` | StudyGroup.vue | 小组列表 + 创建 + 邮箱邀请 + 成员角色（MEMBER/ADMIN）+ WebSocket 实时群聊 |
| **私信中心** | `/messages` | Messages.vue | 会话列表 + 实时消息 WebSocket + 未读计数 |
| **排行榜** | `/ranking`（嵌入） | - | 按经验/阅读/连续打卡多维度，支持周/月/全部周期 |

### A.6 专注与沉浸模式

| 功能 | 路由 | 页面 | 说明 |
|------|------|------|------|
| **沉浸工作台** | `/learning/focus` | FocusMode.vue | **全屏深色主题（路由 meta.layout='none'）**：SVG 进度环 + 番茄钟整合 + 白噪音播放器（Web Audio 程序化生成 rain/cafe/wave）+ 今日任务列表 + 激励文案 + 5 种专注模式切换（Flow/Deep/Pomodoro/Spaced/Buddy）+ 进入自动切深色、退出恢复 |
| **学习模式（旧）** | `/learning/mode` | LearningMode.vue | 沉浸式学习壳（侧边栏可收起） |
| **番茄钟** | `/learning/pomodoro` | LearningCenter.vue | 独立番茄钟页面 + 白噪音悬浮播放器 |
| **白噪音组件** | - | WhiteNoisePlayer.vue | 三种环境音（rain/cafe/wave）+ 音量调节 + 最小化悬浮，挂载于番茄钟和 FocusMode |
| **学习提醒** | `/settings/prefs`（嵌入） | - | PWA Service Worker + Notification API 定时提醒 + 设置面板（LearningPrefs.vue） |

### A.7 学习工作台（输入→整理→复习→输出闭环）

| 功能 | 路由 | 页面 | 说明 |
|------|------|------|------|
| **工作台总览** | `/workbench` | Workbench.vue | 四模块入口：Capture（抓取）→ Notes（笔记）→ Review（复习）→ Palace（记忆宫殿）+ Recall（主动回忆）+ Story（联想故事） |
| **抓取** | `/workbench/capture` | WorkbenchCapture.vue | 碎片知识快速录入（文本/链接/图片） |
| **笔记** | `/workbench/notes` | WorkbenchNotes.vue | 笔记列表 + 新建/编辑 |
| **记忆宫殿** | `/workbench/palace` | WorkbenchPalace.vue | 宫殿列表（地点法）+ 编辑宫殿 + 编辑 loci（位置点） |
| **主动回忆** | `/workbench/recall` | WorkbenchRecall.vue | 回忆练习会话 + 遗忘曲线统计 |
| **联想故事** | `/workbench/story` | WorkbenchStory.vue | 故事法记忆 + 编辑故事 |

### A.8 个人空间

| 功能 | 路由 | 页面 | 说明 |
|------|------|------|------|
| **任务中心** | `/tasks` | TaskCenter.vue | 新工作台首页（替代 UnifiedPortal）+ 今日/待办/已完成 + 沉浸工作台 Banner 入口 |
| **个人中心** | `/profile` | Profile.vue | 等级经验条（level/exp/energy/streak_days）+ 资料编辑 + AI 设置（11 个提供商 Key）+ 学习偏好设置 |
| **我的收藏** | `/favorites` | Favorites.vue | 文档/帖子收藏列表 |
| **通知中心** | `/notifications` | Notifications.vue | 系统/学习/社区三类通知 + 标记已读 |

### A.9 管理后台（B 端）

| 功能 | 路由 | 说明 |
|------|------|------|
| 总览 | `/admin/overview` | 用户增长曲线 + 健康度指标 + 最近活动流 |
| 知识库管理 | `/admin/knowledge` | 知识库 CRUD + 成员管理 |
| 分类管理 | `/admin/categories` | 分类树（分类/子分类） |
| 文档管理 | `/admin/docs` | 文档列表 + 上传 + 新建/编辑（Markdown 编辑器） |
| 标签管理 | `/admin/tags` | 标签 CRUD |
| 闪卡管理 | `/admin/flashcards` | 闪卡 CRUD + AI 生成 |
| 学习路径管理 | `/admin/learning-paths` | 路径 CRUD + 章节管理 + **AI 生成路径/章节**（主题+知识库文档自动生成）+ 自动推断 DAG 依赖 |
| 章节编辑 | `/admin/learning/chapters/:id/edit` | Markdown 编辑器 + 前置章节选择 |
| 代码题管理 | `/admin/code-questions` | 代码题 CRUD（题面/测试用例/模板代码） |
| 题库管理 | `/admin/quiz` | 题目 CRUD + AI 出题 |
| 用户管理 | `/admin/users` | 用户列表 + 角色管理 |
| 数据库设置 | `/admin/database` | H2/MySQL 切换 + 数据导入导出 |
| 聊天配置 | `/admin/chat-config` | AI 模型全局配置 |
| 图标管理 | `/admin/icons` | 图标库管理 |
| 文件管理 | `/admin/files` | 上传文件列表 |
| 社区管理 | `/admin/community` | 帖子/评论审核 |
| 写作管理 | `/admin/writing` | AI 写作工具 |

---

## 附录 B：待开发功能（按优先级排序）

### B.1 P3 — 低优先级（锦上添花，未实现项）

| ID | 功能 | 模块 | 设计要点 |
|----|------|------|----------|
| **L-ADAPT-01** | 内容难度分级 | 学习门户 | 章节内容增加「基础/进阶/挑战」标签；根据用户掌握度（`CategoryMasteryVO`）自动展示对应难度内容；难度切换按钮放在章节学习页顶部 |
| **L-ADAPT-02** | 低正确率推荐复习 | 学习门户 | 答题正确率 < 阈值时，弹出浮动卡片推荐复习闪卡或前置章节；支持「一键跳转复习」 |
| **L-ADAPT-03** | 章节末尾补充练习 | 学习门户 | 章节末尾根据用户掌握情况，AI 动态生成 3-5 道补充练习题；复用 ` ```quiz-run ` 互动语法 |
| **SC1-GRAPH-01** | **技术栈依赖图谱** | 学习门户 | 复用 `KnowledgeGraph.vue` SVG 外壳；新增第 5 个 Tab「技术栈图」；节点类型：语言/框架/库/数据库/算法；关系类型：依赖/扩展/替代/包含；点击节点显示说明 + 推荐学习路径（与 learning_path 关联） |
| **SC1-GRAPH-03** | **概念可视化图解** | 学习门户 | 独立组件 `ConceptDiagram.vue`；用 SVG/Mermaid 图解讲解编程基础概念（变量作用域、条件判断流程、数组遍历、面向对象继承链、数据结构可视化）；AI 自动生成图解结构（节点+连线+解说文字）；嵌入到章节学习页正文的 ` ```diagram-run ` 代码块 |
| **G-COMP-01** | 竞赛管理（管理端） | 社区激励 | 管理后台新增「竞赛管理」页：创建竞赛（名称/描述/开始结束时间/题目集合/排名规则/奖励 EXP）；题目从 quiz_question 和 code_question 中选取；状态：草稿/进行中/已结束 |
| **G-COMP-02** | 竞赛答题 + 实时排名 | 社区激励 | C 端新增 `/competition/:id` 页面：倒计时 + 题目列表（支持选择题/代码题混排）+ 自动保存草稿 + 实时排行榜（WebSocket 推送排名变化，Top10 头像+姓名+得分+答题进度） |
| **G-COMP-03** | 竞赛成绩公示 + 奖励 | 社区激励 | 竞赛结束页：成绩榜（排名/用户/得分/用时）+ 奖励发放动画（EXP + 徽章）+ 获奖证书（复用 Certificate 模板变体） |
| **G-WORK-01** | 作品展示页 | 社区激励 | 新增「作品集」Tab 到社区页或独立 `/works` 路由；作品卡片：项目封面截图 + 标题 + 描述 + 关联学习路径标签 + 作者信息；发布表单：上传多图（最多 6 张）+ 富文本描述 + 关联学习路径选择 + 技术栈标签；作品集详情页：图片轮播 + 项目介绍 + 技术栈 + 关联学习章节链接 |

### B.2 P4 — 远期规划

| ID | 功能 | 模块 | 设计要点 |
|----|------|------|----------|
| **L-FORM-03** | 互动黑板 | 学习门户 | 全屏画板（Canvas/SVG）：支持手写（触屏/鼠标）+ 插入 Markdown 文本块 + 插入代码块 + 图形（矩形/箭头/流程图形状）；AI 实时批注：选中内容后右键「AI 解释/补充/纠错」，AI 批注以不同颜色便签贴在黑板上；支持保存/加载/分享黑板快照 |
| **A-CHAT-02** | 多智能体讨论 | 学习助手 | Chat.vue 升级：支持创建「多角色对话」，添加多个 AI Agent（如「导师」严谨派、「同学」活泼派、「考官」出题派）；讨论视图：消息气泡按 Agent 头像和颜色区分；「圆桌模式」：用户抛题后，各 Agent 依次发言并互相引用/反驳，最后由主持人 Agent 总结 |
| **P-ML-01** | 模型中心 | 实战演练场 | 独立 `/models` 页面：卡片式展示 AI 模型列表（文本生成/图片生成/语音/Embedding）；每个模型卡片：名称/厂商/参数大小/上下文窗口/示例调用代码 + 「在线体验」按钮（跳转 Playground）；在线体验页：模型参数面板（temperature/top_p/max_tokens）+ 输入输出测试区 + 流式输出 |
| **P-ML-02** | 数据标注中心 | 实战演练场 | 独立 `/annotation` 页面：标注任务列表（文本分类/NER 命名实体识别/情感分析）；标注工作台：左侧原文 + 右侧标注面板（分类标签选择/实体高亮选择）；快捷键支持（1/2/3 快速选标签，Ctrl+Enter 提交下一条）；标注进度统计（已标/未标/一致性检查） |
| **P-ML-03** | 模型训练任务 | 实战演练场 | 独立 `/training` 页面：创建训练任务（选择数据集/基础模型/超参数 batch_size/epochs/learning_rate）；训练日志实时流（WebSocket 推送 epoch/loss/accuracy 曲线图表）；训练指标面板：loss 曲线 + accuracy 曲线 + 混淆矩阵可视化；训练完成自动生成评估报告 + 模型下载/部署入口 |
| **G-CERT-02** | 证书验证页 | 社区激励 | 独立 `/certificate/verify` 页面（匿名可访问）：输入框输入证书验证码（KC-xxx 格式）+ 验证按钮；验证结果：证书图片 + 颁发对象 + 颁发日期 + 完成学习路径名称 + 证书哈希校验通过标识 |

---

## 附录 C：Vibecoding 开发流程提示词模板

### 模板 A：新建完整页面（复制后按需修改）

```
【任务】创建 KnowFlow 项目的新页面：<页面名称>
【路由】/xxx/xxx  → meta.layout: 'c|b|none'，requiresAuth: true|false

【设计规范——严格遵守】
1. 颜色：所有颜色使用 CSS 变量 --kb-*，禁止写色值。主色 #3B6FE0 (--kb-primary)，高光暖橘 #FF6B35 (--kb-highlight)
2. 字体：H1/H2/H3 使用衬线 Noto Serif SC（kb-h1/kb-h2/kb-h3 工具类），正文使用 Noto Sans SC（kb-body），代码使用 JetBrains Mono
3. 组件：必须复用 @/components/ui/ 下的 Icon/Button/Card/Badge/PageHeader/EmptyState/SkeletonList/Pagination 组件，禁止手写
4. 表单：select 加 .kb-select 类，筛选按钮用 .kb-filter-btn + .kb-filter-group，自定义下拉用 .kb-dropdown
5. 交互反馈：loading 态用 SkeletonList，空态用 EmptyState，错误 catch(e: unknown) + getApiError(e) + notify
6. 对话框：严禁 window.alert/confirm/prompt，统一用 notify/confirmDialog/promptDialog（@/utils/toast）
7. 动效：入场列表加 .reveal-stagger + .reveal-item，高光时刻用 highlight-badge + particle-burst

【功能需求】
<详细列出功能点：>
1. xxx
2. xxx

【API 接口参考】
- 列表：GET /api/xxx → 返回 { list: XxxVO[], total: number }
- 详情：GET /api/xxx/:id → XxxVO
- 其他接口...

【布局参考】
- 顶栏：参考 @/components/layout/Topbar.vue（搜索框 + 主题切换 + 通知 + 用户菜单）
- 内容容器：<div class="kb-region-content min-h-screen p-4 lg:p-8">
- 内容宽度：max-w-7xl mx-auto
- 卡片间距：gap-6 网格

【参考已实现页面】
- 同类参考：<已有的类似页面路径，如 @/views/LearningReport.vue>
- 组件参考：<类似组件路径>

【产出要求】
1. 在 frontend/src/views/ 下创建 xxx.vue
2. 如果需要，在 frontend/src/api/ 下添加 api 方法
3. 在 router/index.ts 中注册路由
4. 代码严格遵守 TypeScript + Composition API + <script setup lang="ts"> 风格
```

### 模板 B：修改现有页面 UI

```
【任务】优化 <页面路径> 的 UI 设计
【改动要点】
1. <具体改动点1，如：卡片增加 hover 动效，间距调整为 space-4>
2. <具体改动点2>
...

【严格遵守 KnowFlow 设计令牌】
- 颜色变量：--kb-primary #3B6FE0 / --kb-highlight #FF6B35 / --kb-card / --kb-border 等
- 工具类：.kb-h1/.kb-h2 衬线标题、.kb-body 正文、.highlight-badge 高光徽章
- 组件：复用 UI 组件，不造轮子
- 间距：基于 4px 网格（space-2/3/4/6/8）

【参考】
- 设计令牌：docs/前端规范/UI设计系统.md §二（设计令牌）、§五（布局约定）
- 类似页面：<参考路径>
```

### 模板 C：新增独立组件

```
【任务】新增 KnowFlow UI 组件：<组件名>
【文件位置】frontend/src/components/<目录>/<Name>.vue
【组件职责】<一句话说明>

【Props 设计】
- prop1: string - 说明
- prop2?: number - 说明

【Slots】
- default: 主内容
- header?: 头部

【Emits】
- update:modelValue (value: T)
- submit

【设计要求】
1. 严格使用 --kb-* 颜色变量
2. 尺寸使用语义化变量（--kb-icon-md / --kb-fs-body-md 等）
3. 圆角使用 --kb-radius-md（10px）作为默认
4. 支持深色模式（data-theme="dark"，所有色值基于 --kb-* 自动响应）
5. loading 态 + disabled 态 + hover 态 + focus 可见环
6. 无任何硬编码色值或像素魔法数字

【参考组件】
- @/components/ui/Button.vue
- @/components/ui/Card.vue
```

---

> **最后更新**：2026-08-28（v2.0 合并版）。新增功能或界面改造请同步更新本文档，确保单一真实来源始终与项目现状一致。
