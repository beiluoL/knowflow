# knowflow · AI 知识库与学习平台

一个面向学习场景的**知识库 + AI 学习平台**：用户可浏览 / 搜索 / 上传文档，借助 AI 对话答疑，并沿着系统化的学习路径（章节 + 闪卡 + 测验 + 写作 + 代码练习）持续学习，辅以等级、经验、能量、连续打卡等游戏化激励；管理员可在后台统一管理用户、文档、知识库与 AI 配置。

> 仓库名 `knowflow`（knowledge + flow）。代码中包名、表名等沿用 `knowflow` 前缀，与仓库名一致。

## 功能特性

- **用户与鉴权**：注册 / 登录（JWT + 登出令牌黑名单）、GitHub/微信 OAuth 第三方登录、个人资料；成长体系含等级、经验、能量、连续打卡天数（均基于真实数据聚合）。
- **文档与知识库**：文档浏览 / 详情 / 多级分类 / 全文搜索 / 上传；收藏与阅读进度（百分比 / 时长 / 上次阅读时间）；文档支持 **AI 生成摘要** 与 **AI 一键生成闪卡**（落库可复习）。
- **知识库协作**：知识库支持 **OWNER / EDITOR / READER 三级成员权限**、搜索邀请已注册用户或邮箱邀请；支持批量导入文档（.zip/.md/.txt/.json）与导出知识库为 ZIP。
- **Obsidian 目录一键导入（四合一）**：在「导入知识库」页点「目录一键四模块导入」，可输入本地目录绝对/相对路径或选择文件夹，系统自动扫描 Markdown 结构，按所选模块（知识库 / 学习路径 / 闪卡 / 题库，支持单选或全选）一键生成四个模块；自动将 Obsidian `![[图片]]` 引用重写为可渲染路径并迁移图片，确保课程章节中图片正确显示。内容提炼为离线规则模板，不依赖 AI 服务。
- **AI 对话**：多轮会话管理、RAG 检索增强（回答附带文档引用可溯源）、**多模型切换**；Markdown 渲染（表格/代码块/引用块）。
- **用户自配大模型**：用户可在 AI 设置中输入自己的 API Key（支持 DeepSeek/硅基流动/OpenAI/通义千问/阿里云百炼/智谱AI/月之暗面/字节豆包/腾讯混元/百度文心/Anthropic/自定义），也可使用平台订阅模型；配置持久化（`sys_user_ai_config`），Key 返回时脱敏。
- **学习中心**：学习路径与章节（可挂载文档与闪卡，完成章节幂等计数）、闪卡复习（**SM-2 间隔重复算法**）、学习任务（经验奖励 / 能量消耗）、复习计划、沉浸式学习模式、番茄钟；**AI 个性化学习路径**（按目标/水平/时长生成并持久化缓存，**自动推断章节前置依赖**，可「采用」落地为真实路径 + 章节 + 依赖关系并自动报名跟踪进度）；**学习路径章节依赖 DAG 可视化**（dagre 分层布局 + 自绘 SVG 交互式图谱，支持拖拽平移 / 滚轮缩放 / 悬停高亮链路 / 全屏查看，按完成 / 可学 / 锁定三态着色）。
- **错题本**：测验答错 / 闪卡「不认识」自动归集（同题幂等去重），支持标记掌握、分类筛选与统计（本周新增 / 今日待复习）。
- **代码练习**：内置代码题库（JavaScript/TypeScript/Python/Java/SQL），在线 Playground 做题、测试用例校验、提交/通过统计；管理端题库 CRUD 与发布/下架。
- **编程 Agent（`/coding/agent`）**：多轮编程对话（SSE 流式）、本地目录挂载与文件树浏览、代码在线运行；支持接入本地 **Ollama** 模型。本地代码生成并落盘：输入「替我实现一个番茄钟」等自然语言指令，自动调用本地 `deepseek-coder:6.7b` 生成代码，解析出文件后写入本机磁盘（基于 File System Access API，不支持的浏览器降级为下载）。意图理解与质量闭环（方案 P1~P3）：①**多轮上下文动态意图识别**——`send()` 先调后端 `POST /api/agent/intent`（`IntentService.classify`）融合近 6 轮历史与目录快照做 LLM 结构化分类（generate/modify/explain/debug/chat），取代旧的正则二分类；②**显式意图确认**——低置信度或参数缺失时后端返回 `needsClarify` + 结构化澄清问题，前端冻结输入并展示澄清卡片，用户作答后重进识别流程；③**结构/语义歧义检测**——生成后调 `POST /api/agent/ambiguities`，基于挂载目录快照做「缺文件 / 框架不匹配 / 语言冲突」等探测，产物区以高亮标签提示；④**准确率评估闭环**——生成/回答完成后调 `POST /api/agent/evaluate` 自评匹配度，回填消息「匹配度」徽标并回写 `AgentCallLog.score`（2026-08-03 补强：`evaluate` 已落库 `agent_call_log.score`/intent，支持按会话与意图复盘）；⑤**结构探针补强**——`IntentService.structuralProbe` 已补齐 `missing-file` 分支（指令修改的文件不在快照中即标记）；⑥**多轮硬指代解析**——前端为每轮消息生成 `id` 并回填 `parentId`（modify 指向最近产物），后端 `classify` 据 `parentId` 注入指代目标到 prompt，实现「再加个暗色主题」式精确指代，替代纯 LLM 软消解。体验优化：⑦**设置中预配置默认保存目录**——在「设置」抽屉里预先选好本地目录并持久化，之后一句指令即可全自动「生成 → 落盘」，全程无需弹框；未配置时首次生成弹框选择并自动记住；⑧**HTML 与独立 JS 分文件产出**——系统提示词要求前端功能（番茄钟 / 计时器 / 小游戏等）默认拆分为 `index.html` + `app.js` 两个文件；⑨**文件树自动刷新**——代码生成保存至已挂载的项目目录后，自动同步目录结构（新增 / 修改 / 删除立即可见，保留已展开目录的展开状态）；「设置」中可启用/禁用自动刷新、调节刷新延迟（合并多文件触发、避免频繁更新）、并可选开启目录持续监听（轮询式，捕捉生成之外的外部改动）；文件树头部提供手动「刷新」按钮；⑩**结构化推理过程**——对话区以「理解意图 → 澄清意图 → 确定目录 → 模型生成 → 写入文件」的步骤链实时展示每一步状态与细节，类似主流智能编程工具；全流程含环境自检（服务未启动 / 模型未安装给出 `ollama pull` 建议）与失败反馈。
- **编程 Agent 大模型适配与工具引擎（2026-08-04 P0~P1）**：①**多 Provider 统一适配层**——`AiProviderRegistry.Protocol`（OPENAI/ANTHROPIC/QIANFAN）驱动 `ModelAdapterFactory` 选择 `OpenAiAdapter`（/v1/chat/completions）、`AnthropicAdapter`（/v1/messages + x-api-key）、`QianfanAdapter`（千fan token 鉴权 + functions），屏蔽厂商差异，用户切换 provider 即换协议；`AiService` 新增 `chatMulti`/`streamMulti` 多轮 + 工具通道。②**工具调用引擎**——`AgentTool` 接口 + `ToolRegistry`（自动收集 Spring Bean、启用/授权校验、调用链落库）；内置 10 个工具：`code_run`（沙箱执行，SAFE）、`fs_read`（SAFE）、`fs_write`（WRITE）、`db_query`（DANGEROUS 默认禁用）、`fs_tree`/`fs_symbols`（项目结构理解与跨文件符号，SAFE）、`code_review`（智能审查，SAFE）、`diagnose`（错误诊断自动修复，SAFE）、`test_gen`（测试生成+沙箱验证，SAFE）、`code_complete`（行内补全，SAFE）、`api_doc`（API 文档查询，SAFE）；另有 `terminal`/`git` 两个 DANGEROUS 工具（默认禁用，需显式开启 + 二次确认）。`code_review`/`diagnose`/`test_gen` 仅做只读分析，修复落盘复用 `fs_write` 写授权。`AgentRuntimeService` 实现 ReAct 循环（模型→工具→回灌，上限 8 轮）。③**自定义工作流**——`agent_workflow` 表 + `AgentWorkflowController`（`/api/agent/workflows` CRUD）+ `AgentWorkflowService`（关键词命中→在编排入口注入 prompt 模板）；前端「工作流」标签页可配置。④**接口**——`POST /api/agent/chat`（同步 ReAct）、`/api/agent/tools`（列表/启停授权）、`/api/agent/tools/sessions/{id}/call-chain`（调用链可视化）、`/api/agent/workflows`（CRUD）；`agent_message`/`agent_session` 扩展 `message_type`/`tool_call_id`/`context_window`/`agent_mode`，新增 `agent_tool_config`/`agent_tool_call`/`agent_workflow` 三表。
- **编程 Agent 上下文管理与前端工具化（2026-08-04 P2~P4）**：①**上下文管理**——`AgentContextManager` 以「滚动窗口 + 摘要压缩」组装上下文：按 `agent_session.context_window`（默认 6000，可在会话设置中调整）从新往旧累加预估 token，超出部分达阈值即调模型压缩为 ≤400 字摘要落库（`message_type='summary'`），被覆盖的旧消息逻辑删除，长会话不再击穿模型窗口；摘要失败自动降级截断拼接，不阻断对话。②**REST 补全**——会话分页 + 标题搜索、消息**游标分页**（`beforeId`，Agent 持续追加消息时不会重复/漏读）、会话配置更新；控制器统一改抛 `BusinessException` 交由 `GlobalExceptionHandler` 收敛。③**前端**——新增「工具」标签页（工具管理面板：权限徽标 + 启停开关；调用链可视化：时间轴 + 按工具聚合统计），对话页支持「加载更早的消息」、对话流内渲染**工具执行卡片**（执行中/成功/失败三态，可展开入参与输出）与历史摘要气泡，输入区新增「Agent 工具模式」开关（开启后跳过意图路由直接走 ReAct）与「调用链」抽屉；**高危工具二次确认**——后端推送 `tool-confirm` 事件并阻塞等待（60s 超时按拒绝处理），前端弹窗展示工具与入参并倒计时，用户放行后经 `POST /api/agent/tool-confirm` 回传，未接确认通道的调用方默认一律拒绝（fail-safe）。
- **知识库工作台（学习闭环）**：新增顶部菜单「知识库工作台」（`/workbench`），实现 **知识输入 → 整理 → 复习 → 输出** 四模块闭环，融合多种高效学习方法论：①**间隔重复**——基于 **SM-2 遗忘曲线算法** 自动排程复习卡片，按用户反馈（忘了/困难/一般/容易）动态拉长间隔（`ease_factor`/`repetitions`/`interval_day` 驱动 `next_review_time` 提醒），支持卡片抽查与暂停；②**记忆宫殿**——可视化空间编辑，把知识点挂载到熟悉场景的固定位点（`pos_x/pos_y` 画布百分比坐标，前端拖拽布局），沿 `sort_order` 漫游路线回忆；③**费曼故事**——以故事/叙事形式重新表达知识（以教代学），支持假想听众（小孩/初学者/同行/面试官）、核心类比 `metaphor` 与**卡点记录 `gap_note`**（讲不通处即知识漏洞）；④**康奈尔笔记**——三栏结构（线索栏自测/笔记栏记录/总结栏复述）融入整理模块，掌握度 `mastery` 自评驱动总览。四模块通过 `wb_capture` 收集箱串联：任一知识可从采集派生笔记/复习卡/宫殿位点/故事，形成全生命周期闭环。
- **智能测验与写作**：多题型题库（单选/多选/填空/判断/简答）+ **AI 按知识库/文档出题**；智能写作含 AI 评分反馈、实时预览、导出 PDF / Markdown。
- **数据可视化**：学习热力图（GitHub 式按日活跃度）、掌握分布看板（闪卡/错题掌握度）、**知识图谱**（分类/文档层级图、技术栈依赖图、概念图解、**AI 抽取的实体关系图**等多视图 SVG 可视化）。
- **社区与消息**：帖子 / 评论 / 点赞（幂等切换）、精华帖；消息通知中心（未读数 / 单条已读 / 一键全部已读）；即时通讯（学习小组群聊 + 单聊私信，含 @提及 / 撤回 / 已读游标，详见 `doc/技术方案/消息功能技术方案.md`）。
- **管理后台**：概览看板（真实统计：用户增长/健康度/活动流）、用户管理、文档管理（发布/草稿/废弃、批量删除/移动）、知识库与成员管理、学习路径与章节管理（**AI 生成路径/章节内容**）、闪卡管理（**AI 批量生成**）、代码题库、测验题库（**AI 出题**）、自定义图标（SVG 上传）、对话配置、社区管理。

