# 章节生产批处理计划（Batch Plan）

> 自动生成于 Batch 5 启动。把 `syllabus_data.py` 中 **283 条待撰写**条目（planned+v2）编排成可机械执行的批次队列。

> 流程：写 `chapters/<domain>/<slug>.md`（V4.1 二十节+11 项 frontmatter）→ 建 `examples/<x>-verify` 并跑绿 → `tools/add_chapter.py` 登记 → `quality_check.py --write-manifest` 归零 FAIL → 本地 commit。

## 进度总览

- 大纲总条目 302｜已正式 12｜**待撰写 283**｜merged 7
- 优先级（待撰）：P0=82，P1=161，P2=40
- 难度（待撰）：L1=2，L2=39，L3=163，L4=74，L5=5
- 需源码精讲 163｜需实战 185

## 批次定义

### Batch 5（本批 4 篇）—— Java 语言/集合基础，均可建 Maven 验证工程

| topic_id | 标题 | 模块 | 优先级 | 验证工程 |
|---|---|---|---|---|
| JAVA-LANG-DYNAMIC-PROXY-SPI | JDK 动态代理与 SPI | M01 | P0 | dynamic-proxy-verify |
| JAVA-COLLECTIONS-ARRAYLIST | ArrayList 底层结构、扩容与迭代 | M02 | P0 | arraylist-verify |
| JAVA-COLLECTIONS-LINKEDLIST | LinkedList 与链表实现 | M02 | P1 | linkedlist-verify |
| JAVA-LANG-STRING | String、StringBuilder、StringBuffer | M01 | P1 | string-verify |

### Batch 6+（机械队列，按 优先级→模块→序号 取前 N）

剩余 **279** 条。每批建议 4–6 篇，保证质量与可验证性。

| 模块 | 名称 | 待撰(P0/P1/P2) | dir |
|---|---|---|---|
| M01 | Java 语言基础 | P0=2/P1=6/P2=2 | M01-java-language |
| M02 | Java 集合框架 | P0=1/P1=3/P2=1 | M02-java-collections |
| M03 | Java 并发编程 | P0=6/P1=5/P2=0 | M03-java-concurrency |
| M04 | JVM 虚拟机 | P0=0/P1=10/P2=3 | M04-jvm |
| M05 | Java IO、NIO 与 Netty | P0=0/P1=5/P2=4 | M05-java-io-netty |
| M06 | Spring 与 Spring Boot | P0=7/P1=7/P2=1 | M06-spring |
| M07 | MySQL | P0=5/P1=6/P2=1 | M07-mysql |
| M08 | Redis | P0=2/P1=9/P2=2 | M08-redis |
| M09 | Elasticsearch | P0=0/P1=6/P2=2 | M09-elasticsearch |
| M10 | 分布式系统 | P0=4/P1=6/P2=2 | M10-distributed |
| M11 | Kafka | P0=6/P1=8/P2=0 | M11-kafka |
| M12 | RabbitMQ | P0=5/P1=5/P2=2 | M12-rabbitmq |
| M13 | 网络与微服务 | P0=1/P1=9/P2=2 | M13-network-microservices |
| M14 | 大模型基础 | P0=5/P1=6/P2=1 | M14-llm |
| M15 | Prompt 与推理工程 | P0=3/P1=6/P2=0 | M15-prompt-inference |
| M16 | Embedding 与向量检索 | P0=3/P1=8/P2=1 | M16-embedding-vector |
| M17 | RAG 系统 | P0=8/P1=7/P2=0 | M17-rag |
| M18 | Java + AI 应用开发 | P0=2/P1=9/P2=1 | M18-java-ai |
| M19 | Agent 与 MCP | P0=10/P1=5/P2=0 | M19-agent-mcp |
| M20 | NL2SQL 与多模态 | P0=3/P1=5/P2=3 | M20-nl2sql-multimodal |
| M21 | 微调、部署与推理 | P0=0/P1=4/P2=8 | M21-finetuning-deployment |
| M22 | AI 安全与可观测性 | P0=3/P1=9/P2=0 | M22-security-observability |
| M23 | 高并发与系统设计 | P0=3/P1=7/P2=2 | M23-system-design |
| M24 | 项目实战与面试表达 | P0=3/P1=10/P2=2 | M24-project-interview |

## 优先级队列（前 40，供下一批直接取用）

