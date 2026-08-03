# knowflow 知识库项目 — 功能模块全面审查报告

> 审查时间：2026-07-25  
> 审查范围：后端 14 个 Controller / 9 个 Service / 17 个 Entity，前端 40 个 View / 12 个 API 模块 / 路由与状态管理  
> 审查方法：主线程核读核心配置（schema.sql / SecurityConfig / router / request.ts）+ 4 个并行子代理深入各业务模块逐文件审查  
> 遵循规范：《阿里巴巴 Java 开发手册》、Airbnb 前端规范、Ant Design 设计体系

---

## 一、执行摘要

knowflow 是一个「知识管理 + 智能学习 + AI 问答」一体化平台，前端 Vue 3 + TS + Vite + Pinia，后端 Spring Boot 3 + MyBatis Plus + Spring Security + JWT，数据库 H2（目标 MySQL 8）。

**整体结论：项目骨架完整、分层规范、阿里规约执行到位（逻辑外键、三字段、逻辑删除均已落实），但存在三类系统性问题：**

1. **安全防线几乎失守**：管理员接口零鉴权、普通用户可越权提权、密码哈希泄露、JWT 密钥硬编码、前台文档写接口无鉴权——当前状态下**不可上线**。
2. **核心功能名存实亡**：AI 对话是模拟回复、闪卡复习无间隔重复、学习任务/通知/社区评论整模块未实现、大量统计字段是死字段——产品宣传与实现严重脱节。
3. **前端大量假数据与死按钮**：6+ 页面全 mock、多个按钮无 @click、Chat 假流式、401 硬跳转丢状态——用户体验存在欺骗性。

**缺陷统计：严重(P0) 34 项 / 中等(P1) 28 项 / 轻微(P2) 19 项，合计 81 项。**

---

## 二、功能模块清单矩阵

### 后端 API 端点全量清单

