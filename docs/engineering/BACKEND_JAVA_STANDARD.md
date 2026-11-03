# KnowFlow 后端 Java 工程规范

> **CURRENT · 2026-11-01** · 适用：`backend/src/main/java/com/knowflow/**`
> 上游依据：阿里巴巴 Java 开发手册、Spring Boot 3.x 官方参考、Java 并发实践、项目真实分层（Spring Boot 3.2.5 / Java 17 / MyBatis-Plus 3.5.5）。

---

## 0. 适用与优先级

- 本分册只约束后端代码与数据库变更。
- 与 [AGENTS.md](../../AGENTS.md) 架构铁律冲突时，架构铁律优先。
- 存量违规采用**棘轮基线**（见 [TESTING_AND_AUTOMATION_GATES](TESTING_AND_AUTOMATION_GATES.md)），不得新增。

---

## 1. 命名规范

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| BJ-01 | 类名 `UpperCamelCase`；接口/枚举/注解同理；抽象类以 `Abstract`/`Base` 开头，异常类以 `Exception` 结尾 | MUST | OFFICIAL |
| BJ-02 | 方法、参数、局部变量、成员变量 `lowerCamelCase` | MUST | OFFICIAL |
| BJ-03 | 常量 `UPPER_SNAKE_CASE`，`long` 字面量用大写 `L` | MUST | OFFICIAL |
| BJ-04 | 包名全小写，单数；本项目领域包使用 `learning`、`exam`、`interview`、`flashcards`、`catalog`、`agent`、`ai`、`knowledge` 等既有命名 | MUST | PROJECT |
| BJ-05 | 禁止无解释魔法值；可复用字面量必须提取为常量或配置项 | MUST | OFFICIAL |
| BJ-06 | POJO/DTO/VO 布尔属性不加 `is` 前缀；数据库列可用 `is_xxx` | MUST | OFFICIAL |
| BJ-07 | 数据库实体后缀按既有约定：`entity/` 下直接用领域名（如 `SysUser`、`DocDocument`），不再新增 `DO/PO` 同义层 | MUST | PROJECT |
| BJ-08 | 禁止以 `_` 或 `$` 开头/结尾命名 | FORBIDDEN | OFFICIAL |

**存量说明**：领域包内存在平铺 `service/impl` 与领域内 `Service` 两种组织；新类必须跟随**所属领域包**的既有组织方式，不在同一领域内混用。

---

## 2. 工程分层

| 层 | 目录 | 职责 | 禁止 |
|---|---|---|---|
| Controller | `controller/`、领域内 `controller/` | 认证上下文取身份、入参校验、DTO↔VO 转换、HTTP 语义 | 写业务规则、直接操作多表、拼接 SQL |
| DTO / VO | `dto/`、`vo/`、领域内 `vo/` | 请求/响应合同 | 承载数据库查询逻辑 |
| Service | `service/`、领域内 `xxxService` | 业务规则、事务边界、归属校验、幂等 | 依赖 `HttpServletRequest`、返回 `ResponseEntity` |
| Mapper | `mapper/`、领域内 `mapper/` | 持久化访问（MyBatis-Plus / JDBC） | 承载业务分支判断 |
| Entity | `entity/`、领域内 `entity/` | 表映射 | 直接作为对外 API 响应 |
| 公共 | `common/`、`exception/`、`util(s)/` | `Result<T>`、`BusinessException`、`GlobalExceptionHandler`、工具 | 放业务逻辑 |

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| BJ-09 | 新增接口必须遵循 Controller → Service → Mapper 调用链；跨领域调用走 Service，不直接调他人 Mapper | MUST | PROJECT |
| BJ-10 | Controller 不得信任请求体内的 `userId` / `role` / `ownerId`；身份一律来自服务端认证上下文 | MUST | PROJECT |
| BJ-11 | 对外响应统一 `Result<T>`；分页沿用既有 `PageResult` 合同 | MUST | PROJECT |
| BJ-12 | 事务注解只加在 Service 公共方法；同类内部调用不得绕过代理导致事务失效 | MUST | OFFICIAL |
| BJ-13 | 不得为统一外观合并无关表、复制一套新框架或另建平行 Service | FORBIDDEN | PROJECT |

---

## 3. 数据库规范

