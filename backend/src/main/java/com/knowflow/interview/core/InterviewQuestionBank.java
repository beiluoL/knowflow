package com.knowflow.interview.core;

import java.util.List;

/**
 * 模拟面试预置题库（Phase 3H 基础模式数据源）。
 *
 * <p>内容为经整理的 Java + AI 岗位高频题，含参考要点与一层追问树；
 * 无 AI 时也能完成一场完整模拟面试。题库为代码内白名单，新增方向只能追加，不能由用户提交内容直接进入。
 */
public final class InterviewQuestionBank {

    private InterviewQuestionBank() {
    }

    /** 单道预置题（会整体快照进会话）。 */
    public record SeedQuestion(
            String id,
            String title,
            /** 参考要点（自评对照，不是标准答案）。 */
            List<String> referencePoints,
            /** 一层追问树（基础模式手工选择/全部展示）。 */
            List<String> followups,
            /** 自评量表锚点：1 分 / 3 分 / 5 分。 */
            Rubric rubric) {
    }

    public record Rubric(String level1, String level3, String level5) {
    }

    public record Track(String code, String name, String description, List<SeedQuestion> questions) {
    }

    private static Rubric rubric() {
        return new Rubric(
                "只能泛泛而谈或有事实错误，无法展开细节",
                "能讲清核心概念与基本使用，部分细节需要提示",
                "原理、源码/参数、实战踩坑与权衡都能主动展开");
    }

    private static SeedQuestion q(String id, String title, List<String> points, List<String> followups) {
        return new SeedQuestion(id, title, points, followups, rubric());
    }