| 模块 | 端点 | 方法 | 鉴权要求 | 实现状态 | 主要问题 |
|------|------|------|---------|---------|---------|
| **认证** | /api/auth/login | POST | 公开 | ✅ | 用户枚举、无暴力防护 |
| | /api/auth/register | POST | 公开 | ✅ | email 不查重、密码强度弱 |
| | /api/auth/me | GET | 公开(配置错误) | ✅ | 应为 authenticated |
| | 登出 | — | — | ❌缺失 | 无 logout、无 token 吊销 |
| **用户** | /api/user/profile | GET/PUT | authenticated | ⚠️ | UpdateProfileDTO 无校验、统计字段死 |
| | /api/user/stats | GET | authenticated | ⚠️ | completedPaths/totalFlashcards 硬编码 0 |
| **文档** | /api/docs | GET | 公开 | ✅ | pageSize 无上限 |
| | /api/docs/{id} | GET | 公开 | ⚠️ | 草稿泄露、viewCount 可刷 |
| | /api/docs (POST/PUT/DELETE) | — | **无鉴权** | ⚠️ | 任意用户可增删改文档 |
| | /api/docs/{id}/favorite | POST | authenticated | ⚠️ | 无联合唯一约束、并发重复 |
| | /api/docs/progress | POST | authenticated | ⚠️ | ReadProgressDTO 无校验、readCount 可刷 |
| **分类** | /api/categories/tree | GET | 公开 | ✅ | — |
| **搜索** | (复用 /api/docs) | — | — | ⚠️ | orderBy 未实现、LIKE 查询 |
| **AI 对话** | /api/chat/conversations | GET/POST | authenticated | ⚠️ | — |
| | /api/chat/conversations/{id} | DELETE | authenticated | ✅ | — |
| | /api/chat/conversations/{id}/messages | GET | authenticated | ⚠️ | 无分页 |
| | /api/chat/send | POST | authenticated | ❌ | **模拟回复、未接大模型** |
| **学习路径** | /api/learning/paths | GET | 公开 | ⚠️ | 详情不校验 status |
| | /api/learning/paths/{id}/enroll | POST | authenticated | ⚠️ | 不校验 status、并发重复 |
| | /api/learning/chapters/{id}/complete | POST | authenticated | ❌ | **非幂等、progress 可破 100%** |
| **闪卡** | /api/learning/flashcards | GET | 公开 | ⚠️ | 泄露未发布路径内容 |
| | 复习接口 | — | — | ❌缺失 | **无 SM-2、reviewCount 死字段** |
| **学习任务** | /api/learning/tasks | GET | authenticated | ❌ | **从不创建、整模块空壳** |
| **错题** | /api/mistakes | GET/POST | authenticated | ⚠️ | 详情/标记不校验归属 |
| | 更新/删除 | — | — | ❌缺失 | 无法编辑删除 |
| | 复习接口 | — | — | ❌缺失 | reviewCount 永远 0 |
| **社区** | /api/community/posts | GET/POST | authenticated | ⚠️ | 发帖 OK 但无编辑/删除 |
| | 评论 | — | — | ❌缺失 | **comment_count 是死字段** |
| | 加精/审核 | — | — | ❌缺失 | isEssence/status 无逻辑 |
| **通知** | /api/notifications | GET | authenticated | ⚠️ | 标记不校验归属 |
| | 通知生成 | — | — | ❌缺失 | **从不 insert、永远空** |
| **管理后台** | /api/admin/** | — | authenticated(无角色校验) | ❌ | **任意登录用户可越权** |

### 前端页面清单

| 分类 | 页面 | 路由 | 数据来源 | 状态 |
|------|------|------|---------|------|
| 公共 | Home / KnowledgeHome / Categories / Docs / SearchResult / DocDetail | / | 后端 API | ✅ 真实 |
| 认证 | Login / Register / UnifiedPortal | /login /register /portal | 后端 API | ✅ 真实 |
| AI | Chat | /chat | 后端(模拟回复) | ⚠️ 假 AI |
| 学习 | LearningPaths / PathDetail / ChapterLearn | /learning/* | 后端 API | ✅ 真实 |
| | FlashCards | /learning/flashcards | 后端 API | ⚠️ 复习不持久化 |
| | ReviewPlan | /learning/review | **纯前端 mock** | ❌ 假数据 |
| | LearningCenter(番茄钟) | /learning/pomodoro | **mock** | ❌ 假数据 |
| | LearningReport(学习中心) | /learning/center | **mock** | ❌ 假数据 |
| | SmartQuiz | /learning/quiz | **mock** | ❌ 假数据 |
| | SmartWriting | /learning/writing | **mock** | ❌ 假数据 |
| | CodePractice | /learning/code-practice | — | 未审查 |
| | LearningMode | /learning/mode | — | 未审查 |
| 个人 | Profile / Favorites / NotesManage | /profile /favorites /notes | 后端 API | ✅/⚠️ |
| | Mistakes | /mistakes | 后端 API | ⚠️ 练习按钮无事件 |
| 社区 | Community | /community | 后端 API | ⚠️ 发帖按钮无事件 |
| 通知 | Notifications | /notifications | 后端 API(永远空) | ❌ 后端不生成 |
| 管理 | admin/Overview | /admin/overview | **mock** | ❌ 假数据 |
| | admin/DocManagement / KnowledgeMgmt / UserManagement / TagManagement / FlashcardMgmt | /admin/* | 后端 API | ✅ 真实 |
| | admin/ChatConfig | /admin/chat-config | **localStorage** | ❌ 不生效 |
| | admin/CommunityManage | /admin/community | — | 未审查 |

---

## 三、模块依赖关系分析

### 3.1 后端模块依赖

```
sys_user ──┬── doc_favorite ──── doc_document ──── doc_category
           ├── doc_read_progress ─┘
           ├── chat_conversation ── chat_message
           ├── learning_user_path ── learning_path ── learning_chapter
           │                          └── learning_flashcard
           ├── learning_task
           ├── learning_mistake
           ├── community_post (comment_count 无评论表支撑)
           └── sys_notification (无生成入口)
```

**依赖问题：**
- **强耦合点**：`DocServiceImpl.getDocDetail` 依赖 `CategoryService` 取分类名（DocServiceImpl.java:92-100），文档与分类服务耦合。
- **职责重复**：`DocController`（前台）与 `AdminDocController`（后台）的 create/update/delete 逻辑完全重复，且前台无鉴权。
- **割裂点**：`ChatService` 与 `DocService` 完全无交互——AI 对话不检索知识库文档，doc_references 硬编码。
- **孤儿数据风险**：删除文档不清收藏/进度；删除分类不处理子分类与文档；删除学习路径不清理章节/闪卡/用户进度。

### 3.2 前后端契约一致性

| 问题 | 前端 | 后端 | 影响 |
|------|------|------|------|
| 闪卡管理列表类型 | 期望 `{records,total}` | 返回 `List` | 后台闪卡页崩 |
| 通知列表 isRead 参数 | 传 isRead | 不接收 | 过滤失效 |
| AdminUser 返回类型 | 标注 UserVO | 返回 SysUser | 密码哈希泄露 |
| AdminDoc 返回类型 | 标注 DocVO | 返回 DocDocument | 字段不一致 |
| MessageVO.docReferences | 定义但不用 | 返回字符串 | 参考来源断链 |
| Result.code 非 200 | 当成功 | 返回 error | 业务错误被吞 |

---

## 四、严重缺陷清单（P0 — 必须立即修复）

### 4.1 安全类（最高优先级）

| # | 缺陷 | 位置 | 风险 |
|---|------|------|------|
| S1 | **管理员接口零鉴权**：所有 `/api/admin/**` 无 `@PreAuthorize`，任意登录用户可越权 CRUD 用户/文档/分类/学习路径 | SecurityConfig.java:46 + 全部 admin Controller | 越权破坏 |
| S2 | **普通用户可提权 ADMIN**：`PUT /api/admin/users/{自己id}` 传 `{"role":"ADMIN"}` | AdminUserController.java:55-60 | 权限提升 |
| S3 | **JWT 权限硬编码 ROLE_USER**：过滤器对所有 token 一律授 ROLE_USER，未启用 @EnableMethodSecurity | JwtAuthenticationFilter.java:40 | 即使加注解也无效 |
| S4 | **AdminUserController 泄露密码哈希**：返回 `SysUser` 实体 | AdminUserController.java:25,43 | 凭证泄露 |
| S5 | **AdminUserController.add/update 密码明文入库**：未 BCrypt 编码 | AdminUserController.java:49-52,56-60 | 用户无法登录 |
| S6 | **JWT 密钥硬编码且弱**：application.yml 明文写死，可预测 | application.yml:50 + JwtUtils.java:18 | 可伪造任意 token |
| S7 | **无登出/token 吊销机制**：token 24h 内不可失效 | 全项目无 logout | 无法主动下线 |
| S8 | **前台文档写接口无鉴权**：POST/PUT/DELETE `/api/docs` 任意登录用户可改任意文档 | DocController.java:84-103 + SecurityConfig | 文档可被篡改 |
| S9 | **批量赋值漏洞**：DocController/AdminDocController 直接接收实体，可篡改 id/viewCount/deleted | DocController.java:85,92 | 数据破坏 |
| S10 | **草稿可通过公开详情泄露**：getDocDetail 不校验 status | DocServiceImpl.java:80-84 | 内容泄露 |
| S11 | **公开闪卡/路径接口泄露未发布内容**：不校验 status=1 | LearningServiceImpl.java:53-59,87-99 | 草稿泄露 |
| S12 | **错题详情/标记不校验归属**：任意用户可读写他人错题 | MistakeServiceImpl.java:41-47,50-57 | 隐私泄露 |
| S13 | **通知标记不校验归属**：任意用户可标记他人通知已读 | NotificationServiceImpl.java:35-41 | — |
| S14 | **Chat.vue v-html XSS**：renderMarkdown 非代码内容未转义 | Chat.vue:154,476-498 | 存储型 XSS |
| S15 | **Token 存 localStorage**：配合 XSS 可窃取 | auth.ts:10,21 | 凭证窃取 |

### 4.2 功能缺失类

| # | 缺陷 | 位置 | 影响 |
|---|------|------|------|
| F1 | **AI 对话实为模拟回复**：generateMockReply 返回硬编码字符串，未接大模型 | ChatServiceImpl.java:115-118 | 核心卖点虚假 |
| F2 | **doc_references 硬编码**：无文档检索，知识库与对话割裂 | ChatServiceImpl.java:78 | RAG 未实现 |
| F3 | **闪卡复习接口完全不存在**：无 SM-2、reviewCount/difficulty 死字段 | 全项目 grep 0 命中 | 复习功能虚假 |
| F4 | **学习任务整模块未实现**：从不创建，deadline/exp/energy 全死字段 | LearningTask 仅读不写 | 任务系统空壳 |
| F5 | **通知整模块"只读不写"**：从不 insert，用户侧永远空 | 全项目无 insert | 通知系统空壳 |
| F6 | **社区无评论功能**：comment_count 是死字段，无评论表/接口 | 无 CommunityComment | 互动功能虚假 |
| F7 | **社区无编辑/删除/加精/审核**：isEssence/status 无逻辑 | 无对应接口 | 管理失控 |
| F8 | **错题无更新/删除/复习接口** | MistakeController 仅 GET/POST/mastered | 错题不可维护 |
| F9 | **用户统计字段永不更新**：readDocsCount/favoriteCount/level/exp/energy 全死，getUserStats 硬编码 0 | UserServiceImpl.java:60-66,99-100 | 统计虚假 |
| F10 | **completeChapter 非幂等**：重复调用 progress 可破 100%，且无法判断某章节是否已完成（设计层缺陷） | LearningServiceImpl.java:152 | 进度失真 |
| F11 | **删除分类不处理子分类与文档**：产生孤儿数据 | AdminCategoryController.java:44-46 | 数据完整性破坏 |
| F12 | **删除文档不清理收藏/进度**：收藏列表项"静默消失" | DocServiceImpl 未重写 removeById | 数据不一致 |
| F13 | **word_count/doc_count 从未计算维护** | 全项目无赋值 | 字段形同虚设 |

### 4.3 前端严重类

| # | 缺陷 | 位置 | 影响 |
|---|------|------|------|
| FE1 | **AdminLearningController.listFlashcards 返回 List 非 PageResult** | AdminLearningController.java:91-99 | 后台闪卡页崩 |
| FE2 | **notificationsApi.isRead 后端不接收** | NotificationController.java:23-32 | 过滤失效 |
| FE3 | **Chat "参考来源"完全不工作**：sources 从未赋值 | Chat.vue:158,429-434 | 功能虚假 |
| FE4 | **Chat 假流式**：逐字 setTimeout，未清理定时器 | Chat.vue:458-470 | 内存泄漏、体验差 |
| FE5 | **401 硬跳转丢状态**：window.location.href 不带 redirect | request.ts:30 | 表单丢失、无法回跳 |
| FE6 | **Community "发布帖子"按钮无 @click** | Community.vue:8-15 | 无法发帖 |
| FE7 | **Mistakes "开始练习"按钮无 @click** | Mistakes.vue:9-16 | 无法练习 |
| FE8 | **FlashCards 复习结果不持久化** | FlashCards.vue:219-228 | 评分丢失 |
| FE9 | **ReviewPlan 纯前端 mock**：艾宾浩斯是装饰 | ReviewPlan.vue:360-367 | 功能虚假 |
| FE10 | **6+ 页面全 mock**：SmartQuiz/SmartWriting/LearningReport/LearningCenter/admin Overview | 各 vue 文件 | 数据虚假 |
| FE11 | **路由命名错位**：LearningCenter→LearningReport.vue，LearningPomodoro→LearningCenter.vue | router/index.ts:82-86,136-140 | name 导航错乱 |
| FE12 | **admin 复用 SmartQuiz/SmartWriting 是假复用**：无管理功能 | router/index.ts:250-260 | 管理后台空壳 |
| FE13 | **Result.code 非 200 被当成功**：拦截器未校验 | request.ts:22-35 | 业务错误被吞 |

---

## 五、中等问题清单（P1 — 应尽快修复）

### 后端

| # | 缺陷 | 位置 |
|---|------|------|
| M1 | 无刷新 token 机制 | JwtUtils.java:21 |
| M2 | 无暴力登录防护/账号锁定 | UserServiceImpl.java:31-45 |
| M3 | 登录响应区分"用户不存在/密码错误"——用户枚举 | UserServiceImpl.java:35,38 |
| M4 | UpdateProfileDTO 无校验且未加 @Valid | UpdateProfileDTO.java + UserController.java:36 |
| M5 | 注册/改资料未检查 email 唯一 | UserServiceImpl.java:48-53 |
| M6 | 分页 pageSize 无上限（可传 1000000） | PageQuery.java:12 |
| M7 | 密码最低 6 位无复杂度要求 | RegisterDTO.java:18-19 |
| M8 | 删除用户无"不能删自己/admin"保护 | AdminUserController.java:63-67 |
| M9 | 无自定义 AuthenticationEntryPoint——403 非 401 | SecurityConfig.java |
| M10 | 计数器并发丢失更新：viewCount/favoriteCount/readCount/likeCount/enrolledCount/completedChapters/messageCount 全是 read-modify-write | 多处 ServiceImpl |
| M11 | 收藏/阅读进度重复插入：无联合唯一约束 + 先查后写 | schema.sql + DocServiceImpl.java:115-127,175-195 |
| M12 | ReadProgressDTO 无任何校验：docId 可 null、progress 可 200 | ReadProgressDTO.java |
| M13 | 消息列表无分页 | ChatServiceImpl.java:45-47 |
| M14 | 对话标题生成简陋（取前 50 字符） | ChatServiceImpl.java:96 |
| M15 | PageQuery.orderBy 未实现（参数被忽略） | PageQuery.java:14 + DocServiceImpl.java:60 |
| M16 | 前后台搜索不一致（前台 title+summary，后台仅 title） | DocServiceImpl.java:46 vs AdminDocController.java:29 |
| M17 | AdminDocController 列表返回 content 全文 | AdminDocController.java:25 |
| M18 | 删除学习路径/章节不级联清理 | AdminLearningController.java:53,84 |
| M19 | 章节 docIds/flashcardIds 无一致性校验 | AdminLearningController.java:69 |
| M20 | chapter VO 始终 completed=false 硬编码 | LearningServiceImpl.java:69,82 |
| M21 | enrolledCount 并发更新丢失 | LearningServiceImpl.java:135 |
| M22 | todayActiveUsers 用 create_time 而非 update_time，严重低估 | AdminOverviewServiceImpl.java:80-98 |

### 前端

| # | 缺陷 | 位置 |
|---|------|------|
| M23 | Store 覆盖不足：仅 auth+notification，跨页状态不共享 | stores/ |
| M24 | logout 清理不彻底：不重置 notification store | auth.ts:44-49 |
| M25 | Login.vue 表单校验弱、rememberMe 是装饰按钮 | Login.vue:297,307-311 |
| M26 | Login.vue 内嵌注册绕过 agreeTerms 校验 | Login.vue:329-356 |
| M27 | Chat.vue selectedModel/useKnowledgeBase 无效（不传后端） | Chat.vue:273,278,428 |
| M28 | Chat.vue deleteChat 失败仍本地删除 | Chat.vue:362-369 |
| M29 | DocDetail 阅读进度仅在 onUnmounted 保存（刷新/关页丢失） | DocDetail.vue:561-571 |
| M30 | 大量 catch { /* 忽略 */ } 吞错：DocDetail/ChapterLearn/PathDetail/Profile/Chat | 多处 |
| M31 | 加载态三套实现不统一 | 多处 |
| M32 | 死按钮汇总：忘记密码/社交登录/分享/记笔记 | Login.vue:100,254 / DocDetail.vue:527 |

