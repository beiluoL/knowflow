# 真实进度 PROGRESS — java-ai-interview V4.1

> 本文件只记录真实发生的事。任何「计划中」显式标注候选，不计入完成数。正文写完 ≠ 审核通过；完成数以 MANIFEST 九状态机为准。

## 工程起点（2026-10-16）

- 接手时 `content-studio/java-ai-interview/` 为空目录，无任何工程文件。按用户指令从零初始化，不参考 `java-ai-interview-v2`。
- 同日完成两轮工作：**Batch 1 生产**（V4.0 规范 + 3 篇章节）与 **Batch 1 技术质量审计修复**（V4.1 规范 + 分层门禁 + 验证工程）。两轮均记录在案。

## Batch 1 生产轮（同日早些）

- 初始化：CONTENT_STANDARD-V4、README、knowledge-map（14 域 119 主题）、modules、quality_check.py v1。
- 产出 3 篇：jmm-happens-before（396→401 行）、synchronized-monitor（468→593 行）、aop-proxy-self-invocation（397→427 行）。
- 首轮门禁抓出 2 个代码块语言标注 FAIL，修复后 3/3 PASS。

## Batch 1 技术审计轮（同日，质量优先，停 Batch 2）

### 审计方式

- 逐篇技术事实审计（TECHNICAL_AUDIT.md，A/B/C/D 四组共 30+ 断言）。
- 真实运行全部可复现实验并归档证据（evidence/batch-1/，7 个证据文件）。
- 建隔离 Maven 验证工程（examples/batch1-verify），Spring Boot 3.2.5 + H2 + JUnit 5，**20/20 通过**。

### 缺陷与修复（详见 ISSUE_REGISTER，共 29 项）

- **P0 共 5 项，全部修复**：伪实测数据 2 项（jmm「20 次 3 次卡死」实为 20/20 全过；DCL/JOL 无证据）、业务逻辑错误 1 项（LocalRateLimiter 无时间窗口）、编译错误 1 项（ThreadPoolTaskExecutor.CallerRunsPolicy 不存在）、语义错误 1 项（自调用「脏行残留」断言不准确）。
- **P1 共 19 项，全部修复或补证**：JDK 版本表述（实测 JDK17 告警为 deprecated 非 removed）、面试话术虚构经历 3 处、Integer 缓存举例越界、@Async 默认线程池口径错误、AopContext 包名错误、事件非可靠消息边界等。
- **P2 共 5 项**：MANIFEST 九状态机、课程映射、测试查询字段误用、反例记录、性能口径。

### 关键实测发现（已回写章节）

1. **自调用 ≠ 内层无事务**：内层写入实际加入外层事务；外层 catch 时脏行随外层提交才是事故形态（原文档断言相反）。
2. **CallerRunsPolicy + shutdown = 静默丢弃**：不抛 TaskRejectedException、不执行（原文档断言抛异常）。
3. **VisibilityDemo 本机 20 轮未复现卡死**：如实记录「未复现 ≠ 安全」，改以 JMM 语义 + jcstress 方法学论证。
4. **无同步 check-and-deduct 真实丢失更新**：100 线程 × 扣 1、初始 100 → remaining=1（两次运行 1/7）。
5. **JOL 真实锁升级证据**：non-biasable → thin lock → 竞争后 fat lock（主线程阻塞 402ms）。

### 本轮新增工程文件

- `curriculum/CONTENT_STANDARD-V4.1.md`（18 项能力契约 + 九状态机 + Gate A-E 分层）。
- `curriculum/CURRICULUM_MAPPING.md`（6 领域 24 模块 ↔ 14 域 119 主题）。
- `99-maintenance/TECHNICAL_AUDIT.md`、`ISSUE_REGISTER.md`。
- `99-maintenance/evidence/batch-1/`（environment.md、compile-results.txt、5 个原始输出、maven-test-results.txt）。
- `examples/batch1-verify/`（Maven 验证工程 + README）。
- `tools/quality_check.py` 升级 V4.1（Gate A 语义映射 + Gate C 半自动 + 九状态机校验）。