## 技术栈

| 层   | 技术                                                                          |
| --- | --------------------------------------------------------------------------- |
| 前端  | Vue 3 + TypeScript + Vite + Pinia + Vue Router + Axios + TailwindCSS        |
| 后端  | Spring Boot 3.2 + MyBatis-Plus + Spring Security + JWT + SpringDoc（Swagger） |
| 数据库 | H2（开发测试）/ MySQL（生产），配置化切换 + 后台运行时热切换，双方言脚本自动适配             |
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
|  | 学习路径列表（**列表内一键报名**，报名后原地切换为「开始学习」，已学习显示进度条+「学习中」） | `/learning/paths` |
|  | 路径详情（显式报名 / 报名后解锁章节 / 环形进度 / DAG 图谱 / 证书入口） | `/learning/path/:id` |
|  | 章节学习（Markdown 讲义 / 嵌入视频 / 内嵌可运行代码 / 即时测验 / 相关文档推荐 / 底部栏完成按钮 / **完成全部章节弹出证书庆祝弹窗**） | `/learning/chapter/:id` |
|  | 代码练习列表 | `/learning/code-practice` |
|  | 代码 Playground（在线做题） | `/learning/code-practice/:id` |
|  | 编程 Agent（对话 / 本地目录 / 代码运行 / **本地生成代码并保存到指定目录**） | `/coding/agent` |
|  | 学习闪卡 | `/learning/flashcards` |
|  | 知识图谱 | `/learning/knowledge-graph` |
|  | 掌握分布看板（闪卡/错题掌握度 + 分类薄弱项） | `/learning/mastery` |
| **绘图编辑器** | 思维导图（节点/连线/折叠/自动布局） | `/mindmap` |
|  | 流程图（vue-flow：拖拽节点 / 自由连线 / 双击编辑 / 自动保存） | `/drawing` |
|  | 复习计划 | `/learning/review` |
|  | 沉浸式学习模式（无顶栏） | `/learning/mode` |
|  | 番茄钟 | `/learning/pomodoro` |
| **AI 助手** | 智能问答对话（多模型切换 / RAG 溯源 / 分享） | `/chat` |
|  | 智能出题 | `/learning/quiz` |
|  | 智能写作（AI 评分 / 预览 / 导出 PDF・MD） | `/learning/writing` |
| **个人空间** | 工作台（任务中心） | `/tasks` |
|  | 任务清单（Things3 式：智能列表 inbox/today/upcoming/someday/logbook + 项目/区域 + 子任务树） | `/task-list` |
|  | 日历（月/周/日三视图 + 24h 时间轴 + 范围查询 + 全天/定时事件 CRUD，与任务清单同源） | `/calendar` |
|  | 个人中心 | `/profile` |
|  | 收藏夹 | `/favorites` |
|  | 笔记管理 / 笔记编辑 | `/notes` `/notes/new` `/notes/:id/edit` |
|  | 错题本 | `/mistakes` |
|  | 成就 | `/achievements` |
|  | 知识库称号 | `/kb-titles` |
|  | 打卡 | `/check-in` |
|  | 我的证书 / 证书详情 | `/certificate/:id` |
|  | 消息中心 | `/notifications` |
|  | 社区讨论 | `/community` |
| **知识库工作台** | 工作台总览（四模块闭环看板） | `/workbench` |
|  | 知识输入·收集箱 | `/workbench/capture` |
|  | 知识整理·康奈尔笔记列表 / 编辑 | `/workbench/notes` `/workbench/notes/:id` |
|  | 知识复习·间隔重复（SM-2 抽查） | `/workbench/review` |
|  | 记忆宫殿列表 / 可视化编辑 | `/workbench/palace` `/workbench/palace/:id` |
|  | 知识输出·费曼故事列表 / 编辑 | `/workbench/story` `/workbench/story/:id` |
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