---

## 六、轻微问题清单（P2 — 择机优化）

1. 注册 username 无格式约束、email 无 @NotBlank
2. nickname/avatar 无 XSS 过滤
3. H2 console 生产环境须关闭
4. /api/auth/me 鉴权方式不一致
5. PageQuery.orderBy 潜在注入面（当前未用）
6. Integer 拆箱 NPE 风险（viewCount 等为 null 时）
7. getDocDetail 的 updateById 副作用（刷新 updateTime）
8. 分类树循环引用无防护
9. 推荐列表硬编码 LIMIT 10
10. getFavoriteList 顺序丢失（listByIds 不保序）
11. Chat lastMessage 无长度截断
12. 分类 code 无唯一性保障
13. 管理员分类视图非树形
14. learningApi 与 adminApi 闪卡 CRUD 重复
15. DocDetailVO.readProgress 类型不一致（BigDecimal vs number）
16. Chat.vue 会话搜索仅前端过滤
17. Profile.vue 深色模式开关无效
18. Login.vue 内嵌注册与 Register.vue 重复
19. Profile.vue 头像 URL 未校验

---

## 七、改进建议路线图

### 第一阶段：安全加固（上线前必须完成）

1. **启用方法级安全**：SecurityConfig 加 `@EnableMethodSecurity`，所有 admin Controller 加 `@PreAuthorize("hasRole('ADMIN')")`
2. **修复 JWT 过滤器**：从 DB/token 读取真实 role 授予对应权限，移除硬编码 ROLE_USER
3. **AdminUserController 重构**：返回 UserVO（@JsonIgnore password 或改 VO）、add/update 对 password 做 BCrypt、update 限制可改字段（白名单，禁止改 role）
4. **JWT 密钥外置**：从环境变量读取，移除默认值，长度 ≥ 32 字节
5. **实现登出**：token 黑名单（Redis）+ /api/auth/logout 端点
6. **移除前台文档写接口**：DocController 的 POST/PUT/DELETE 删除或改为仅管理员，统一由 AdminDocController 管理
7. **修复批量赋值**：改用专用 DTO 而非直接接收实体
8. **草稿/未发布内容保护**：getDocDetail 加 status=1 校验（管理员除外），闪卡/路径详情同理
9. **归属校验**：错题/通知的 detail 与 markAsRead 加 userId 校验
10. **XSS 防护**：Chat.vue renderMarkdown 对所有内容转义，或引入 DOMPurify