### 门禁结果（V4.1）

- Gate A：3/3 FAIL=0（WARN：13/14 弱映射 3 篇共有，登记待修订）。
- Gate B/D：技术审计完成，P0/P1 全修复（TECHNICAL_AUDIT + ISSUE_REGISTER）。
- Gate C：验证工程 20/20，BUILD SUCCESS；三篇均达 code_verified。
- Gate E：证据 7 文件全部本机真实获取；历史无证据声称已改标。
- 三篇状态：pending_human_review（等人工审批，AI 未自封 approved）。

### 真实边界（未验证，不得对外声称已验证）

- HotSpot C++ 源码与 Spring 源码树本机不可达（网络受限），路径引用保留并标 ⚠️；mark word 布局以 JOL 解码为间接证据。
- 性能数字无 JMH 基准（教学量级）。
- 三篇「13 企业工程分析」「14 技术对比与选型」无专门节（V4.0 骨架遗留，WARN 登记）。
- MySQL/Redis/Kafka/RAG/LLM 等域章节未开始（Batch 2+）。

## 下一批待办（Batch 2 候选，未开始）

1. **volatile 与原子性**（M02, P0）— JMM 续篇，CAS 与原子类。
2. **IoC 与依赖注入**（M05, P0）— AOP 篇前置补充。
3. **CAS 与 ABA**（M02, P0）— AQS 前置，AtomicStampedReference。

修订待办（不占批次名额）：三篇补「企业工程分析」「技术对比与选型」专门节以完整落地 V4.1 契约；新章节按 V4.1 十八项能力直接生产。

## Batch 2 + V2 统一合并轮（2026-10-16 21:40-22:30）

### 扫描与盘点（真实数据）

- A 主库 49 文件 / B（V2）326 文件（md 74、json 113、img 42、log 29、py 22、java 20）；跨库字节级相同文件 0；V2 库内 37 组重复（reviews 历史快照）。
- 产出 merge/ 8 件套：INVENTORY_V4、INVENTORY_V2、DUPLICATE_REPORT、CONTENT_COMPARISON、MERGE_PLAN、MERGE_DECISIONS、SOURCE_PROVENANCE.json、MERGE_REPORT。

### 合并执行（非破坏性、幂等）

- 124 个 V2 素材文件 → `90-legacy-review/v2/`（copy2 幂等；V2 原件 326 文件 SHA-256 复核 0 改动）。
- 7 个素材主题待审核（CHM 12 专题计 1 主题子章节）；不迁 V2 快照/截图/日志/tools（PROVENANCE 记录）。

### 规范与索引升级（V5.0）

- CURRICULUM_MAPPING 重写为 6 领域 24 正式模块（M01-M24）；14 域降为浏览视图；topic_id 体系建立。
- 新增 TOPIC_CATALOG.md + topics.json（5 正式 + 4 待审核主题）。
- 门禁升级 V5.0：frontmatter 增 module/topic 校验、module 范围、topic_id 唯一、topics.json 一致性；门禁结果 **51 PASS / 0 FAIL / 0 WARN**，全库 58 链接 0 断链。

### Batch 1 修复（Phase 7）

- 三篇补「13 企业工程分析」「14 技术对比与选型」专门节（实质内容）；frontmatter 补 module/topic（M03/M03/M06）；13/14 弱映射 WARN 消除。

### Batch 2 两篇融合新章（pending_human_review）

- **JAVA-COLLECTIONS-HASHMAP**（M02，423+ 行）：融合 V2 源码定位（putVal~623/resize~675/treeifyBin~753）与 8/9 树化边界；两实验本机复跑成功（证据 batch-2）；补 Spring Boot 分层案例（⚠️ 未编译）。
- **MYSQL-MVCC**（M07，384 行）：融合 V2 ReadView 源码定位与字段名纠正；可见性模型本机 Python 实测；SQL 两会话实验维持 ⚠️ Not verified（本机 MySQL 5.7 未运行且版本不符，未连接任何数据库）；补 Spring Boot 乐观更新案例（⚠️ 未编译）。