## 后端接口总览（50 个控制器）

统一前缀 `/api`，完整接口文档见 Swagger（`/swagger-ui.html`）。

### 面向用户（37 个，下表为主要控制器）

| 控制器 | 根路径 | 能力 |
|---|---|---|
| AuthController | `/api/auth` | 登录 / 注册 / 登出（黑名单）/ 当前用户 / GitHub・微信 OAuth |
| UserController | `/api/user` | 个人资料、学习统计 |
| AiConfigController | `/api/ai-config` | 用户自建 AI Key 配置（脱敏返回）、平台模型列表 |
| DocController | `/api/docs` | 文档列表 / 详情 / 收藏 / 进度 / 推荐 / **AI 摘要** / **AI 生成闪卡** |
| CategoryController | `/api/categories` | 分类树 |
| ChatController | `/api/chat` | 会话 CRUD、发送消息（RAG + 大模型）、**可用模型列表** |
| LearningController | `/api/learning` | 路径 / 章节 / 报名 / 闪卡 SM-2 复习 / 任务 / **热力图**（`/stats/daily-activity`）/ **掌握分布**（`/stats/mastery`）/ **分类掌握度（含薄弱项）**（`/category-mastery`）/ **章节依赖图(DAG)**（`/paths/{pathId}/dag`）/ **视频进度**（`/chapters/{id}/video-progress`）/ **数字证书**（`/certificates` 列表/详情/`verify` 验证） |
| CodeQuestionController | `/api/code-questions` | 已发布代码题列表 / 详情 / 提交统计 |
| MistakeController | `/api/mistakes` | 错题列表 / 标记掌握 / 幂等添加 / 统计 |
| CommunityController | `/api/community` | 帖子 / 评论 / 点赞（幂等切换） |
| NotificationController | `/api/notifications` | 消息列表 / 已读 / 未读数 |
| KnowledgeController | `/api/knowledge` | 知识图谱数据（分类图/技术栈图/概念图解/实体关系图；`/entity-graph` 查询、`/extract` 触发 AI 抽取） |
| DrawingController | `/api/drawings` | 绘图编辑器·流程图整图 CRUD（列表/详情/新建/更新/删除），全部需登录，user_id 维度隔离；`data` 存 vue-flow 契约 JSON（nodes/edges） |
| TaskController | `/api/tasks` | 任务 CRUD + 智能列表（inbox/today/upcoming/someday/logbook，按 scheduledDate/completed 自动归类）、按清单/父任务/完成态过滤；支持子任务（parentId 层级、递归子树）；**日历范围查询** `GET /api/tasks/range?start=&end=&status=&listId=`（按时间窗返回重叠的定时事件 + 区间内的全天事件，绝不全量扫描）；定时事件 `start_time/end_time` 字段；全部需登录，user_id 维度隔离 |
| TaskListController | `/api/task-lists` | 任务清单/项目/区域 CRUD（type: inbox/today/upcoming/someday/logbook/project/area），含任务计数；user_id 维度隔离 |
| QuizController | `/api/quiz` | 智能测验：按知识库/文档出题、提交判分、答题记录、错题同步 |
| CheckInController | `/api/checkin` | 每日打卡（签到 / 补卡 / 连续天数与奖励） |
| AchievementController | `/api/achievements` | 成就列表与解锁、用户成就进度 |
| RankController | `/api/ranking` | 积分 / 经验排行榜 |
| CodeChallengeController | `/api/challenges` | 编程挑战赛道与关卡、提交判题、排行榜、个人战绩（榜单/详情匿名可读，提交与战绩需登录） |
| WorkbenchController | `/api/workbench` | 知识库工作台四模块闭环（输入收集箱 / 整理康奈尔笔记 / 复习 SM-2 间隔重复 + 记忆宫殿 / 输出费曼故事），全部需登录，user_id 维度隔离 |
| StudyGroupController | `/api/study-groups` | 学习小组 CRUD、成员管理、群公告、邀请 |
| PrivateMessageController | `/api/im/private` | 私信会话与消息、已读游标、撤回 |
| CodeWorkspaceController | `/api/code/workspace` | 代码工作区（多文件 / 沙箱运行） |
| CodeRunController | `/api/code` | 单文件代码在线运行（沙箱执行） |
| CodeAgentController | `/api/code-agent`、`/api/agent` | 编程 Agent 对话（SSE 流式）、会话与消息持久化、使用统计；**2026-08-04 P3/P4 补全**：`POST /chat/agent-stream`（ReAct 工具流式对话，扩展 thinking/tool-start/tool-end/tool-confirm 事件）、`POST /tool-confirm`（高危工具二次确认回调）、`GET /sessions/page`（会话分页 + 关键字）、`GET /sessions/{id}/messages/page`（消息游标分页）、`PUT /sessions/{id}`（重命名 + 模型 / 项目目录 / 上下文窗口 / 运行模式） |
| CodeGenController | `/api/code-gen` | **本地代码生成**：自然语言指令 → deepseek-coder 生成可落盘文件（`/generate`）、生成前环境自检（`/health`） |
| AgentIntentController | `/api/agent` | **多轮意图识别与答案评估（方案 P1~P3）**：`POST /intent` 结构化意图分类（含上下文融合、低置信度强制澄清）、`POST /ambiguities` 结构/语义歧义检测、`POST /evaluate` 输出匹配度评分与改进建议 |
| AgentToolController | `/api/agent/tools` | **编程 Agent 工具调用引擎（2026-08-04 P1/P3）**：`GET /` 工具列表（含启用状态）、`GET /{name}` 工具详情、`PUT /{name}` 启用/禁用与写授权、`GET /sessions/{id}/call-chain` 会话级工具调用链、`GET /sessions/{id}/call-stats` 按工具聚合统计（次数/成功率/平均耗时） |
| OllamaController | `/api/ollama` | 本地 Ollama 配置、连接测试、模型列表 / 加载 / 卸载 / 删除 |
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
| ObsidianImportController | `/api/obsidian/import` | **Obsidian 目录一键导入（四合一）**：`GET /scan` 扫描本地目录预览 Markdown 结构；`POST /generate` 按所选模块（knowledge/path/flashcard/quiz 单选或全选）自动生成知识库、学习路径（按目录层级建章节）、闪卡、题库；自动将 Obsidian `![[图片]]` 语法预处理为标准 Markdown 并迁移图片到 `/uploads`，保证四模块图片正确渲染。内容提炼采用离线规则模板，不依赖 AI；生成时可指定 `flashcardTemplateId` / `quizTemplateId` 应用自定义规则模板。 |
| ImportTemplateController | `/api/import-templates` | **导入规则模板管理**：为 Obsidian 一键导入的闪卡/题库提供可自定义的规则模板。`GET` 列表（按类型/启用状态过滤，返回可见范围）、`GET /{id}` 详情、`POST` 创建、`PUT /{id}` 更新、`DELETE /{id}` 删除（预设不可删）、`POST /{id}/toggle` 启用停用、`POST /{id}/default` 设默认（同类型唯一）。模板内容为 JSON（字段结构/抽取规则/校验/展示样式/数据源绑定），预设模板由 data.sql 幂等写入，前端页面 `/import-templates` 支持创建、编辑、删除、预览、启用停用与设默认。 |

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
│   ├── src/main/resources/           # application.yml、db/h2/*.sql、db/mysql/*.sql
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
├── miniprogram/           # 微信小程序（独立子项目，见 `doc/改造与提交/审核提交说明.md`）
├── design/                # 原始设计稿（HTML）
├── doc/                   # 项目文档汇总（技术方案 / 审查报告 / 优化选型 / 产品项目 / 改造提交 / 部署接入）
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
- 数据库：默认 H2 内存库，启动时自动执行 `db/h2/schema.sql` 建表、`db/h2/data.sql` 写入初始数据
- API 文档（Swagger）：<http://localhost:8080/swagger-ui.html>
- H2 控制台：<http://localhost:8080/h2-console>（JDBC URL：`jdbc:h2:mem:knowflow`，用户名 `sa`，密码空）
- **切换 MySQL**：把 `application.yml` 中 `knowflow.datasource.type` 改为 `mysql`（或启动时加 `DB_TYPE=mysql`），
  系统会自动加载 MySQL 驱动与 `db/mysql/` 方言脚本，**无需改动任何代码**；
  也可登录后台「系统设置 → 数据库设置」在运行时热切换。详见 [DATABASE.md 第五章](./doc/部署与接入/DATABASE.md#五双数据库支持h2--mysql-切换)
- **AI 功能配置**：复制 `backend/src/main/resources/application-local.example.yml` 为 `application-local.yml`（已被 gitignore，不会提交），填入真实 `ai.api-key` / `ai.base-url` / `ai.model`（也支持环境变量 `AI_API_KEY` 等覆盖）。未配置时 AI 对话 / 摘要 / 出题等功能不可用；用户也可在前端「AI 设置」中自行填入个人 Key
- **语义检索配置（可选）**：文档检索支持「关键词 + 向量语义」混合召回。嵌入模型与对话模型通常不是同一厂商，
  需通过 `ai.embedding-base-url` / `ai.embedding-api-key` 单独配置（留空则回退 `ai.base-url` / `ai.api-key`）。
  注意 **DeepSeek 不提供 `/embeddings` 接口**，若 `ai.base-url` 指向 DeepSeek 而未单独配置嵌入地址，
  启动日志会给出 ERROR 提示，语义检索将自动降级为纯关键词检索（功能可用，仅召回能力下降）。
  可选嵌入服务：硅基流动 `https://api.siliconflow.cn/v1`（`BAAI/bge-m3`）、本地 Ollama `http://localhost:11434/v1`（`nomic-embed-text`）

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

生产环境部署（后端 jar / MySQL、前端构建 + Nginx 反代、Docker 一键部署、systemd 守护等）请见 [DEPLOY.md](./doc/部署与接入/DEPLOY.md)。

## 相关文档

- [DATABASE.md](./doc/部署与接入/DATABASE.md) — 数据库表结构（41 张表）、字段定义、索引设计（遵循《阿里巴巴 Java 开发手册》）
- [DEPLOY.md](./doc/部署与接入/DEPLOY.md) — 生产环境部署指南（jar / Nginx / Docker / systemd）
- [OAUTH.md](./doc/部署与接入/OAUTH.md) — GitHub / 微信 第三方 OAuth 登录接入指南
- [消息功能技术方案.md](./doc/技术方案/消息功能技术方案.md) — IM（学习小组群聊 / 单聊私信）权威技术文档
- [PRD-功能需求文档.md](./doc/产品与项目/PRD-功能需求文档.md) — 产品功能需求文档
- [审核提交说明.md](./doc/改造与提交/审核提交说明.md) — 微信小程序审核提交说明
- 其余技术方案 / 审查报告 / 优化选型等文档已归集到 [`doc/`](./doc/README.md) 目录

## 说明

- 根目录 `.gitignore` 已忽略 `node_modules/`、`backend/target/`、IDE 配置（`.idea/`、`.trae/`）、本地代理记忆（`.workbuddy/`）等，避免把依赖与编译产物提交到仓库。
- 前端 `README.md` 仅作子模块说明，项目整体说明以本文件为准。