### 第二阶段：核心功能补全

1. **接入大模型**：ChatService 调用真实 LLM API（DeepSeek/通义等），实现流式 SSE
2. **实现 RAG**：用户提问 → 检索知识库文档 → 组装 prompt → 调用 LLM → 返回 doc_references
3. **闪卡间隔重复**：实现 SM-2 算法，新增复习接口，更新 reviewCount/difficulty/nextReviewTime
4. **学习任务系统**：实现任务创建（报名路径自动生成、每日任务）、状态机、deadline 提醒
5. **通知系统**：在关键业务节点（点赞、报名、任务到期、错题提醒）生成通知
6. **社区评论**：新增 community_comment 表 + CRUD 接口，维护 comment_count
7. **社区管理**：编辑/删除自己的帖子、管理员加精/审核接口
8. **错题完善**：新增更新/删除/复习接口，reviewCount 自增
9. **用户统计联动**：阅读文档 +1 readDocsCount、收藏 +1 favoriteCount 等，用 SQL 原子更新
10. **completeChapter 幂等化**：新增 learning_user_chapter 关联表记录已完成章节，complete 前先查重

### 第三阶段：数据一致性与体验

1. **计数器原子化**：所有 count 字段改用 `UPDATE ... SET count = count + 1`，移除 read-modify-write
2. **联合唯一约束**：doc_favorite (user_id, doc_id)、doc_read_progress (user_id, doc_id) 加 UNIQUE
3. **级联清理**：删除文档时清理收藏/进度；删除分类时迁移/拒绝；删除路径时清理章节/闪卡/用户进度
4. **word_count/doc_count 维护**：文档保存时计算字数，文档增删时更新分类计数
5. **前端契约对齐**：修复 listFlashcards 返回类型、notifications isRead 参数、AdminUser/AdminDoc 返回 VO
6. **request.ts 拦截器**：校验 Result.code，非 200 时 reject + toast；401 改用 router.push 带 redirect
7. **路由命名修正**：交换 LearningCenter 与 LearningPomodoro 的组件，或重命名
8. **假数据治理**：6+ mock 页面明确标注"演示数据"或接线后端
9. **死按钮治理**：Community 发帖、Mistakes 练习按钮接线或移除
10. **Chat 真流式**：后端改 SSE，前端 EventSource 消费，移除假打字

