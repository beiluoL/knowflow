# Java + AI 面试知识库 V9.1（java-ai-interview）

统一工程：6 大领域 24 正式模块、14 域浏览视图、批次制生产、每篇落地 18 项教学能力（V4.1 契约）。V2 素材库（`java-ai-interview-v2/`，只读保留）已完整盘点并安全迁入待审核区。目标：把「Java 后端 + AI 工程」面试考点写成**可复现、可追源、可复述**的完整教材——不是提纲，不是八股文拼接，不靠文件数量充数。V6.1 起以 [CHAPTER_PRODUCTION_PLAYBOOK](curriculum/CHAPTER_PRODUCTION_PLAYBOOK.md) 为生产规范，[原子类章节](chapters/java/atomic-classes.md) 为质量样板。

## 开始阅读

0. [课程总纲 MASTER_SYLLABUS](curriculum/MASTER_SYLLABUS.md) — 6 领域 24 模块 302 章完整目录（V6.0 规划宪法）。
1. [内容规范 V4.1](curriculum/CONTENT_STANDARD-V4.1.md) — 18 项教学能力契约与九状态机（V4.0 规范保留备查）。
2. [课程映射](curriculum/CURRICULUM_MAPPING.md) — 6 领域 24 正式模块 × 14 域浏览视图。
3. [知识点总目录](curriculum/TOPIC_CATALOG.md) — topic_id 总索引（人读版；机器版 `topics.json`）。
4. [知识地图](knowledge-map.md) — 14 域结构、依赖、完成状态与优先级。
5. [模块清单](modules.md) — 目录职责与批次计划。
6. [真实进度](99-maintenance/PROGRESS.md)｜[技术审计](99-maintenance/TECHNICAL_AUDIT.md)｜[问题登记](99-maintenance/ISSUE_REGISTER.md)。
7. [合并工程文档](99-maintenance/merge/MERGE_REPORT.md) — V2 合并的结果与统计。

## 历史正式章节（Batch 1–5 共 16 篇；当前生产清单见下方）

| 章节 | topic_id | 模块 | 优先级 | 来源 | 文件 |
|---|---|---|---|---|---|
| JMM 与 happens-before | JAVA-CONCURRENCY-JMM | M03 | P0 | V4.1 新写 | [chapters/java/jmm-happens-before.md](chapters/java/jmm-happens-before.md) |
| synchronized 与 Monitor | JAVA-CONCURRENCY-SYNCHRONIZED | M03 | P0 | V4.1 新写 | [chapters/java/synchronized-monitor.md](chapters/java/synchronized-monitor.md) |
| AOP 代理与自调用 | SPRING-AOP-SELF-INVOCATION | M06 | P0 | V4.1 新写 | [chapters/spring/aop-proxy-self-invocation.md](chapters/spring/aop-proxy-self-invocation.md) |
| HashMap | JAVA-COLLECTIONS-HASHMAP | M02 | P0 | **融合 V2** | [chapters/java/hashmap.md](chapters/java/hashmap.md) |
| MySQL MVCC | MYSQL-MVCC | M07 | P0 | **融合 V2** | [chapters/mysql/mvcc.md](chapters/mysql/mvcc.md) |
| volatile 与原子性 | JAVA-CONCURRENCY-VOLATILE-ATOMICITY | M03 | P0 | V4.1 新写 | [chapters/java/volatile-atomicity.md](chapters/java/volatile-atomicity.md) |
| CAS 与 ABA | JAVA-CONCURRENCY-CAS-ABA | M03 | P0 | V4.1 新写 | [chapters/java/cas-aba.md](chapters/java/cas-aba.md) |
| ConcurrentHashMap | JAVA-COLLECTIONS-CONCURRENT-HASHMAP | M02 | P0 | **融合 V2**（含 12 专题索引） | [chapters/java/concurrenthashmap.md](chapters/java/concurrenthashmap.md) |
| Java 原子类与底层实现 | JAVA-CONCURRENCY-ATOMIC-CLASSES | M03 | P0 | V4.1 新写（V6.1 样板） | [chapters/java/atomic-classes.md](chapters/java/atomic-classes.md) |
| equals、hashCode 与对象相等性 | JAVA-LANG-EQUALS-HASHCODE | M01 | P0 | V4.1 新写（Batch 4） | [chapters/java/equals-hashcode.md](chapters/java/equals-hashcode.md) |
| AQS 底层原理 | JAVA-CONCURRENCY-AQS | M03 | P0 | **融合 V2**（AQS17/Lock17 源码 + Condition/Park 实验） | [chapters/java/aqs.md](chapters/java/aqs.md) |
| RAG 整体架构 | RAG-ARCHITECTURE | M17 | P0 | **融合 V2**（三种事实分开纪律） | [chapters/rag/rag-architecture.md](chapters/rag/rag-architecture.md) |
| JDK 动态代理与 SPI | JAVA-LANG-DYNAMIC-PROXY-SPI | M01 | P0 | V4.1 新写（Batch 5） | [chapters/java/dynamic-proxy-spi.md](chapters/java/dynamic-proxy-spi.md) |
| ArrayList | JAVA-COLLECTIONS-ARRAYLIST | M02 | P0 | V4.1 新写（Batch 5） | [chapters/java/arraylist.md](chapters/java/arraylist.md) |
| LinkedList | JAVA-COLLECTIONS-LINKEDLIST | M02 | P1 | V4.1 新写（Batch 5） | [chapters/java/linkedlist.md](chapters/java/linkedlist.md) |
| String/StringBuilder/StringBuffer | JAVA-LANG-STRING | M01 | P1 | V4.1 新写（Batch 5） | [chapters/java/string.md](chapters/java/string.md) |

