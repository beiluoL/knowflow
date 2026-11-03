# KnowFlow 安全与 AI 工程规范

> **CURRENT · 2026-11-01** · 适用：全部后端接口、前端调用、AI Provider、判题/宿主能力、数据与内容处理。
> 上游依据：OWASP ASVS 5.0、OWASP Top 10 (2021)、Spring Security 官方实践、项目既有安全默认（`SecurityConfig`、Surefire 安全属性、`roadmap/07`）。

---

## 1. 安全底线

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| SEC-01 | 身份只来自服务端认证上下文（`SecurityConfig` + `JwtAuthenticationFilter`）；禁止信任请求体/查询参数中的 `userId`、`role`、`ownerId` | FORBIDDEN | PROJECT |
| SEC-02 | 每个资源接口必须在 Service 层校验归属：Owner / 协作角色 / 管理员 | MUST | PROJECT |
| SEC-03 | UI 隐藏、按钮置灰、二次确认均不构成授权；授权只在后端执行 | MUST | PROJECT |
| SEC-04 | 默认拒绝：新增公开端点必须显式列入安全白名单并说明理由 | MUST | OFFICIAL |
| SEC-05 | 禁止在日志、异常响应、前端埋点中输出 Token、Cookie、API Key、密码、私密正文、完整手机号/身份证 | FORBIDDEN | PROJECT |

---

## 2. 权限矩阵（必须逐项验证）

| 场景 | 必须覆盖 | 级别 |
|---|---|---|
| 通用资源 | Anonymous / Owner / Other User / Admin | MUST |
| 知识库 | 上述四项 + EDITOR / READER + 继承 / 撤权 / 未发布 / 逻辑删除 / 改版 | MUST |
| 导出 / 分享 / 快照 / 附件 | 归属校验 + 脱敏 + 到期 / 撤销 | MUST |
| 列表与搜索 | 不得因过滤缺失泄露他人私有资源的存在性 | MUST |
| 账号切换 / 重新登录 | 旧账号缓存数据不得残留到新会话 | MUST |

来源：PROJECT（AGENTS「Security & Ownership」、roadmap/07 §3）。

---

## 3. 输入、输出与 HTTP 语义

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| SEC-06 | 所有外部输入在边界校验（Bean Validation / 前端表单校验仅作体验，不作为安全边界） | MUST | OFFICIAL |
| SEC-07 | SQL 使用参数绑定；禁止拼接用户输入；富文本/HTML 必须经 DOMPurify 或等价白名单过滤 | FORBIDDEN | OFFICIAL |
| SEC-08 | 上传文件校验类型、大小、扩展名与存储路径；不使用用户原始文件名作为落盘路径 | MUST | OFFICIAL |
| SEC-09 | 状态码必须明确：400 / 401 / 403 / 404 / 409 / 415 / 500；同时断言 HTTP 状态与 `body.code` | MUST | PROJECT |
| SEC-10 | 已登记兼容风险：`BusinessException` 5xx 业务码当前返回 HTTP 200；新接口不得沿用该行为 | LEGACY | PROJECT |
| SEC-11 | 错误响应不得包含堆栈、SQL、内部类名、文件路径、模型 Prompt 全文 | FORBIDDEN | PROJECT |
| SEC-12 | CORS 只允许明确来源；禁止 `*` 搭配凭证 | FORBIDDEN | OFFICIAL |

---

## 4. 数据与隐私

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| SEC-13 | 逻辑删除记录不得被普通查询返回；`deleted=1` 不释放 UNIQUE，需验证重复注册/重复关系 | MUST | PROJECT |
| SEC-14 | 新注册账号不得复用旧用户 ID 或继承其历史私有数据 | FORBIDDEN | PROJECT |
| SEC-15 | 测试、脚本、演示不得连接正式数据源，不得写入真实学习记录 | FORBIDDEN | PROJECT |
| SEC-16 | 备份/导出文件必须脱敏并限定访问范围与保留期限 | MUST | PROJECT |
| SEC-17 | 删除用户数据按既有策略执行；不得以「清理」为名删除他人记录 | FORBIDDEN | PROJECT |

---

## 5. AI 工程规范

