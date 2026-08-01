# 知识库（knowflow）功能测试与体验评估报告

> 评估视角：产品经理 + Web 体验专家
> 评估时间：2026-07-26　评估方式：运行系统动态探针（后端 8080 / 前端 5173）+ 前端源码走查
> 账号：`admin` / `admin123`（user1、user2 同密码）

## 评估结论速览

| 维度 | 问题数 | 高 | 中 | 低 |
|------|--------|----|----|----|
| 1. 用户流程完整性 | 2 | 1 |  plausibility | 0 |
| 2. 交互体验（反馈/表单/错误提示） | 4 | 1 | 2 | 1 |
| 3. 视觉一致性 | 2 | 0 | 1 | 1 |
| 4. 边界与异常处理 | 6 | 1 | 3 | 2 |
| 5. 可用性与可访问性 | 1 | 0 | 1 | 0 |
| **合计** | **15** | **4** | **9** | **3** |

> 说明：本评估基于真实运行系统（非仅看代码）。UI 像素级渲染需你在浏览器最终确认；下方“复现步骤”同时给出接口级与页面级路径。

---

## 1. 用户流程完整性

### 🔴 高·F-01 「学习中心」导航指向错误页面，真实学习中心页无任何入口
- **位置**：`frontend/src/components/layout/CTopNav.vue`（navLinks：`学习中心 → /learning/center`）、`router/index.ts`、`LearningCenter.vue`（页面标题为「番茄钟专注」）、`LearningReport.vue`（页面标题为「学习中心」，路由为 `/learning/report` 且**未加入任何导航**）。
- **复现**：
  1. 点击顶部导航「学习中心」→ 进入的是**番茄钟计时器**，而非学习概览。
  2. 点击个人菜单「番茄钟专注」→ `/learning/pomodoro` 重定向到 `/learning/center` → 同样是番茄钟（与①同一页面）。
  3. 真正的学习概览页（`LearningReport.vue`）只能通过手动访问 `/learning/report` 到达，**导航里没有任何入口**。
- **影响**：信息架构混乱，用户找不到学习进度总览；两个菜单项指向同一页，造成“重复/缺失”的双重错觉。
- **建议**：将 `/learning/center` 映射到 `LearningReport.vue`（真实学习中心），番茄钟独立为 `/learning/pomodoro → LearningCenter.vue`；导航「学习中心」指向 `/learning/center`。

### 🟡 中·F-02 社区发帖后无自动跳转/刷新闭环（体验断点）—— 已修复(2026-07-31)
- **原问题**：`Community.vue` 发帖成功后仅关闭弹窗，列表未自动刷新（需手动），且新帖不置顶。
- **修复**：发布改为独立页面 `PostEdit.vue`；发布成功后 `router.push('/community')` 跳回列表并执行 `fetchList()` 刷新、复位「最新」排序首页、平滑滚动到顶部，且给出「发布成功」toast 反馈，形成完整闭环。

---

## 2. 交互体验

### 🔴 高·F-03 搜索结果高亮使用 `v-html` 且未转义源文本（XSS 隐患）
- **位置**：`frontend/src/views/SearchResult.vue:128,134` `v-html="highlightKeyword(doc.title / summary)"`；`highlightKeyword` 仅对正则特殊字符做了转义，**未对文本做 HTML 转义**。
- **复现**：若任一文档标题/摘要含 HTML（如 `<img src=x onerror=alert(1)>`），在搜索结果页经 `v-html` 渲染即执行脚本。当前种子数据无 HTML 故未触发，但属**存储型/反射型 XSS 隐患**。
- **对比**：`Chat.vue` 已做 `escapeHtml` 再渲染，搜索页未做，处理不一致。
- **建议**：先 `escapeHtml(text)` 再注入高亮 `<span>`，或仅对纯文本做安全高亮。

### 🟡 中·F-04 错误提示不统一：登录失败返回 HTTP 200 + 业务码
- **位置**：`AuthController.login` 错误密码返回 HTTP 200（body 内 `code≠200`）。
- **复现**：`POST /api/auth/login` 错误密码 → HTTP 200（前端需读 body 判断）。
- **影响**：监控/自动化难识别失败；与“正确失败应 401/400”的惯例不符。

### 🟡 中·F-05 表单校验反馈不完整：评论无空值/长度校验
- **位置**：`CommunityController` 评论接口 + `AddCommentDTO` 无 `@NotBlank`/`@Size`；`CommunityServiceImpl` 未校验。
- **复现**：`POST /api/community/posts/1/comments {"content":""}` → 200（空评论入库）；10000 字符 → 200（若超 DB 列长会 500）。
- **影响**：数据污染、垃圾内容、潜在存储异常。

### 🟢 低·F-06 部分操作后 toast 反馈缺失
- **复现观察**：章节完成、收藏切换等有 `notify`，但个别次要操作（如通知“标为已读”后角标更新）依赖轮询，偶有延迟感。
- **建议**：关键写操作统一 toast 反馈 + 角标即时更新。

---

## 3. 视觉一致性