### 当前状态

- 正式完成 5/119（全部 pending_human_review，等人工审批）；P0 完成 5/38；V2 待审核素材主题 7。
- 下一批候选：volatile 与原子性（M03）、CAS 与 ABA（M03）、IoC 与依赖注入（M06）；V2 已有素材主题（CHM/AQS/TPE/RAG）优先融合升级。
- 遗留：MVCC SQL 需可丢弃 MySQL 8.4；两处 Spring Boot 片段未建工程；性能无 JMH 基准。

## Batch 3（2026-10-16 22:00-22:40）：并发域补全 + CHM 融合

### 选题依据（从真实缺口）

- ISSUE_REGISTER open 项仅 B1-022/023/024（P1/P2 环境限制），**无未关闭 P0 技术错误**。
- 按 24 模块轮转与依赖：M03 缺 volatile/CAS（JMM 后继、AQS 前置）；CHM 是 V2 素材最全的未融合 P0（主文档 254L + 12 专题 + 4 实验 + CHM17 源码镜像 267KB）。

### 三篇产出（全部 pending_human_review，code_verified）

1. **volatile 与原子性**（M03，从零，~430 行）：volatile 两性质边界、CAS 循环、LongAdder 分散、配额 CAS 案例。实测：8 线程 × 10 万 volatile++ 三次各丢 31-42 万；AtomicInteger 三次全对；闸门 3/3。
2. **CAS 与 ABA**（M03，从零，~400 行）：CAS 三要素、自旋边界、ABA 形式化、版本号防线。实测：ABA 复现（裸引用 CAS 成功）与阻断（StampedReference 版本号拒绝）均为确定性断言。
3. **ConcurrentHashMap**（M02，**融合 V2**，~480 行）：put 三分支（CAS 占桶/桶锁/helpTransfer）、get 无锁、sizeCtl 多阶段编码、协作扩容、CounterCell。融合 V2 主文档 + 12 专题索引 + 4 实验复跑（stale replacement=false、get+put=1 vs compute=2、sizeCtl 16→32 全周期编码实测）。

### 索引与门禁

- topics.json 正式主题 8（CHM 从 legacy 升正式）；TOPIC_CATALOG/README/knowledge-map 同步；MERGE_DECISIONS 追加 D-09。
- 门禁 V5.0：**78 PASS / 0 FAIL / 0 WARN**；MANIFEST 重建（8 章节）。
- 证据：`evidence/batch-3/`（evidence-volatile-cas.txt、evidence-chm-4.txt）。

### 当前状态与下一批

- 正式完成 8/119，P0 完成 8/38；V2 待审核素材主题 3（AQS/TPE/RAG）。
- 下一批候选（按依赖与模块轮转）：**AQS 与 ReentrantLock**（M03，融合 V2 297L + AQS17/Lock17 镜像）、**ThreadPoolExecutor**（M03，融合 V2 233L + 4 实验）、**IoC 与依赖注入**（M06，从零，AOP 篇后继）。

## V6.0 课程体系规划轮（2026-10-16 22:45-23:20，只规划不产正文）

