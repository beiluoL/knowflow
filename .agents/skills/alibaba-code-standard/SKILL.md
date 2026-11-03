---
name: alibaba-code-standard
description: 在编写 Java / Spring Boot 后端代码、设计数据库表结构、或评审本项目代码时加载本技能，强制遵循《阿里巴巴 Java 开发手册》核心规约。尤其用于「数据库设计」「表结构 / schema.sql 编写」「建索引」「CRUD 实现」「命名规范」等场景。When the user references 阿里规范 / Alibaba / 开发手册 / 代码规范 / 数据库设计 / 表结构 / 外键，trigger this skill.
agent_created: true
---

# 阿里巴巴 Java 开发规范（强制项速查）

本技能用于所有 Java / Spring Boot 后端开发与数据库设计任务，确保在动手写代码、建表、写 SQL、做评审时默认遵循《阿里巴巴 Java 开发手册》的**强制（强制）**级规约。完整条目见 `references/alibaba_mandatory_rules.md`。

## 何时使用

- 设计数据库表结构、编写 `schema.sql`、加索引
- 编写实体类（Entity / DO / POJO / VO / DTO）
- 实现 CRUD 接口、写 MyBatis / MyBatis-Plus 的 SQL
- 评审或修改 Java 后端代码
- 用户提及「阿里规范」「开发手册」「代码规范」「不要用外键」等

## 最高优先级：新增表禁用物理外键

**【强制】新增表不得使用外键与级联，一切外键概念必须在应用层解决。**

- 物理外键会带来：更新主表主键时的级联问题、增加数据库写入工作量、影响插入速度、易造成死锁、使分库分表困难。
- **存量事实（2026-11-01 审计）**：`db/mysql/schema.sql` 第 2135–2217 行仍有 9 处 `FOREIGN KEY`（interview 相关表），与第 295 行注释冲突；存量外键不擅自删除，删除需单独授权与迁移评估。新表、新关联列一律不写外键。
- 常规表间关联使用**逻辑外键**（仅存储 `category_id` / `user_id` / `path_id` 等关联列，关系完整性由 Service 层代码保证）。
- **逻辑外键列必须建索引**（阿里明确要求）：如 `idx_doc_category ON doc_document(category_id)`。
- `doc_category.parent_id` 用 `0` 作顶级哨兵值（非 NULL），不建自引用外键。

> 在新增任何表或关联列时，默认不写 `FOREIGN KEY`，改为普通索引 + 应用层维护。完整条款见 `docs/engineering/BACKEND_JAVA_STANDARD.md` BJ-21～BJ-23。

## 数据库规约（MySQL）速查

1. 表名、字段名必须用**小写字母或数字**，禁止数字开头，禁止两个下划线之间只出现数字；**表名不用复数名词**。
2. 表达"是否"概念用 `is_xxx`（如 `is_deleted`），数据类型 `TINYINT(1)`（1=是，0=否）；但 **POJO 布尔属性不加 `is` 前缀**（避免序列化/反序列化问题）。
3. 主键索引 `pk_字段`、唯一索引 `uk_字段`、普通索引 `idx_字段`。
4. 小数用 `DECIMAL`，**禁止** `FLOAT` / `DOUBLE`（精度丢失）。
5. 字符串长度相近用 `CHAR`；`VARCHAR` 不超过 5000，超过用 `TEXT` + 独立表。
6. 表必备三字段：`id`（BIGINT 自增主键）、`create_time`、`update_time`（均 `DATETIME`）。
7. 统一逻辑删除字段：`deleted INT DEFAULT 0`（1=已删，0=未删）；**不物理删除**。
8. 查询务必指明字段，**禁止 `SELECT *`**；`COUNT` 用 `COUNT(*)`；`IN` 集合元素控制在 1000 内。

## Java 命名与 OOP 速查

- 类名 `UpperCamelCase`（DO/BO/DTO/VO/DAO/PO 等例外）；方法/变量 `lowerCamelCase`；常量全大写下划线。
- 代码命名禁用以 `_` / `$` 开头结尾；抽象类 `Abstract/Base` 开头，异常 `Exception` 结尾。
- **禁止魔法值**（未经定义的字面量常量直接出现在代码里）；`long` 初值用大写 `L`。
- 重写 `equals` 必须重写 `hashCode`；包装类（Integer 等）比较一律用 `equals`，不用 `==`。
- 用 `Objects.equals(a, b)` 避免 NPE。
- POJO 类必须重写 `toString()`；类、属性、方法必须有 Javadoc（`/** */`，getter/setter 除外）。

## 并发与异常速查

- 线程池**禁止**用 `Executors` 创建，必须用 `ThreadPoolExecutor` 显式指定参数。
- `SimpleDateFormat` 线程不安全，用 `ThreadLocal` 包装或 `DateTimeFormatter`。
- **禁止**捕获 `Exception`/`RuntimeException` 后空处理（吞异常）；`finally` 块内不写 `return`。
- 异常用于错误处理，**不用于流程控制**。

## 应用方式（面向 WorkBuddy）

1. 写 `schema.sql` / 建表前，先对照"数据库规约"与"禁用外键"规则自检：无 `FOREIGN KEY`、逻辑外键列有索引、命名小写、必备三字段、逻辑删除 `deleted`。
2. 写实体类时，布尔属性去掉 `is` 前缀、字段名与表列 `map-underscore-to-camel-case` 对齐。
3. 写 SQL / Mapper 时，列名显式列出、不用 `SELECT *`、用 `COUNT(*)`。
4. 评审代码时，按命名、魔法值、异常吞掉、线程池、`equals`/`==` 逐项检查并给出整改建议。

## 参考

- 详细规则全集：`references/alibaba_mandatory_rules.md`（按模块：命名、常量、OOP、并发、数据库、工程结构、异常日志、单元测试）。
