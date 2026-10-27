# HashMap：契约、源码与失效边界

版本 java-ai-content-rebuild-v1；Java SE 17 / OpenJDK jdk-17+35；核验 2026-10-11。此资料不是学习记录。

## 01. 两条阅读路线

面试冲刺：先回答“键插入后为什么查不到”，再读键契约、扩容与树化，闭卷讲述末尾三个标准回答，最后做题与实验。

深入学习：按契约 → 数据结构 → put/get → resize → 树化 → 并发 → 诊断顺序。前置：二进制按位运算、equals/hashCode、Java 内存模型。预计 60 分钟为内容规划，不代表已学习。

## 02. 契约比内部结构更重要

`Map<K,V>` 为等价键保存一个映射。`equals()` 相等要求 `hashCode()` 相同；同散列不意味着相等。桶定位只是缩小候选范围，仍需判等。

键入表后若改变等价字段，会使当前查询散列与节点保存的插入散列不一致。即使元素仍在迭代结果中，`get()` 也可能找不到。不可变 record 是一种解决方案，但其成员若引用可变对象，仍须审查成员等价逻辑。

`null` 键和值允许存在；`get()==null` 不足以判定缺失，使用 `containsKey()` 区分。遍历顺序无保证。需要插入顺序考虑 `LinkedHashMap`，排序考虑 `TreeMap`，不是靠观察一次 HashMap 输出。

## 03. Java17 源码阅读路径

### 3.1 定位与查找

`hash()` 将高 16 位混入低位；`(n-1)&hash` 选择二次幂数组桶。`Node` 保存 hash/key/value/next；`TreeNode` 保存树关系。扰动不消除冲突。

`get()` → `getNode()`：定位桶、比较首节点；随后树查找或沿链表比较 hash 和 equals。同散列不可比较键并不适合承诺所有树查找严格 `O(log n)`。

### 3.2 putVal 的决策

1. table 为空时通过 `resize()` 分配。
2. 空桶创建节点；非空桶先检查首节点，再进入树/链表分支。
3. 等价键更新 value，非新增节点不增加 size。
4. 新键增加 size、modCount，超过 threshold 触发扩容。

构造器设置容量目标并不等于立即分配桶数组。`putIfAbsent()` 在普通 HashMap 上不是并发原子协议。

## 04. 扩容不是重新调用全部 hashCode

容量通常翻倍，新增一位掩码。节点缓存 hash 与 `oldCap` 按位与：零留原桶，否则移到原桶加 oldCap。需要遍历重建连接，却不重新调用每个键的 `hashCode()`。

```java
int oldCap = 16;
int hash = 17;
int oldIndex = hash & (oldCap - 1);
int newIndex = hash & (oldCap * 2 - 1);
System.out.println(oldIndex + " -> " + newIndex); // 1 -> 17
```

负载因子默认 0.75 是时间/空间折中。容量预估考虑元素数、负载因子和取整；过大的稀疏表也有代价，遍历成本与容量加 size 相关。本实验是位拆分数学模型，不是假装观测内部 table。

## 05. 树化与退化必须看调用分支

| 条件 | 作用 | 不该说什么 |
| --- | --- | --- |
| TREEIFY_THRESHOLD=8 | 链表计数达到相应条件时调用树化判断 | 第八次插入一定变树 |
| MIN_TREEIFY_CAPACITY=64 | 小表长桶优先扩容 | 任意容量都先树化 |
| UNTREEIFY_THRESHOLD=6 | 扩容树桶拆分的小分支退化条件 | 删除到六个一定立即退化 |

`TreeNode.split()` 的数量判断与 `removeTreeNode()` 的结构判断不同。阈值属于固定源码版本，不是 Map API 承诺。树节点更重；树化缓解部分冲突风险，不能替代好散列、容量治理与合理键。

## 06. 并发与版本差异

HashMap 不提供同步契约。JDK7 的头插迁移历史环只是特定风险；Java17 迁移策略不同，仍不能并发写。不要用“没有环”“没有 CME”推导线程安全。

:::warning
fail-fast 是尽力诊断，不提供 happens-before。单线程错误地在增强 for 中结构修改也能触发 CME。同步包装的复合操作与迭代仍须遵循共同锁协议。
:::

## 07. 工程场景与排查

以下是教学场景，不是用户生产事故。缓存“丢键”先保存插入/查询的键字段、hashCode、containsKey、实际删除日志，排查键突变/null语义；再查并发、淘汰和生命周期。不能直接归咎“HashMap 随机丢数据”。

CPU 上升先用线程栈/剖析确认热点是否在桶遍历、equals、迭代或其他业务。冲突与无界增长是不同问题；换并发容器不自动解决容量上限。共享并发映射用 CHM，跨键不变量用更高层事务/锁协议。

## 08. 实验与主动回忆

配套 `HashContractRebuild` 用可变键复现查询失败，随后 immutable record 对照。先预测，再运行 Java17 文件。确定性输出见练习；运行不会自动写学习成绩。

闭卷解释：为什么迭代仍看到元素而 get 失败？为什么 resize 只看新增的一位？8/6/64 各在什么方法生效？预估过大为什么也慢？有哪些风险不是 CHM 能解决的？

## 09. 连续追问与标准回答

键身份 → hash 扰动 → putVal 判等 → 扩容分流 → 树化分支 → fail-fast → 并发选型 → 故障证据。对应题库有稳定身份与知识关联。

### 9.1 键契约

30秒：HashMap 先用散列定位桶，再用 equals 判等；等价键须同散列，键入表后不要改变等价字段。

60秒：Node 缓存插入散列。修改键可能改变当前 hashCode，查询定位不同桶，元素仍存在但找不到。用稳定不可变键；record 成员也要检查可变性。null 查询必须区分缺失和映射空值。

### 9.2 resize

30秒：二次幂容量翻倍增加一位掩码，旧节点按 hash & oldCap 分为原位置和加 oldCap 两组。

60秒：利用缓存 hash 避免重新调用键 hashCode，但仍分配数组、扫描旧桶并重建连接；树桶还会 split。预估容量权衡扩容次数和稀疏遍历成本，不能把“无需重算散列”说成“扩容免费”。

### 9.3 并发

30秒：HashMap 不支持共享并发写，fail-fast 也不是同步机制。

60秒：Java17 不再按 JDK7 迁移方式工作，但没有引入线程安全契约。需要共享单键原子更新可选 CHM compute/merge；跨键事务、值对象安全和容量限制必须另设计。深挖应区分 API 承诺、实现分支与实验观察。

## 10. 总结与官方证据

核验 2026-10-11；A级。API 适用 Java17，源码固定 jdk-17+35，JDK7只作历史对照，不作为当前机制。

- [HashMap API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html)：类说明、构造器、get、迭代性能和 fail-fast。
- [Map API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html)：键/映射契约与可变键警告。
- [OpenJDK17 HashMap](https://github.com/openjdk/jdk/blob/jdk-17%2B35/src/java.base/share/classes/java/util/HashMap.java)：hash、putVal、getNode、resize、treeifyBin、TreeNode.split/removeTreeNode。
- [CHM API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)：并发选型与原子方法边界。