- 用户指令转向课程架构：先建完整知识树/章节体系/依赖/源码/实战/面试规划，暂停正文生产；不重写已有章节、不删 V2、不重复迁移、不跑 Maven、不生成教学代码、不 git。
- **Stage 1 全局规划**（curriculum/ 8 文档）：MASTER_SYLLABUS（6 领域 24 模块 302 章全树）、LEARNING_PATHS（5 路径）、DEPENDENCY_GRAPH（前置/关联/扩展三类 + 模块级 Mermaid）、SOURCE_STUDY_PLAN（41 源码专题，4 项已实测支撑、其余标待核实）、PRACTICE_PROJECT_PLAN（P01-P08 可复用企业案例）、INTERVIEW_BLUEPRINT（295 题范围 + 6 追问树）、COVERAGE_MATRIX（真实统计）、CURRICULUM_MAPPING（V6.0 指引）。
- **Stage 2 规划产物**：`curriculum/modules/` 24 模块 × 6 规划文件 = 144 个；`curriculum/chapter-stubs/` **287 个章节骨架**（planned 281 + v2 待融合 6；已有正文 8 章与并入 7 节只记录链接，不建副本）。
- **工具**：tools/syllabus_data.py（302 章结构化数据）+ tools/generate_planning.py（幂等生成器，重跑结果一致）。
- **门禁升级 V6.0**：quality_check.py 增加 planned 骨架检查（frontmatter/命名/topic_id 唯一/module 归属/链接 + 24 模块规划文件存在性），不做正文完整性检查；结果 **78 PASS / 0 FAIL / 0 WARN**；全库 **1009 链接 0 断链**。
- **架构一致性 18 项**：全过（6 域 24 模块、编号无冲突、前置链 ID 全部存在、stub 与数据表 287 一致、正式 8 篇未被覆盖、planned 不计完成率、无虚构验证）。
- **冲突消解**：CHM 4 节/MVCC 2 节/TPE 3 节按「一知识点一权威入口」并入（7 节并入 3 个权威入口）。
- **下一批生产建议**（COVERAGE_MATRIX）：① JAVA-CONCURRENCY-ATOMIC-CLASSES（M03-06 P0，从零可验证）；② JAVA-LANG-EQUALS-HASHCODE（M01-03 P0）；③ JAVA-CONCURRENCY-AQS（M03-07 P0，融合 V2）。

## V6.1 校准 + 样板生产轮（2026-10-16 23:20-24:00）

### Phase 1 课程数据校准（真实统计）

- 口径确认（syllabus_data 脚本统计）：**302 章节位 / 295 唯一 topic_id / 正式正文 8→9 / merged 7（并入 3 权威入口）/ v2 待融合 6 / planned 280**。
- 修复：TOPIC_CATALOG「正式章节（5）」→9 且队列移除已完成项、119→295；MASTER_SYLLABUS 统计漂移修正；README 版本名 V5.0→V6.1。
- 依赖校准（数据源 tools/syllabus_data.py）：17 个模块 prereq/related 重设——RabbitMQ 不再前置 Kafka、MySQL 不再是 Redis 必修前置、Java LLM API/Tool Calling 不前置完整 RAG、M24 无硬前置；AQS 移除 synchronized 硬前置（相关非底层）。DEPENDENCY_GRAPH mermaid 按新数据重建。

### Phase 2-5 M03-06 样板：JAVA-CONCURRENCY-ATOMIC-CLASSES

- 正文 chapters/java/atomic-classes.md（V4.1 十八项能力全落地；Mermaid 三流程；面试三版话术无虚构；五层总结含复习计划）。
- 源码核实：本机 JDK 17.0.14 lib/src.zip 逐行核对 AtomicInteger（**仍 Unsafe**，启动环依赖注释；incrementAndGet=getAndAddInt+1 单条 RMW 非 CAS 循环；CAS 循环在 updateAndGet）、AtomicReference（**用 VarHandle**）、LongAdder/Striped64（casBase→probe→longAccumulate；sum()「NOT an atomic snapshot」）、VarHandle 访问模式分级。证据 4 个源码提取件归档 batch-4。
- 验证工程 examples/atomic-quota-verify：AI 文档处理单实例配额（AtomicInteger CAS + ConcurrentHashMap remove 幂等 + finally 释放 + 429 语义）+ LongAdder 统计。**26/26 BUILD SUCCESS**。
- 开发期修复实录：并发测试度量错误（B1-034）、avgMs 断言算术错、章节链接深度错——全部修复复测，失败记录保留。

### Phase 6 规范与索引

- 新增 curriculum/CHAPTER_PRODUCTION_PLAYBOOK.md（12 步生产流程 + Gate A-E + 8 条 AI 踩坑实录）与 NEXT_BATCH_PLAN.md（Batch 4：equals/hashCode、AQS 融合、RAG 融合）。
- topics.json formal=9；TOPIC_CATALOG/README/knowledge-map/MASTER_SYLLABUS/MANIFEST 全部对齐；门禁 **88 PASS / 0 FAIL / 0 WARN**；**1025 链接 0 断链**。
- TECHNICAL_AUDIT 追加 12 条事实审计表；ISSUE_REGISTER 追加 B1-030~035。