    public static final List<Track> TRACKS = List.of(
            new Track("JAVA_BASIC", "Java 基础", "集合、异常、泛型、IO、新特性等 Java SE 核心。", List.of(
                    q("JAVA_BASIC-1", "HashMap 的底层结构是怎样的？JDK 8 为什么引入红黑树？",
                            List.of("数组+链表（JDK8 后链表长度≥8且数组容量≥64转红黑树，≤6退化为链表）",
                                    "put 流程：hash 扰动、寻址、树/链表插入、扩容",
                                    "负载因子 0.75 的时空权衡；扩容 2 倍与 rehash 优化",
                                    "线程不安全：并发 put 丢数据/JDK7 扩容环链（历史问题）"),
                            List.of("为什么容量必须是 2 的幂？", "hash 扰动函数解决什么问题？",
                                    "什么场景应该用 TreeMap/LinkedHashMap 替代？")),
                    q("JAVA_BASIC-2", "equals 与 hashCode 的契约是什么？只重写 equals 会怎样？",
                            List.of("equals 相等的对象 hashCode 必须相等；反之不要求",
                                    "HashMap/HashSet 依赖 hashCode 分桶，只重写 equals 会导致同逻辑对象落到不同桶",
                                    "实体放进基于哈希的集合后修改参与哈希的字段会失效",
                                    "Lombok @EqualsAndHashCode 与 IDE 生成的区别与风险"),
                            List.of("String 的 hashCode 怎么计算？", "如何正确实现不可变值对象？")),
                    q("JAVA_BASIC-3", "Java 的异常体系与异常处理最佳实践？",
                            List.of("Throwable → Error/Checked Exception/RuntimeException",
                                    "不要捕获 Throwable 后吞掉；catch 范围要具体",
                                    "try-with-resources 与 suppressed exception",
                                    "业务异常 vs 系统异常的分层；异常不用于正常控制流"),
                            List.of("受检异常为何被批评？Spring 为什么大多用非受检异常？",
                                    "finally 中 return 会发生什么？")))),

            new Track("JAVA_CONCURRENCY", "Java 并发", "JMM、锁、线程池、JUC 工具与并发设计。", List.of(
                    q("JAVA_CONCURRENCY-1", "synchronized 与 ReentrantLock 的区别与选型？",
                            List.of("synchronized 是关键字、JVM 层实现（偏向→轻量级→重量级锁升级，不可降级）",
                                    "ReentrantLock 是 AQS API：可中断、可超时、公平锁、多 Condition",
                                    "可重入语义一致；ReentrantLock 必须 finally unlock",
                                    "选型：简单同步用 synchronized；需要高级特性才用 ReentrantLock"),
                            List.of("锁升级为什么设计成不可逆？", "什么是锁消除与锁粗化？")),
                    q("JAVA_CONCURRENCY-2", "线程池核心参数有哪些？如何合理设置？",
                            List.of("corePoolSize/maxPoolSize/keepAlive/workQueue/threadFactory/rejectedHandler",
                                    "提交顺序：核心线程→队列→最大线程→拒绝策略",
                                    "CPU 密集型≈核数+1；IO 密集型按等待比放大；必须用有界队列",
                                    "拒绝策略四选与业务降级；为什么禁止 Executors 无界工厂",
                                    "线程池监控：活跃数/队列积压/拒绝次数"),
                            List.of("为什么阿里规约要求显式 new ThreadPoolExecutor？",
                                    "如何优雅关闭线程池（shutdown/awaitTermination）？")),
                    q("JAVA_CONCURRENCY-3", "volatile 解决什么问题？它能保证原子性吗？",
                            List.of("保证可见性（MESI + 内存屏障）与禁止重排序",
                                    "不保证 i++ 这类复合操作原子性",
                                    "happens-before：volatile 写先于后续读",
                                    "典型用法：状态开关、DCL 单例的实例字段"),
                            List.of("DCL 单例为什么实例必须 volatile？", "AtomicInteger 底层 CAS 如何工作？")))),

            new Track("JVM", "JVM", "内存模型、GC、类加载、调优与故障排查。", List.of(
                    q("JVM-1", "JVM 运行时内存区域如何划分？哪些会发生 OOM？",
                            List.of("堆/方法区(元空间)/虚拟机栈/本地方法栈/程序计数器/直接内存",
                                    "堆 OOM（对象无法回收）、栈 SO 与 OOM、元空间 OOM（类加载泄漏）、直接内存 OOM",
                                    "栈帧结构：局部变量表/操作数栈/动态链接/返回地址",
                                    "排查工具：jmap/jstat/HeapDump/MAT"),
                            List.of("为什么 JDK8 用元空间替换永久代？", "StringTable 在哪个区域？")),
                    q("JVM-2", "常见 GC 算法与收集器如何选择？",
                            List.of("标记-清除/复制/标记-整理/分代收集",
                                    "Serial/Parallel/CMS/G1/ZGC/Shenandoah 的定位",
                                    "G1：Region、可预测停顿、Mixed GC、大对象 Humongous",
                                    "调优目标：吞吐 vs 延迟；不要背参数，要会看 GC 日志定指标"),
                            List.of("什么是卡表与记忆集？", "Full GC 频繁如何一步步定位？")),
                    q("JVM-3", "描述一次类加载过程与双亲委派模型。",
                            List.of("加载→验证→准备→解析→初始化→使用→卸载",
                                    "双亲委派：自底向上检查、自顶向下加载；防止核心类被篡改",
                                    "打破场景：SPI（线程上下文类加载器）、OSGi、热部署",
                                    "Tomcat 的多 WebApp 类加载器结构"),
                            List.of("ClassNotFound 与 NoClassDefFoundError 的区别？",
                                    "准备阶段与初始化阶段静态变量分别是什么值？")))),

            new Track("FRAMEWORK_STORAGE", "Spring / MySQL / Redis", "框架原理、数据库与缓存工程实践。", List.of(
                    q("FRAMEWORK_STORAGE-1", "Spring Bean 的生命周期与循环依赖如何解决？",
                            List.of("实例化→属性填充→初始化（Aware/BeanPostProcessor/@PostConstruct/initMethod）",
                                    "三级缓存：singletonObjects/earlySingletonObjects/singletonFactories",
                                    "AOP 代理在提前暴露时由 SmartInstantiationAwareBeanPostProcessor 生成",
                                    "构造器注入的循环依赖无法解决（设计上应避免）",
                                    "@Async/@Transactional 自调用失效的原因"),
                            List.of("为什么需要三级缓存而不是两级？", "原型 Bean 为什么没有循环依赖缓存？")),
                    q("FRAMEWORK_STORAGE-2", "MySQL 索引为什么用 B+ 树？什么情况索引失效？",
                            List.of("B+ 树矮胖、叶子有序链表、非叶子不存数据→单节点扇出大、IO 少",
                                    "聚簇索引 vs 二级索引与回表；覆盖索引/最左前缀",
                                    "失效：函数/隐式类型转换、前导模糊、不满足最左前缀、OR、选择性差被优化器放弃",
                                    "EXPLAIN 看 type/key/rows/Extra；索引不是越多越好"),
                            List.of("索引下推（ICP）解决什么？", "如何设计联合索引的列顺序？")),
                    q("FRAMEWORK_STORAGE-3", "缓存穿透、击穿、雪崩分别是什么？怎么防？",
                            List.of("穿透：查不存在的数据→缓存空值/布隆过滤器",
                                    "击穿：热点 key 过期→互斥重建/逻辑过期",
                                    "雪崩：大批 key 同时失效或 Redis 宕机→过期时间加随机值/多级缓存/限流降级",
                                    "缓存与数据库一致性：先更库后删缓存 + 延迟双删/Canal",
                                    "big key/热 key 的发现与拆分"),
                            List.of("为什么是删缓存而不是更新缓存？", "Redis 分布式锁有哪些坑（误删/超时/续约）？")))),

            new Track("RAG", "RAG 检索增强", "分块、嵌入、检索、重排、评测与工程化。", List.of(
                    q("RAG-1", "一个生产级 RAG 系统包含哪些环节？各环节常见失败模式？",
                            List.of("文档解析→清洗切分→嵌入→入库（向量+元数据）→检索→重排→拼装上下文→生成→引用/溯源",
                                    "失败：切分破坏语义、嵌入模型不匹配语种、检索召回低、上下文超窗、幻觉",
                                    "混合检索（向量+BM25 关键词）与 Rerank 的作用",
                                    "评测：召回率/命中率、答案忠实度、引用准确率；建立黄金问答集"),
                            List.of("chunk 大小如何权衡？父子分块/小到多大检索是什么？",
                                    "多租户文档怎么做权限过滤（不能只靠 prompt）？")),
                    q("RAG-2", "如何提升检索召回质量？",
                            List.of("查询改写/扩展（HyDE、多查询、同义词）",
                                    "元数据过滤与结构化检索结合",
                                    "多路召回 + RRF 融合 + Cross-Encoder 重排",
                                    "Embedding 微调与领域语料；向量索引参数（HNSW efSearch）",
                                    "用真实问题集做离线评测驱动迭代"),
                            List.of("向量维度越高效果一定越好吗？", "如何检测并剔除“看起来相关”的干扰块？")),
                    q("RAG-3", "RAG 与微调如何取舍？知识更新频繁怎么办？",
                            List.of("RAG 强在知识时效、可溯源、低成本更新；微调强在风格/格式/任务能力固化",
                                    "二者可组合：微调交互形式，RAG 提供事实",
                                    "知识更新：文档版本化、增量嵌入、删除向量（软删除+重建）",
                                    "答案与知识库版本绑定，避免新旧知识混用"),
                            List.of("如何回答“模型不知道但文档里有”的问题？", "引用链接 404 怎么治理？")))),

            new Track("AGENT_MCP", "Agent / MCP", "工具调用、规划、记忆、协议与可靠性。", List.of(
                    q("AGENT_MCP-1", "Function Calling / Tool Use 的完整链路与可靠性要点？",
                            List.of("模型输出结构化工具调用→服务端执行（不是模型执行）→结果回灌→继续推理",
                                    "工具 schema 设计：少而精、枚举约束、参数校验、幂等",
                                    "危险操作必须人审/确认；最小权限 token；审计日志",
                                    "失败处理：工具重试、参数自纠、超时与熔断、步数/成本上限",
                                    "结构化输出校验（JSON Schema/严格模式）与解析兜底"),
                            List.of("模型反复错误调用同一工具怎么办？", "并行工具调用如何设计？")),
                    q("AGENT_MCP-2", "MCP 解决了什么问题？核心概念有哪些？",
                            List.of("标准化模型客户端与工具/资源服务器的对接协议，避免 N×M 集成",
                                    "Host/Client/Server；Tools/Resources/Prompts 三类能力",
                                    "传输：stdio（本地）与 Streamable HTTP（远程）",
                                    "能力协商（capabilities）与 Sampling/Roots",
                                    "安全：用户同意机制、沙箱、远程服务器鉴权与 PWA 风险"),
                            List.of("MCP 与 OpenAPI/插件方案的关系？", "一个 MCP Server 如何做资源权限隔离？")),
                    q("AGENT_MCP-3", "如何让 Agent 在长任务中不“跑偏”？",
                            List.of("规划-执行分离；ReAct/Plan-and-Execute 等模式与适用场景",
                                    "短期/长期记忆分层；上下文压缩与关键状态外置",
                                    "确定性流程交给工作流，不确定环节才交给模型",
                                    "可观测：完整 trace、token 成本、每步输入输出回放",
                                    "终止条件与人工接管（HITL）"),
                            List.of("什么时候不该用 Agent？", "如何评估一个 Agent 任务的成功率？")))),

            new Track("JAVA_AI_SCENE", "Java + AI 项目实战", "Spring AI/LangChain4j、工程落地与架构权衡。", List.of(
                    q("JAVA_AI_SCENE-1", "在 Spring Boot 中集成 LLM 应用，你会怎么设计分层？",
                            List.of("Controller 不直接调模型；业务服务 → AI 能力 Facade → Provider Adapter",
                                    "超时/重试/退避/熔断/并发与预算隔离；失败分类驱动业务降级",
                                    "Prompt 模板与版本管理；密钥按租户隔离",
                                    "AI 是增强层：核心业务在无 AI 时必须可完成",
                                    "可观测：请求体（脱敏）、token、延迟、失败原因埋点"),
                            List.of("用户自带 Key 与系统 Key 如何选择与计费？",
                                    "如何在不重启的情况下切换供应商？")),
                    q("JAVA_AI_SCENE-2", "做一个 AI 代码点评功能，安全边界怎么划？",
                            List.of("AI 永远不执行用户代码；执行只走独立判题沙箱",
                                    "沙箱失败禁止回退宿主机直跑；AI 只读取编译/测试事实做解读",
                                    "AI 结论不得改写判题结果与运行指标，必须标注“AI 建议”",
                                    "提示注入防护：用户代码视为不可信数据，输出不授权敏感操作",
                                    "数据出境/代码隐私：脱敏、私有化模型选项"),
                            List.of("如何防止模型被代码里的恶意指令越狱？", "点评成本失控如何限流？")),
                    q("JAVA_AI_SCENE-3", "AI 生成内容（题目/文案）如何做质量治理？",
                            List.of("JSON Schema + 业务规则双重校验（题型/分类/知识点/答案一致性）",
                                    "语义去重指纹；算法题参考解必须真实可运行验证",
                                    "状态流：草稿→生成→校验→待人工审核→通过→发布，AI 不等于审核",
                                    "灰度与可回滚；生成内容审计与举报",
                                    "成本：缓存、小模型分流、批量生成"),
                            List.of("如何评估题库生成质量？", "人工审核资源不足时如何排队？")))),

            new Track("ALGORITHM", "算法题面试", "高频数据结构与算法的口述与手写。", List.of(
                    q("ALGORITHM-1", "两数之和有哪些解法？如何分析时空复杂度？",
                            List.of("暴力双层循环 O(n²)/O(1)",
                                    "哈希表一次遍历 O(n)/O(n)：边查边放",
                                    "能讲清复杂度推导与常数项；有序场景可双指针",
                                    "扩展：三数之和如何去重、重复元素处理"),
                            List.of("哈希冲突会不会让复杂度退化？", "返回所有解时复杂度如何变化？")),
                    q("ALGORITHM-2", "LRU 缓存如何用数据结构实现？",
                            List.of("HashMap + 双向链表：get/put 均 O(1)",
                                    "访问/更新移动到头部，容量淘汰尾部",
                                    "哨兵头尾节点简化边界；链表节点保存 key 才能反删 map",
                                    "Java 可直接用 LinkedHashMap（accessOrder）但要能手写",
                                    "扩展 LFU 的频次桶设计"),
                            List.of("并发场景怎么做（分段锁/读写锁）？", "为什么 Redis 的近似 LRU 不严格？")),
                    q("ALGORITHM-3", "口述快速排序/归并排序，并说明稳定性与适用场景。",
                            List.of("快排平均 O(nlogn) 最坏 O(n²)、原地、不稳定；随机/三数取中防退化",
                                    "归并稳定 O(nlogn) 需要 O(n) 辅助空间；适合链表/外部排序",
                                    "partition 边界与递归终止条件",
                                    "工程排序：TimSort（归并+插入混合）、对象排序要求稳定性",
                                    "TopK：快速选择 O(n) 平均 / 堆 O(nlogk)"),
                            List.of("为什么 JDK 对基本类型用双轴快排而对象用 TimSort？",
                                    "海量数据无法全部载入内存怎么排序？")))),

            // Kafka 事务不自动覆盖外部数据库；引导候选人基于真实故障窗口解释幂等。
            new Track("KAFKA", "Kafka 消息与幂等", "重复消费、外部数据库事务与 Offset 故障边界。", List.of(
                    q("KAFKA-1", "Kafka 消费者写入 Oracle 成功，但提交 Offset 前宕机，如何保证幂等？",
                            List.of("幂等消费：使用稳定业务键和数据库唯一约束", "事务原子性：去重记录与业务写入在同一数据库事务提交或回滚", "Redis 标记与 Oracle 写入跨系统不具备天然事务原子性", "处理成功后提交 Offset；宕机重放时识别已处理事件"),
                            List.of("如果 Redis 标记成功但 Oracle 事务回滚，事务原子性会出现什么问题？", "如何用数据库唯一约束实现幂等消费？")),
                    q("KAFKA-2", "Kafka 的事务和 Exactly Once 能否自动保证外部数据库只写一次？",
                            List.of("Kafka 事务可协调 Kafka 内的输出记录与消费 Offset", "外部数据库需要自行协调输出和消费位置，不能默认加入 Kafka 事务", "幂等消费需结合业务键、数据库事务和重复检测", "故障恢复应验证提交前后及重放边界"),
                            List.of("外部数据库已经提交但消费位置尚未保存时怎样恢复？", "如何验证幂等消费的重放边界？")),
                    q("KAFKA-3", "消费者发生 Rebalance 时，如何处理正在执行的业务写入与 Offset 提交？",
                            List.of("区分分区所有权与业务事务，撤销后避免继续处理旧分区", "Offset 提交只覆盖已完成处理的记录，不能越过仍在执行的记录", "重复消费仍需数据库幂等约束保护", "明确处理超时、失败重试和恢复观测"),
                            List.of("如果并行处理先完成后面的消息，Offset 提交应怎样确定？", "如何验证重复消费不会产生重复业务写入？"))))
    );
}