### 第四阶段：工程化与防御性编程

1. **分页参数校验**：PageQuery 加 @Max(100)、pageNum ≥ 1
2. **DTO 校验补全**：UpdateProfileDTO、ReadProgressDTO 加注解 + @Valid
3. **email 唯一约束**：schema 加 UNIQUE + Service 查重
4. **登录防暴**：失败计数 + 账号锁定 + 统一错误信息
5. **refresh token**：短期 access + 长期 refresh
6. **统一加载态/错误态组件**：全局 Loading/Error/Empty 组件
7. **全局 loading 指示器**：路由切换顶部进度条
8. **Pinia store 扩展**：useDocStore/useLearningStore 聚合跨页状态
9. **今日活跃统计修正**：改用 update_time 或 last_activity 字段
10. **生产环境配置**：关闭 H2 console、关闭 swagger、CORS 白名单

---

## 编程 Agent 意图识别与答案生成优化（方案 P1~P3，2026-08-03 落地）

**模块现状**：后端已先行落地 `IntentService`（多轮 LLM 意图分类 + 结构/语义歧义检测 + 准确率评估）、`IntentKnowledge`（语言/框架/场景规则表）、`AgentIntentController`（`/api/agent/intent`、`/ambiguities`、`/evaluate`）与 `AgentCallLog.score` 字段；前端 `CodeAgent.vue` 完成了链路打通。

