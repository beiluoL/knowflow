# 阿里巴巴 Java 开发手册 — 强制（强制）级规则全集

> 本文件为 `alibaba-code-standard` 技能的详细参考，按模块整理《阿里巴巴 Java 开发手册》中的**强制**级条目。用于代码评审、建表、写 SQL 时逐项核对。非强制（推荐/参考）条目不在此列。

## 一、命名风格

1. 代码中的命名不能以下划线 `_` 或美元符号 `$` 开始或结束。
2. 类名使用 `UpperCamelCase` 风格（DO / BO / DTO / VO / AO / PO / UID 等例外）。
3. 方法名、参数名、成员变量、局部变量统一使用 `lowerCamelCase` 风格。
4. 常量命名全部大写，单词间用下划线隔开，语义完整，不要嫌长。
5. 抽象类命名以 `Abstract` 或 `Base` 开头；异常类命名以 `Exception` 结尾；测试类以 `Test` 结尾。
6. 类型与中括号紧挨：`List<String> list`，禁止 `List list` 这种 raw type 定义。
7. 包名统一小写，点分隔符之间只有一个自然语义单词，不使用复数。

## 二、常量定义

1. **不允许任何魔法值（未经预先定义的常量）直接出现在代码中。**
2. `long` 或 `Long` 初始赋值时，使用大写的 `L`，小写的 `l` 容易与数字 `1` 混淆。

## 三、代码格式

1. 大括号内为空，简洁写成 `{}`，不需要换行与空格。
2. `if / for / while / switch / do` 等保留字与括号之间都必须加空格。
3. 任何二目、三目运算符的左右两边都需要加一个空格。
4. 采用 4 个空格缩进，禁止使用 Tab（不同系统 Tab 宽度不一致）。
5. 注释的双斜线 `//` 与注释内容之间有一个空格。

## 四、OOP 规约

1. 避免通过实例访问静态变量或静态方法，用类名访问。
2. 覆写 `equals` 方法时，必须同时覆写 `hashCode` 方法（如对象作为 `Set`/`Map` 的键）。
3. 所有整型包装类对象之间值的比较，全部使用 `equals` 方法（Integer 缓存区间 -128~127）。
4. 任何 `POJO` 类（DO / DTO / BO / VO / AO）都必须重写 `toString()` 方法（便于排查问题，排除敏感字段）。
5. 类、类属性、类方法必须使用 Javadoc（`/** */`），注释内容包含功能说明、参数、返回值、异常，除 getter/setter 外。
6. 使用 `java.util.Objects#equals`（JDK7 引入）做相等判断，避免 NPE。
7. 定义 DO 类（数据对象）时，禁止属性类型使用基本数据类型（如 `int`），必须用包装类型（如 `Integer`），避免默认值 0 造成的歧义（尤其配合数据库 `NULL`）。

## 五、集合处理

1. 只要重写 `equals`，就必须重写 `hashCode`（`Set`/`Map` 正确去重与查找）。
2. `ArrayList` 的 `subList` 结果是原 list 的视图，对 subList 的写操作会影响原 list；对原 list 的结构性修改会使 subList 遍历/增删抛出 `ConcurrentModificationException`。
3. 使用工具类 `Arrays.asList()` 把数组转集合时，不能使用其修改集合相关方法（add/remove/clear 会抛 `UnsupportedOperationException`），因为返回的是 `Arrays` 内部类，非 `java.util.ArrayList`。
4. 泛型通配符 `<? extends T>` 来接收返回数据，`<? super T>` 下限通配符用于写入对象，频繁往外读取用 `extends`，频繁插入用 `super`（PECS 原则）。
5. 不要在 `foreach` 循环里进行元素的 `remove`/`add` 操作；`remove` 必须使用 `Iterator`，且并发 iter 的 `remove` 若线程安全则使用 `Iterator.remove`。

## 六、并发处理

1. 获取单例对象（尤其被多线程共享的）需要保证线程安全。
2. **线程池不允许使用 `Executors` 去创建**（FixedThreadPool / SingleThreadPool 队列无界易 OOM，CachedThreadPool 线程数无界易 OOM），必须通过 `ThreadPoolExecutor` 的构造函数显式指定参数。
3. `SimpleDateFormat` 是线程不安全的，不要定义为 `static` 变量；若共享使用，加 `ThreadLocal` 或用 `DateTimeFormatter` 替代。
4. 高并发时，同步调用应考虑锁的性能损耗；能用无锁数据结构、读写锁、分段锁尽量用。
5. 并发修改同一记录时，避免更新丢失，需要加锁（应用层 `synchronized` / 数据库 `SELECT ... FOR UPDATE` / 乐观锁 `version` 字段）。
6. 多线程并行处理定时任务时，注意 `Timer` 运行多个 `TimeTask` 时只要一个异常未捕获其余任务即终止；推荐 `ScheduledExecutorService`。

## 七、控制语句

1. 在 `if / else / for / while / do` 语句中必须使用大括号，即使只有一行代码。
2. 在条件表达式中不准使用否定逻辑（如 `if (!(x >= 628))`），应写成 `if (x < 628)`。
3. 避免采用取反逻辑运算符，否则不利于快速理解。
4. 循环体中的语句要考量性能：定义对象、变量、获取数据库连接、try-catch 操作尽量移至循环体外处理。

## 八、数据库规约（MySQL）— 本项目重点