| # | topic_id | 标题 | 模块 | P | L |
|---|---|---|---|---|---|
| 1 | JAVA-LANG-GENERICS | 泛型、类型擦除与 PECS | M01 | P0 | L3 |
| 2 | JAVA-LANG-DYNAMIC-PROXY-SPI | JDK 动态代理与 SPI | M01 | P0 | L3 |
| 3 | JAVA-COLLECTIONS-ARRAYLIST | ArrayList 底层结构、扩容与迭代 | M02 | P0 | L3 |
| 4 | JAVA-CONCURRENCY-REENTRANTLOCK | ReentrantLock 公平/非公平锁 | M03 | P0 | L4 |
| 5 | JAVA-CONCURRENCY-THREAD-POOL-BASICS | ThreadPoolExecutor 核心参数 | M03 | P0 | L3 |
| 6 | JAVA-CONCURRENCY-THREAD-POOL-INTERNALS | 线程池 execute、ctl、Worker、getTask | M03 | P0 | L5 |
| 7 | JAVA-CONCURRENCY-THREAD-POOL-REJECTION | 线程池拒绝策略、关闭与容量规划 | M03 | P0 | L3 |
| 8 | JAVA-CONCURRENCY-THREADLOCAL | ThreadLocal 原理与内存泄漏 | M03 | P0 | L3 |
| 9 | JAVA-CONCURRENCY-DEADLOCK-DIAGNOSIS | 死锁、活锁、线程饥饿和排查 | M03 | P0 | L3 |
| 10 | SPRING-IOC-DI | Spring IoC 与依赖注入 | M06 | P0 | L3 |
| 11 | SPRING-BEAN-LIFECYCLE | Bean 生命周期与 BeanPostProcessor | M06 | P0 | L3 |
| 12 | SPRING-AOP-PROXY-CHAIN | Spring AOP、代理与拦截器链 | M06 | P0 | L4 |
| 13 | SPRING-TX-INTERNALS | @Transactional 底层实现 | M06 | P0 | L4 |
| 14 | SPRING-TX-PROPAGATION | 事务传播行为与回滚规则 | M06 | P0 | L3 |
| 15 | SPRING-TX-FAILURES | 事务失效、事务边界和异常 | M06 | P0 | L3 |
| 16 | BOOT-AUTOCONFIGURATION | Spring Boot 自动配置原理 | M06 | P0 | L3 |
| 17 | MYSQL-BPLUS-TREE | B+Tree 与聚簇索引 | M07 | P0 | L3 |
| 18 | MYSQL-INDEX-TYPES | 普通索引、联合索引、覆盖索引 | M07 | P0 | L3 |
| 19 | MYSQL-INDEX-OPTIMIZATION | 最左前缀、回表与索引下推 | M07 | P0 | L3 |
| 20 | MYSQL-EXPLAIN | EXPLAIN 与慢 SQL 优化 | M07 | P0 | L3 |
| 21 | MYSQL-LOCKS | 行锁、间隙锁与 Next-Key Lock | M07 | P0 | L4 |
| 22 | REDIS-CACHE-STORMS | 缓存穿透、击穿、雪崩 | M08 | P0 | L3 |
| 23 | REDIS-CACHE-CONSISTENCY | 缓存与 MySQL 数据一致性 | M08 | P0 | L3 |
| 24 | DIST-IDEMPOTENCY | 幂等设计与业务唯一标识 | M10 | P0 | L3 |
| 25 | DIST-TRANSACTION-BASICS | 分布式事务基础与最终一致性 | M10 | P0 | L3 |
| 26 | DIST-OUTBOX | Outbox 本地消息表 | M10 | P0 | L3 |
| 27 | DIST-RATE-LIMITING | 限流、熔断、降级 | M10 | P0 | L3 |
| 28 | KAFKA-TOPIC-PARTITION | Broker、Topic、Partition | M11 | P0 | L3 |
| 29 | KAFKA-OFFSET-GROUP | Offset 与消费组 | M11 | P0 | L3 |
| 30 | KAFKA-ACK-ISR | ACK、ISR、副本机制 | M11 | P0 | L4 |
| 31 | KAFKA-IDEMPOTENT-TX | Producer 幂等与事务 | M11 | P0 | L4 |
| 32 | KAFKA-DEDUPLICATION | 重复消费与数据库幂等 | M11 | P0 | L3 |
| 33 | KAFKA-MYSQL-CONSISTENCY | Kafka 与 MySQL 最终一致性 | M11 | P0 | L4 |
| 34 | RABBITMQ-ROUTING | Exchange、Queue、Binding、Routing Key | M12 | P0 | L3 |
| 35 | RABBITMQ-CONFIRM | Publisher Confirm 与 Return | M12 | P0 | L3 |
| 36 | RABBITMQ-DEDUPLICATION | 重复消费、Spring 事务与数据库幂等 | M12 | P0 | L4 |
| 37 | RABBITMQ-ACK | ACK、NACK、Reject、Requeue | M12 | P0 | L3 |
| 38 | RABBITMQ-SPRING-BOOT | Spring Boot + RabbitMQ 企业实战 | M12 | P0 | L4 |
| 39 | NET-TCP-HANDSHAKE | TCP 三次握手、四次挥手 | M13 | P0 | L2 |
| 40 | LLM-TOKENIZER | Token、Tokenizer | M14 | P0 | L2 |

> 队列规则：P0>P1>P2；同优先级按模块号 M01→M24；同模块按序号。随批次完成动态递减。