### 5.1 Provider 与调用

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| SEC-18 | 付费模型调用必须获得用户明确授权；不得读取或输出 API Key | MUST | PROJECT |
| SEC-19 | AI 能力默认关闭；运行时按 Provider/模型/权限/费用/超时/失败策略显式开启（`AiCapabilityExecutor`） | MUST | PROJECT |
| SEC-20 | 模型无配置时必须提供非 AI 回退或明确不可用状态；禁止伪造 AI 结果 | FORBIDDEN | PROJECT |
| SEC-21 | AI 返回内容当作不可信输入：Markdown 安全渲染、代码不自动执行、HTML 预览使用隔离 iframe + 严格 CSP | FORBIDDEN（直接插入主页面） | PROJECT |
| SEC-22 | 未审核的 AI 生成题目/评分结果只进入 `pending_review` / 个人候选，不得自动发布到公共题库 | FORBIDDEN | PROJECT |
| SEC-23 | 内部思考/推理内容默认不永久保存；如需保存必须脱敏并遵守 Provider 使用条款 | MUST | PROJECT |
| SEC-24 | 敏感原文（私人笔记/文档）默认不上传第三方；需要上传时必须显式告知并获授权 | MUST | PROJECT |
| SEC-25 | SSE 流必须支持停止/取消；迟到事件不得写入已切换的会话；失败保留草稿 | MUST | PROJECT |

### 5.2 证据与学习数据（AI 相关）

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| SEC-26 | 无真实事件不得编造 Evidence / Diagnosis；无证据返回空集合 | FORBIDDEN | PROJECT |
| SEC-27 | 不得绕过置信度样本上限直接给满分 | FORBIDDEN | PROJECT |
| SEC-28 | 不得因假设用户不掌握而改写真实掌握度/FSRS 状态 | FORBIDDEN | PROJECT |
| SEC-29 | 模拟评分、演示数据、Mock Provider 必须在 UI 与报告中明确标注，不得冒充真实模型 E2E | FORBIDDEN | PROJECT |

---

## 6. 判题、宿主能力与本地特权

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| SEC-30 | `CodeExecutionServiceImpl` / `LocalHostJavaJudge` 使用宿主机进程，**不是安全沙箱**；不得作为面向普通用户的在线判题后端 | FORBIDDEN | PROJECT |
| SEC-31 | 判题默认 `knowflow.judge.mode=disabled`；仅在独立隔离 Runner 验证通过后才允许翻转 | MUST | PROJECT |
| SEC-32 | `knowflow.host-capabilities.enabled` 默认关闭；本地宿主读写不能等同于生产安全能力 | MUST | PROJECT |
| SEC-33 | 禁止在主业务 JVM 内执行不可信代码；危险工具必须有显式开关、授权与隔离 | FORBIDDEN | PROJECT |
| SEC-34 | 隐藏测试用例、参考解答、代码草稿不得通过普通题目详情接口返回 | FORBIDDEN | PROJECT |

---

## 7. 依赖、配置与秘密

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| SEC-35 | 禁止提交 `application-local.yml`、`.env`、密钥、证书、真实数据库连接串 | FORBIDDEN | PROJECT |
| SEC-36 | 新增依赖必须说明用途、许可证、维护状态；禁止未经审查引入 Skill/插件/二进制 | MUST | PROJECT |
| SEC-37 | 前端构建产物不得内联服务端密钥或内部端点凭证 | FORBIDDEN | OFFICIAL |
| SEC-38 | 依赖漏洞扫描与秘密扫描建议纳入 CI（当前未实现，见审计 M-08） | SHOULD | RECOMMENDATION |

---

## 8. OWASP Top 10 对照（项目落点）

| 风险 | 项目落点 | 对应条款 |
|---|---|---|
| A01 访问控制失效 | Owner/角色校验、知识库权限、导出/分享 | SEC-01~05, 权限矩阵 |
| A02 加密失败 | JWT 密钥强度、传输 HTTPS、敏感存储 | SEC-35, SEC-37 |
| A03 注入 | 参数绑定、Markdown/HTML 白名单 | SEC-07 |
| A04 不安全设计 | 默认拒绝、限流、幂等 | SEC-04, SEC-09 |
| A05 配置错误 | 默认关闭 AI/Judge/Host；CORS | SEC-12, SEC-19, SEC-31~33 |
| A06 组件漏洞 | 依赖审查与扫描 | SEC-36, SEC-38 |
| A07 认证失效 | JWT 过滤链、401 跳转、账号切换 | SEC-01, 权限矩阵 |
| A08 数据完整性 | 快照/revision/幂等/防伪完成 | SEC-26~29 |
| A09 日志监控失败 | 脱敏日志、可观测但不泄露 | SEC-05, SEC-11 |
| A10 SSRF | 外部 URL 抓取与图片代理必须白名单 | SHOULD（未系统覆盖） |

> A10 目前无统一实现，标为静态风险；不得声称已防护。

---

## 9. 提交前自检清单

1. 身份是否来自认证上下文？归属是否在 Service 校验？
2. 权限矩阵是否覆盖 Anonymous/Owner/Other/Admin（知识库另含 EDITOR/READER）？
3. HTTP 与 `body.code` 是否双断言？错误是否泄露内部信息？
4. AI 调用是否获授权、默认关闭、有回退？生成内容是否按不可信处理？
5. 是否触碰判题/宿主/密钥/正式数据？（触碰即停止并升级处理）