### 3.1 命名与结构

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| BJ-14 | 表名/列名小写字母或数字，禁止数字开头，禁止双下划线夹数字；表名用单数 | MUST | OFFICIAL |
| BJ-15 | 表必备字段：`id`（BIGINT 主键）、`create_time`、`update_time`（DATETIME） | MUST | OFFICIAL |
| BJ-16 | 逻辑删除统一 `deleted TINYINT/INT DEFAULT 0`（1=已删）；业务查询默认过滤 | MUST | PROJECT |
| BJ-17 | 主键索引 `pk_*`、唯一索引 `uk_*`、普通索引 `idx_*`；逻辑关联列必须建索引 | MUST | OFFICIAL |
| BJ-18 | 金额/比率用 `DECIMAL`，禁止 `FLOAT`/`DOUBLE` 承载精确值 | MUST | OFFICIAL |
| BJ-19 | `VARCHAR` 不超过 5000；超长正文用 `TEXT` 并独立表 | MUST | OFFICIAL |
| BJ-20 | 必备中文 `COMMENT`：表与关键列说明业务含义 | SHOULD | OFFICIAL |

### 3.2 外键（存量冲突已登记）

**审计事实（2026-11-01）**：`db/mysql/schema.sql` 第 2135–2217 行存在 9 处 `FOREIGN KEY`（interview 相关表），与第 295 行注释「不建立任何 FOREIGN KEY」及既有 Skill 声明冲突。

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| BJ-21 | **新增表禁止物理外键与级联**，关系完整性由 Service 层维护 | FORBIDDEN（新增） | OFFICIAL |
| BJ-22 | 存量外键不擅自删除；删除必须单独授权、评估数据依赖与迁移回滚 | MUST | PROJECT |
| BJ-23 | 新增逻辑关联列必须同时建索引，并在 Service 校验归属 | MUST | PROJECT |

### 3.3 迁移与运行时 Schema

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| BJ-24 | 迁移文件放 `db/h2/migration` 与 `db/mysql/migration` **双份**，命名 `V{yyyyMMdd}[_n]__{name}.sql` | MUST | PROJECT |
| BJ-25 | 同一变更的 H2/MySQL 两份迁移语义一致；H2 通过不代表 MySQL 约束已验证 | MUST | PROJECT |
| BJ-26 | 启动时幂等补列/补表（`*Schema.java`）只能做 `IF NOT EXISTS` 类安全操作，不得静默改类型、删列或重写历史数据 | MUST | PROJECT |
| BJ-27 | 禁止在测试或脚本中连接正式数据源；运行时切库默认关闭 | FORBIDDEN | PROJECT |

### 3.4 查询与并发

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| BJ-28 | 禁止 `SELECT *`；查询显式列名；计数用 `COUNT(*)` | MUST | OFFICIAL |
| BJ-29 | `IN` 集合元素控制在 1000 以内，超出分批 | MUST | OFFICIAL |
| BJ-30 | 分页必须服务端下推（`LIMIT`/`Page`），不得取全量后内存分页 | MUST | PROJECT |
| BJ-31 | 并发写采用数据库约束/条件更新/行锁/乐观锁（`revision`、`version`），不得只靠前端防重 | MUST | PROJECT |
| BJ-32 | 存量 `SELECT *` 55 处进入棘轮基线；触及文件时优先顺手消除本次涉及的语句 | SHOULD | RECOMMENDATION |

---

## 4. 安全规范（后端子集）

完整条款见 [SECURITY_AND_AI_STANDARD](SECURITY_AND_AI_STANDARD.md)。

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| BJ-33 | 每个资源接口在 Service 校验 Owner / 协作角色 / 管理员；UI 隐藏不等于授权 | MUST | PROJECT |
| BJ-34 | 入参使用 Bean Validation（`@Valid`、`@NotBlank`、`@Size` 等）；Controller 不接收未校验的裸 Map | MUST | PROJECT |
| BJ-35 | 动态 SQL 参数必须绑定，禁止字符串拼接用户输入 | FORBIDDEN | OFFICIAL |
| BJ-36 | 日志/异常响应不得输出密码、Token、API Key、完整身份证/手机号、私密正文 | FORBIDDEN | PROJECT |
| BJ-37 | 敏感操作的二次确认只是交互，不构成服务端授权或沙箱 | MUST | PROJECT |

---

## 5. 并发规范

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| BJ-38 | 禁止 `Executors.new*` 创建线程池；必须 `ThreadPoolExecutor` 显式核心/最大线程、队列、线程工厂、拒绝策略 | MUST（新增） | OFFICIAL |
| BJ-39 | 存量 13 处 `Executors.new` / `new Thread(` 逐例登记（判题、宿主、解析等专用场景），不得扩大 | MUST | PROJECT |
| BJ-40 | 异步任务必须有超时与异常出口；禁止 fire-and-forget 吞掉异常 | MUST | RECOMMENDATION |
| BJ-41 | `CompletableFuture` 需显式指定执行器，禁止默认 ForkJoinPool 承载阻塞 IO | SHOULD | RECOMMENDATION |
| BJ-42 | 重试必须幂等：使用 `requestId` / 唯一约束 / 条件更新，禁止重复写事件或重复扣减 | MUST | PROJECT |
| BJ-43 | 事务内不做远程 AI/HTTP 长调用；外部调用放在事务外或明确拆分补偿 | SHOULD | RECOMMENDATION |
| BJ-44 | `SimpleDateFormat` 禁止作为共享静态字段；使用 `DateTimeFormatter` | MUST | OFFICIAL |