### 当前状态

- 正式完成 9/295 topic（全 pending_human_review）；P0 完成 9/38；planned 骨架 280；V2 待融合 4 topic。
- 下一批：equals/hashCode（M01-03）→ AQS（M03-07 融合 V2）→ RAG 架构（M17-01 融合 V2）。

## Batch 4 生产轮（2026-10-17）

### 选题（NEXT_BATCH_PLAN，每批 ≤3）
- equals、hashCode 与对象相等性（M01-03，从零，纯 JDK 可本机验证）；
- AQS 底层架构（M03-07，融合 V2 + JDK17 源码镜像）；
- RAG 整体架构（M17-01，融合 V2，三种事实分开纪律）。

### 产出（V4.1 十八项能力全落地）
- chapters/java/equals-hashcode.md：JDK17 源码核实 Integer 缓存（AutoBoxCacheMax）/ record 自动生成 / String.hashCode 缓存 benign race；10/10 验证。
- chapters/java/aqs.md：融合 V2 aqs-reentrantlock.md + AQS17/Lock17 逐行（Node 接口化、acquire 七步、park 时序两分支、Condition 保存-释放-重获）；13/13 验证。
- chapters/rag/rag-architecture.md：融合 V2 rag-pipeline.md 的「三种事实分开」——两条链 / 权限+版本隔离 / Top-K / 引用拒绝 / 空证据拒答；17/17 验证。

### 验证工程（均 BUILD SUCCESS，OpenJDK 17.0.14 + Maven 3.8.3）
- examples/equals-hashcode-verify：Tests run 10/10。
- examples/aqs-verify：Tests run 13/13。
- examples/rag-pipeline-verify：Tests run 17/17（含逐行复现 V2 离线证据 5 行）。

### V2 融合与事实分级（RAG 章重点）
- 继承 V2 的「三种事实分开」：已执行 / 源文档陈述（KnowFlow 静态追踪，只读未重跑）/ 未验证（真实 Embedding API、向量库、LLM、SSE）。三层级互不替代，未验证一律 ⚠️，禁止把架构图当容量认领。
- V2 待审核素材主题由 3（AQS/TPE/RAG）降至 1（仅 TPE 仍在待审核区）；AQS 与 RAG 升为正式章节。V2 源文件 326 个保持只读未修改。

### 索引对齐
- tools/syllabus_data.py：M01-03 / M03-07 / M17-01 状态 planned/v2 → text。
- topics.json：formal 9 → 12（新增 3 topic，sha256 登记）；totals 同步。
- README / knowledge-map / MASTER_SYLLABUS / TOPIC_CATALOG / CURRICULUM_MAPPING 同步（正式章节 12；RAG 入 M17-01 已完成）。
- 门禁 quality_check.py：**12 篇正式章节，Gate A FAIL=0；topics.json 与章节一致**。

### 当前状态
- 正式完成 12/295 topic（全 pending_human_review）；P0 完成 12/38；planned 骨架 286；V2 待审核 1 topic（TPE）。
- 全部 pending_human_review，等人工审批；AI 未自封 approved。

## Batch 5 生产轮（2026-10-17 晚）：启动「278→正式」大工程

目标：把 `syllabus_data.py` 中 **283 条待撰写**（278 planned + 5 v2）逐步转为正式章节。本批为工程启动批——产出可复用基础设施 + 首批 4 篇可验证章节。

### 基础设施（可复用）
- `tools/add_chapter.py`：登记章节到 `topics.json` + 翻转 `syllabus_data.py` 状态（MANIFEST 由门禁 `--write-manifest` 自动重建）。
- `99-maintenance/BATCH_PLAN.md`：283 条待撰全量队列（按 优先级→模块→序号 机械排序），含 Batch 5 定义、模块分布、前 40 优先队列。