**新增能力**
- P1 多轮上下文动态意图识别：前端 `send()` 调 `detectIntent` 融合近 6 轮历史 + 目录快照做 LLM 分类（generate/modify/explain/debug/chat），取代旧正则二分类；异常降级为正则兜底。
- P2 显式意图确认：低置信度/参数缺失时后端返回 `needsClarify` + 结构化澄清问题，前端冻结输入展示澄清卡片，作答后重进识别；推理步骤链新增「澄清意图」节点。
- P2 结构/语义歧义检测：生成后调 `detectAmbiguities`，基于挂载目录快照做缺文件/框架不匹配/语言冲突探测，产物区 `--kb-warning` 高亮标签。
- P3 准确率评估闭环：生成/回答后调 `evaluate` 回填 `evalScore`，展示匹配度徽标，回写 `AgentCallLog.score`。

**遗留与建议（择机优化）**
- `IntentService.structuralProbe` 的 `missing-file` 分支当前为空，未真正标记「修改不存在的文件」，建议补齐以闭环 P2 结构探针。
- 多轮硬指代（`parentId`）解析：后端目前依赖历史文本让 LLM 软消解，未实现 `parentId` 显式查 `history` 定位；如需「再加个暗色主题」式精确指代，需前端回传引用 + 后端定位。
- `evaluate` 结果写入 `AgentCallLog.score` 的落库调用尚未接，建议在生成后补一条日志写入以支撑 P3 反哺。