## 历史验证工程与证据（最新全量见生产报告）

- [examples/batch1-verify](examples/batch1-verify/README.md) — 隔离 Maven 工程（Spring Boot 3.2.5 + H2 + JUnit 5），20/20 测试通过。
- [examples/atomic-quota-verify](examples/atomic-quota-verify/README.md) — M03-06 样板验证工程（配额 CAS + LongAdder 统计），26/26 测试通过。
- [examples/equals-hashcode-verify](examples/equals-hashcode-verify/README.md) — Batch 4 equals/hashCode 契约验证工程，10/10 测试通过。
- [examples/aqs-verify](examples/aqs-verify/README.md) — Batch 4 AQS 验证工程（park 时序 / Condition / 有界槽位），13/13 测试通过。
- [examples/rag-pipeline-verify](examples/rag-pipeline-verify/README.md) — Batch 4 RAG 管线验证工程（切分/过滤/检索/引用/拒答），17/17 测试通过。
- [examples/dynamic-proxy-verify](examples/dynamic-proxy-verify/README.md) — Batch 5 JDK 动态代理与 SPI 验证工程（InvocationHandler 转发/透传/SPI 发现/递归），4/4 测试通过。
- [examples/arraylist-verify](examples/arraylist-verify/README.md) — Batch 5 ArrayList 验证工程（1.5x 扩容反射/预分配/fail-fast/subList 视图），6/6 测试通过。
- [examples/linkedlist-verify](examples/linkedlist-verify/README.md) — Batch 5 LinkedList 验证工程（头尾/非 RandomAccess/双向迭代/栈语义），8/8 测试通过。
- [examples/string-verify](examples/string-verify/README.md) — Batch 5 String 验证工程（不可变/常量池/==vs equals/循环拼接），9/9 测试通过。
- `99-maintenance/evidence/batch-1/`（9 文件）、`batch-2/`（3 文件）、`batch-3/`（2 文件）、`batch-4/`（maven-equals-hashcode / maven-aqs / maven-rag / evidence-rag-v2 / evidence-aqs-v2-experiments / evidence-integercache-prop / 11 个 JDK 17 源码提取件）— 原始证据：环境、字节码、JOL、并发实验、Maven 实测、融合实验复跑、JDK 17 源码逐行核对。
- `90-legacy-review/v2/` — V2 待审核素材区（124 文件，来源映射见其 PROVENANCE.md）；**不计入正式完成数**。

## 规划体系（V6.0，2026-10-16）

- **正式课程目录**：6 领域 / 24 模块（M01-M24）/ **302 章规划**；每章一个稳定 topic_id。
- **模块规划**：`curriculum/modules/MXX-*/`（README/CHAPTERS/SOURCE_PLAN/INTERVIEW_PLAN/PRACTICE_PLAN/DEPENDENCIES，24 模块 × 6 文件）。
- **章节骨架**：`curriculum/chapter-stubs/MXX/`（**286 个 planned 骨架**，只做规划不写正文，不计完成率）。
- **生产顺序**：按 COVERAGE_MATRIX 的「下一批建议」与依赖图取题；已有正文/V2 素材优先融合，不重复生产。

## 工程约定

- 每批最多 3 个知识点；优先级服从课程规划与学习依赖；技术审查优先于生成速度。
- 源码版本固定：OpenJDK 17.0.14+7 / Spring Boot 3.2.x / MySQL 8.4，禁写「最新版」。
- 事实三分：已实测（附证据）/ 源码定位（附路径版本）/ 工程假设（显式标注）；未验证一律 ⚠️，禁止虚构 PASS。
- 九状态机：planned → … → approved；**AI 不得自封 approved**；待审核区内容不得冒充正式教材。
- V2 目录（`../java-ai-interview-v2/`）只读保留：不删除、不清空、不覆盖，用户明确允许前不动。
- 运行门禁：`python3 tools/quality_check.py --write-manifest`。

<!-- PRODUCTION:BEGIN -->
## 当前磁盘生产统计

正式正文 **210/295**；章节位 **302**。旧段落为历史批次快照，当前统计以 [生产面板](99-maintenance/production/PRODUCTION_DASHBOARD.md) 为准。
<!-- PRODUCTION:END -->
