# ConcurrentHashMap：原子映射不等于业务事务

版本 java-ai-content-rebuild-v1；Java17 / OpenJDK jdk-17+35；核验 2026-10-11。

## 01. 问题与两种学习入口

共享缓存和计数必须同时处理并发映射、值对象生命周期与跨键不变量。冲刺先回答 get+put 反例、null 与 compute，再解释 putVal/迁移；深入阅读先理解 JMM/CAS/monitor 与 HashMap 桶模型，再沿固定版本源码追踪。保留同章前置泛型与 PECS 内容，不将它们当成 CHM 的内部机制。

## 02. API 契约与版本边界

Java7 Segment 是历史架构；Java17 主体是 Node 数组、桶链表/树和协作迁移。不能按旧 Segment 数量解释 concurrencyLevel；当前它是容量规划提示。

`get()` 通常不阻塞，与更新重叠。报告某键更新值的非 null 读取与该更新有 happens-before 关系；这不代表全表一致快照。迭代弱一致、不抛 CME，不等于固定时刻快照。并发变化时 size/isEmpty 适合监控估计，不适合业务额度判断。

## 03. putVal、CAS 与桶同步

1. 检查 key/value 非 null，spread 混合散列。
2. 未初始化则 initTable；空桶用 CAS 安装节点。
3. MOVED 节点说明迁移，调用 helpTransfer 并转向新表。
4. 非空桶对首节点 synchronized，再复查首节点仍有效，遍历链表或 TreeBin 更新。
5. 新增元素后 addCount 更新计数，并可能参与扩容。

CAS 不意味着所有路径无锁。不同键可能落同桶而竞争同一个 monitor；不是每个 key 一把锁。get 依赖发布/可见性与节点查找路径，不能以“没有 synchronized”解释全部内存安全。

## 04. sizeCtl、ForwardingNode 与协作扩容

初始化前 sizeCtl 可表达容量目标，初始化中 -1，正常运行表示扩容阈值；扩容时负值编码 generation stamp 与协作状态，不能概括成“负数就是线程数量”。

`transfer()` 通过 transferIndex 分配迁移区间。迁移后的旧桶放 ForwardingNode，携带 nextTable；get/find 可以转到新表，写线程可 helpTransfer。迁移桶仍有同步成本，不是 stop-the-world 全表替换，也不是没有竞争。

`TreeBin` 包装树桶并组织树结构访问；旋转需要考虑并发读者，不能把它当普通 TreeMap 塞进数组。CounterCell 与 baseCount 分散计数竞争；sumCount 是聚合统计，不是任意时刻的业务事务快照。

## 05. 原子复合方法

| 方法 | 语义 | 回调返回 null |
| --- | --- | --- |
| putIfAbsent | 当前无映射时安装值 | 不接受 null value |
| computeIfAbsent | 当前缺失时计算值 | 不建立映射 |
| compute | 根据当前值更新 | 删除/保持缺失 |
| merge | 缺失安装值，否则合并 | 删除现有映射 |

```java
ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();
counts.merge("request", 1, Integer::sum);
```

回调保持短小、避免阻塞 IO 与更新同 map 的其他映射；检测到递归更新可抛 IllegalStateException。null key/value 被拒绝，get 返回 null 明确表达本次没有映射；缓存空结果可用非 null 包装和显式过期策略。

:::warning
单键原子不等于外部系统副作用恰好执行一次。计算失败、移除、后续重试或重新缺失，都必须按生命周期设计幂等。
:::

## 06. 容器安全与 Value 安全

```java
// 错误：安装 List 安全，不保护随后 ArrayList.add
groups.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
```

可用并发 Value，或在 compute 中创建不可变新值并原子替换。不可变方案有复制成本；持有旧引用的读者读到旧版本是预期语义。跨键转账不能仅靠两次 compute 保证不变量，需共同锁/数据库事务。

频次统计可用 `LongAdder` 降低更新竞争；并发 sum 不是原子快照，不适合精确余额。AtomicInteger/AtomicLong 的 CAS 适合单值状态转换；性能优劣需真实负载测量。删除映射后旧 LongAdder 引用仍可递增，必须定义删除/重建生命周期。

## 07. 可复现工程实验

配套 `ChmAtomicRebuild` 用 CountDownLatch 控制两个线程都先读 0，再 put 1，结果稳定为 1；随后并发 merge，join 后为 2。这是丢更新反例，不是性能基准，也没有实际生产指标。

频次统计工程设计：限定 key 数量与生命周期、保留采样/聚合窗口、在稳定边界读取结果。CHM 不负责 TTL、容量上限、持久化或跨节点一致性。

## 08. 追问与标准回答

结构 → 空桶 CAS/非空桶同步 → 再校验桶头 → ForwardingNode → 协作迁移 → 计数竞争 → 复合原子 → Value/多键边界。

30秒结构回答：Java17 CHM 用 Node 桶，空桶 CAS、非空桶局部同步，迁移节点转向新表；不是 Java7 Segment，也不是全部无锁。

60秒结构回答：从 putVal 讲初始化、空桶安装、MOVED/helpTransfer、桶首同步与复查，最后 addCount。TreeBin 管理树桶并发访问，CounterCell 分散计数竞争。实现有版本，API 保证与内部优化应分开。

30秒原子回答：get 和 put 各自安全，但读取后写回可丢更新；同键更新使用 compute/merge。

60秒原子回答：用屏障构造两个线程同读0写1，对照 merge 得2。回调短小、避免递归和外部副作用；返回 null 的删除语义不同于存 null。不是跨键事务。

30秒安全回答：Map 保护映射，不自动保护可变 Value。

60秒安全回答：ArrayList.add 仍有并发问题，可选并发值或不可变快照替换；LongAdder 用于统计而不是精确余额，还要处理移除旧引用、容量和生命周期。深挖说明如何按业务不变量选锁粒度，而非背“线程安全”。

## 09. 主动回忆与练习

关闭正文解释 sizeCtl 三类状态；指出 TreeBin 与 TreeNode 的职责差异；设计缓存空结果；区分 computeIfAbsent 返回 null 与 CHM.put null；解释为什么不能用并发 size 当限额。

已有 Quiz/Flashcard/实验入口消费同章知识关联；答案展开不产生成绩。

## 10. 官方来源

A级；API Java17，源码固定 jdk-17+35；核验 2026-10-11。JDK7 对照仅历史。

- [CHM API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)：类说明、compute/merge、null、统计。
- [OpenJDK17 CHM](https://github.com/openjdk/jdk/blob/jdk-17%2B35/src/java.base/share/classes/java/util/concurrent/ConcurrentHashMap.java)：putVal、get、initTable、transfer、helpTransfer、sizeCtl、TreeBin、ForwardingNode、CounterCell。
- [LongAdder API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/LongAdder.html#sum())：sum 快照边界。
- [AtomicInteger API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html)：CAS 与更新方法。