### 选题（4 篇，均可建 Maven 验证工程）
- `JAVA-LANG-DYNAMIC-PROXY-SPI`（M01/P0）→ `examples/dynamic-proxy-verify`，4/4。
- `JAVA-COLLECTIONS-ARRAYLIST`（M02/P0）→ `examples/arraylist-verify`，6/6（反射读 `elementData` 验 1.5x 扩容；`--add-opens` 突破模块封装）。
- `JAVA-COLLECTIONS-LINKEDLIST`（M02/P1）→ `examples/linkedlist-verify`，8/8。
- `JAVA-LANG-STRING`（M01/P1）→ `examples/string-verify`，9/9。

### 过程踩坑（已修）
- deep recursion 经 proxy 实际抛 `NoClassDefFoundError`（`UndeclaredThrowableException` 类初始化恰在栈耗尽时发生），非字面 `StackOverflowError` → 断言放宽到 `Error.class`。
- `ArrayList.elementData` 反射被 JDK 模块封装拦截（`InaccessibleObjectException`）→ surefire `argLine` 加 `--add-opens java.base/java.util=ALL-UNNAMED`。
- fail-fast 用例误删「最后返回元素」导致 `hasNext` 提前终止未抛 CME → 改为删首元素使迭代继续触发。

### 索引对齐
- `syllabus_data.py`：4 条 `planned → text`。
- `topics.json`：formal 12 → 16（sha256 登记 + evidence）。
- `README`：正式章节 12→16、新增 4 行与 4 验证工程 bullet。
- 门禁 `quality_check.py --write-manifest`：**16 篇正式章节，Gate A FAIL=0；topics.json 与章节一致**；MANIFEST 自动重建。

### 当前状态
- 正式完成 16/295 topic（全 pending_human_review）；planned 待撰 283→279；V2 待审核仍为 1 topic（TPE）。
- 全部 pending_human_review，等人工审批；AI 未自封 approved。

<!-- PRODUCTION:BEGIN -->
# 全库生产进度

更新时间：2026-10-23T23:00:01+08:00

章节位 302；独立 topic_id 295；正式正文 210；本轮新增 194；剩余 85。

新绑定正文哈希的结构通过 8；技术审核通过 8；代码验证 8；人工批准 0（不推断历史审批）。

既有正文的旧审核声明保留，但未复审的均列 needs_technical_review，不能以历史报告冒充本轮证据。

| 模块 | 章节位 | 独立主题 | 正文 | 剩余 |
|---|---|---|---|---|
| M01 | 11 | 11 | 11 | 0 |
| M02 | 12 | 7 | 7 | 0 |
| M03 | 17 | 17 | 17 | 0 |
| M04 | 13 | 13 | 12 | 1 |
| M05 | 9 | 9 | 7 | 2 |
| M06 | 16 | 16 | 15 | 1 |
| M07 | 15 | 13 | 11 | 2 |
| M08 | 13 | 13 | 3 | 10 |
| M09 | 8 | 8 | 2 | 6 |
| M10 | 12 | 12 | 4 | 8 |
| M11 | 14 | 14 | 7 | 7 |
| M12 | 12 | 12 | 5 | 7 |
| M13 | 12 | 12 | 1 | 11 |
| M14 | 12 | 12 | 11 | 1 |
| M15 | 9 | 9 | 9 | 0 |
| M16 | 12 | 12 | 12 | 0 |
| M17 | 16 | 16 | 16 | 0 |
| M18 | 12 | 12 | 7 | 5 |
| M19 | 15 | 15 | 15 | 0 |
| M20 | 11 | 11 | 11 | 0 |
| M21 | 12 | 12 | 11 | 1 |
| M22 | 12 | 12 | 7 | 5 |
| M23 | 12 | 12 | 6 | 6 |
| M24 | 15 | 15 | 3 | 12 |

下一项：SPRING-AI-ARCHITECTURE。没有后台无人值守执行已启用的声明。

<!-- PRODUCTION:END -->