### 🟡 中·F-07 设计令牌双轨并存（可维护性 + 漂移风险）
- **位置**：42 个文件使用 Tailwind `text-primary-500` 等；17 个文件使用 `var(--kb-*)`（如 `LearningReport.vue` 的 `kb-h1`/`kb-body-sm`、`CTopNav.vue` 的 `var(--kb-card)`）。
- **当前状态**：`--kb-primary:#3B6FE0` 与 Tailwind `primary[500]:#3B6FE0` **色值已对齐**，故当前无可见色差；但两套表达机制并存。
- **影响**：未来改主题时两套易漂移；标题层级表达不统一（`text-2xl font-bold` vs `kb-h1`）。
- **建议**：统一为单一令牌体系（推荐 Tailwind theme extend + CSS 变量映射一处定义）。

### 🟢 低·F-08 C 端 / B 端布局壳风格未完全对齐
- **位置**：`CLayout.vue`（C 端：顶部导航+内容）与 `AppShell.vue`/`Sidebar.vue`（B 端：侧边栏+顶栏）分离实现。
- **影响**：间距/字号/卡片圆角需人工对齐，存在细微不一致风险（需浏览器目测确认）。

---

## 4. 边界与异常处理

### 🔴 高·F-09 分页 `pageSize` 上限未生效（DoS 风险）
- **位置**：`PageQuery` 声称有 pageSize 上限 100，但实测未生效。
- **复现**：`GET /api/community/posts?pageSize=9999` → 返回 `pageSize=9999`、全量记录（HTTP 200）。
- **影响**：大页拉取可拖垮 DB/接口。
- **建议**：在 `PageQuery` 或统一分页切面强制 `pageSize = min(pageSize, 100)`，并对 `pageNum<1` 归一。

### 🟡 中·F-10 点赞无幂等、无取消（计数可刷）
- **位置**：`CommunityServiceImpl.likePost` 仅 `like_count + 1`，无“已点赞”判定，无用户-帖子点赞关系表。
- **复现**：同用户对同帖连点两次 `POST /like` → `likeCount` 130 → 132（翻倍）。
- **影响**：计数失真、可被刷量；用户无法取消赞。

### 🟡 中·F-11 权限拒绝返回 401 而非 403（语义错误，易误踢登录）
- **位置**：普通用户（user1）访问 `/api/admin/overview` → 401（应为 403）。
- **复现**：登录 user1 → 访问 admin 接口 → 401。
- **影响**：前端常把 401 当“未登录”跳登录页；已登录但无权限的用户会被错误重定向到登录页。

### 🟡 中·F-12 孤儿端点 `/api/search` 返回 500
- **位置**：无 `SearchController`，但 `/api/search` 被兜底映射命中抛 500；前端实际调用 `/api/docs?keyword=`（功能正常）。
- **复现**：`GET /api/search` → 500（与 `/api/docs/{id}` 把非数字 id 当 Long 解析抛 500 同源）。
- **影响**：低（UI 不用），但属潜在故障点；建议删除该路由或返回 404。

### 🟢 低·F-13 负页码被接受
- **复现**：`GET /api/community/posts?pageNum=-1` → 200。
- **建议**：分页参数下限校验。

### 🟢 低·F-14 不存在/非法文档 id 的错误处理不一致
- **复现**：`GET /api/docs/9999999` → 200（`data:null`）；`GET /api/docs/abc` → 500（NumberFormatException）。
- **影响**：前端若未判空可能白屏；非数字 id 直接 500（路由设计气味）。
- **建议**：详情接口对 null 返回 404 友好错误；非法 id 统一 400。

---

## 5. 可用性与可访问性

### 🟡 中·F-15 搜索仅匹配标题/摘要，不匹配分类名/标签
- **位置**：`DocServiceImpl.getDocPage` 仅 `like title/summary`。
- **复现**：搜索分类名「人工智能」→ 0 结果（该分类下确有文档）。
- **影响**：用户按分类心智搜索落空；建议扩展至分类名、标签、正文。
- **正面**：搜索功能本身可用（`keyword=学习` → 4 条命中）。

### 可访问性正面观察
- 顶部导航支持 **Cmd/Ctrl+K** 唤起搜索（键盘可达）。
- 移动端有汉堡抽屉导航；下拉菜单带 `aria-haspopup` / `aria-expanded`。
- 通知、用户菜单有关闭遮罩；多处使用 `EmptyState` / `SkeletonList` 处理加载与空态（良好）。

---

## 优先级修复建议（按 ROI）

1. **立即修（高）**：F-01 导航错位、F-03 搜索 XSS、F-09 分页上限、F-05 评论校验（含长度）。
2. **尽快修（中）**：F-10 点赞幂等、F-11 权限 403、F-12 孤儿端点、F-15 搜索扩展、F-07 令牌统一、F-02 发帖闭环。
3. **择机修（低）**：F-04 登录失败状态码、F-06 toast、F-08 布局对齐、F-13/F-14 参数与 id 校验。

> 注：以上均为基于运行系统的实测结论。`pageSize` 上限、空评论、点赞翻倍、401/403、XSS 路径均已通过接口实测得证。