---

## 八、模块健康度评分

| 模块 | 功能完整度 | 安全性 | 数据一致性 | 前后端契约 | 综合评分 |
|------|-----------|--------|-----------|-----------|---------|
| 认证 | 60%（无登出/刷新） | 30%（密钥弱/无防暴） | — | 80% | **D** |
| 用户 | 40%（统计死） | 50%（越权） | 20%（统计不更新） | 70% | **D** |
| 文档 | 70%（word_count 死） | 20%（写接口无鉴权） | 30%（并发/级联） | 60% | **D+** |
| 分类 | 60%（doc_count 死） | 50% | 30%（删除不级联） | 80% | **C-** |
| 搜索 | 50%（orderBy 未实现） | 70% | — | 70% | **C** |
| AI 对话 | 20%（模拟回复） | 60% | 40%（计数并发） | 50%（参考来源断链） | **F** |
| 学习路径 | 50%（completeCh 非幂等） | 30%（公开泄露） | 30%（并发） | 70% | **D** |
| 闪卡 | 30%（无复习） | 30%（公开泄露） | 20%（死字段） | 60% | **F** |
| 学习任务 | 10%（空壳） | 50% | 10% | 50% | **F** |
| 错题 | 40%（无编辑/复习） | 20%（不校验归属） | 20%（reviewCount 死） | 70% | **F** |
| 社区 | 30%（无评论/管理） | 60% | 30%（计数并发） | 70% | **F** |
| 通知 | 10%（不生成） | 20%（不校验归属） | 10% | 80% | **F** |
| 管理后台 | 50%（mock 概览） | 10%（零鉴权） | 50% | 50%（类型不匹配） | **F** |
| 前端整体 | 50%（多 mock） | 30%（XSS/localStorage） | — | 60% | **D** |

**项目整体评级：D-（骨架完整但核心功能与安全均不达标，不可上线，需按路线图分阶段修复）**

---

*报告结束。详细代码位置见各章节引用的 文件:行号。*