1. **【强制】表达是否与某概念时，字段命名 `is_xxx`，数据类型 `unsigned tinyint`（1 是，0 否）。但 POJO 布尔属性不得加 `is` 前缀**（避免框架反序列化报错）。
2. **【强制】表名、字段名必须使用小写字母或数字，禁止数字开头，禁止两个下划线中间只出现数字。**
3. **【强制】表名不使用复数名词。**
4. **【强制】禁用保留字**（如 `desc`、`range`、`match`、`delayed` 等），请参考 MySQL 官方保留字列表。
5. **【强制】主键索引名 `pk_字段`；唯一索引名 `uk_字段`；普通索引名 `idx_字段`。**
6. **【强制】小数类型为 `decimal`，禁止使用 `float` 和 `double`**（存在精度丢失，计量、金额尤其重要）。
7. **【强制】若存储的字符串长度几乎相等，使用 `char` 定长字符串；`varchar` 是可变长，`varchar` 长度不要超过 5000，否则建议用 `text` 并独立出一张表**。
8. **【强制】表必备三字段：`id`、`create_time`、`update_time`。`id` 为主键 `unsigned bigint` 自增（或 `BIGINT AUTO_INCREMENT`）；`create_time`/`update_time` 为 `DATETIME` 类型。**
9. **【强制】新增表不得使用外键与级联，一切外键概念必须在应用层解决。** 存量 interview 表已有 9 处物理外键，不擅自删除，需单独授权；详见技能 SKILL.md 与 `docs/engineering/BACKEND_JAVA_STANDARD.md`。
10. **【强制】逻辑外键（关联列，如 `user_id`、`category_id`）必须建立索引。**
11. **【强制】逻辑删除统一字段 `deleted INT DEFAULT 0`**（1=已删除，0=未删除），禁止物理 `DELETE`。
12. **【强制】查询时禁止 `SELECT *`，必须指明具体字段**（减少 IO、避免无效列返回、列变更兼容）。
13. **【强制】`count(*)` 统计行数，不要写 `count(列名)` 或 `count(常量)`**（`count(*)` 不忽略 NULL，`count(列名)` 忽略 NULL）。
14. **【强制】`in` 操作集合元素数量控制在 1000 以内**，超过分次查询。
15. **【强制】分页查询合理加上 `LIMIT`，避免一次性返回大量数据导致 OOM / 慢查询。**
16. **【强制】`varchar` 建索引必须指定索引长度**（过长会超 767 字节限制），建议前缀索引；`order by` 与 `group by` 走索引避免 `filesort`。
17. **【强制】不同字符集的表禁止做关联（如 `utf8` 与 `utf8mb4`），避免隐式转换导致索引失效。**

## 九、工程结构（分层规约）

1. **【强制】DO（Data Object）与数据库表结构一一对应，通过 DAO 层向上传输。** DTO（Data Transfer Object）用于服务层间传输；BO（Business Object）为业务对象；VO（View Object）为展示层对象；Query 为查询封装。
2. **【强制】分层领域模型命名规约**：数据对象 `xxxDO`，`xxx` 为表名；数据传输 `xxxDTO`；展示 `xxxVO`；POJO 是 DO/DTO/BO/VO 统称。
3. **【强制】Service / DAO 接口定义清晰，接口实现与接口分离。** 禁止在 Controller 直接写业务逻辑与数据库访问。
4. **【强制】`@Transactional` 事务不要滥用**，事务范围尽量小；只读查询不加事务或标注只读。
5. **【强制】禁止 `try-catch` 包裹大段代码仅打印日志不处理**，捕获异常的目的是为了处理，不是为了流程控制或吞掉。
6. **【强制】`finally` 块中不要使用 `return`**（会导致 `try` 中的 `return` 被覆盖）。

## 十、异常日志

1. **【强制】Java 类库中定义的继承自 `RuntimeException` 的异常（如 `IllegalArgumentException`）不应通过 `catch` 捕获后不做任何处理（不要吞异常）。**
2. **【强制】捕获异常是为了处理它**，不要用来做流程控制、条件控制。
3. **【强制】`finally` 块中不要使用 `return`。**
4. **【强制】捕获异常与抛异常必须完全匹配，或捕获异常是抛异常的父类。**
5. **【强制】日志打印禁止直接用 `System.out.println` / `System.err.println`**，使用日志框架（SLF4J + Logback）；禁止在日志中打印敏感信息（密码、token）。

## 十一、安全规约

1. **【强制】用户输入必须做参数校验与防注入**，禁止拼接 SQL（使用预编译 `#{}` / 参数化查询），禁止拼接 OS 命令。
2. **【强制】用户敏感数据（密码、身份证、手机号）存储或传输必须加密**，禁止明文落库。
3. **【强制】权限校验必须在后端做**（`@PreAuthorize` / 拦截器 / AOP），前端隐藏按钮不等于后端鉴权。
4. **【强制】对外暴露的接口必须做身份认证 / 频率限制**，防止越权、刷接口。
5. **【强制】JWT / Session 等凭证必须设置合理过期时间，密钥禁止硬编码在代码或提交到仓库**。

## 十二、单元测试

1. **【强制】好的单元测试必须遵守 AIR 原则**：Automatic（自动化）、Independent（独立）、Repeatable（可重复）。
2. **【强制】单元测试之间必须保持独立，不互相调用，不依赖执行的先后顺序。**
3. **【强制】对于单元测试，要保证测试粒度足够小，有助于精确定位问题；单一测试不应取代集成测试。**
4. **【强制】核心业务、核心应用、核心模块的增量代码必须编写单元测试。**
5. **【强制】数据库相关测试禁止依赖真实外部库强耦合**，建议用内存库（如 H2）或 mock。

---
> 来源：阿里巴巴集团《Java 开发手册》（泰山版 / 嵩山版 / 华山版）强制级条目整理。本文件仅摘录强制项；实施时结合手册原文与团队约定。