---

## 6. 异常与日志

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| BJ-45 | 禁止 `printStackTrace()`；禁止空 catch 吞异常 | FORBIDDEN | OFFICIAL |
| BJ-46 | 禁止 `System.out.println` / `System.err`；统一 SLF4J（Lombok `@Slf4j`） | FORBIDDEN（新增） | PROJECT |
| BJ-47 | 异常用于错误处理，不用于正常流程控制 | MUST | OFFICIAL |
| BJ-48 | 业务失败抛 `BusinessException` 并携带明确业务码；4xx 与 5xx 语义必须显式 | MUST | PROJECT |
| BJ-49 | 兜底 `Exception` 处理器不得把可预期错误伪装成 500；错误信息不得暴露堆栈、类名、SQL | MUST | PROJECT |
| BJ-50 | 已知兼容风险：`BusinessException` 5xx 业务码当前返回 HTTP 200 | LEGACY（待整改） | PROJECT |
| BJ-51 | 日志分级正确：可恢复业务冲突 `warn`，系统故障 `error` 且带异常对象；禁止把正常路径打 `error` | SHOULD | RECOMMENDATION |

---

## 7. 注释与文档

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| BJ-52 | 类、公共方法必须有 Javadoc 说明业务意图、边界、不变量；getter/setter 可省略 | SHOULD | OFFICIAL |
| BJ-53 | 中文注释解释「为什么」与业务约束，不复述代码字面 | MUST | PROJECT |
| BJ-54 | 不得把提案、计划、设想写进注释冒充已实现行为 | FORBIDDEN | PROJECT |
| BJ-55 | API 变更同步 [BACKEND_MODULE_INVENTORY](../BACKEND_MODULE_INVENTORY.md) 与相关模块文档 | SHOULD | PROJECT |

---

## 8. 后端测试规范

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| BJ-56 | 新增/修复必须有回归测试；修复前先让回归用例失败 | MUST | PROJECT |
| BJ-57 | 单元测试用 JUnit 5 + Mockito；集成/HTTP 用 Spring Boot Test 或项目既有 HTTP 测试基类 | MUST | PROJECT |
| BJ-58 | 测试必须自带安全默认：AI/Host/Judge/运行时切库/开发种子关闭（Surefire 已配置，不得在测试内绕过） | MUST | PROJECT |
| BJ-59 | 权限测试至少覆盖 Anonymous / Owner / Other / Admin；知识库另测 EDITOR / READER | MUST | PROJECT |
| BJ-60 | 禁止删除测试、跳过测试、放宽断言制造 PASS；不得把 H2 结果称为 MySQL 验证 | FORBIDDEN | PROJECT |
| BJ-61 | 测试命名表达业务合同（如 `Should_RejectOtherUsersDocument`）；禁止 `test1` 类命名 | SHOULD | RECOMMENDATION |

---

## 9. API 契约

| 编号 | 规则 | 级别 | 来源 |
|---|---|---|---|
| BJ-62 | 新接口路径沿用现有 `/api/{domain}/...` 风格；同一领域不新造第二套前缀或第二套响应包装 | MUST | PROJECT |
| BJ-63 | 对外只暴露 DTO/VO，不复用 Entity；字段增删必须评估前端合同 | MUST | PROJECT |
| BJ-64 | 分页参数与响应沿用 `pageNum` / `pageSize` 与 `PageResult{records,total,pageNum,pageSize,pages}` | MUST | PROJECT |
| BJ-65 | 写接口必须幂等或携带 `revision` / `requestId`；冲突返回 409 并说明可恢复路径 | MUST | PROJECT |
| BJ-66 | 破坏性语义变更必须新增版本或兼容字段，不得静默改变既有字段含义 | MUST | PROJECT |

---

## 10. 提交前自检清单

1. 是否新增物理外键、`SELECT *`、`System.out`、`printStackTrace`、`Executors`？（必须为否）
2. 是否从请求体取身份？是否在 Service 校验归属？（必须为是/是）
3. 是否新增迁移双份且语义一致？
4. 是否补充回归测试并覆盖权限矩阵？
5. `cd backend && mvn -B test` 是否通过？失败项是否区分既有/本次引入？
