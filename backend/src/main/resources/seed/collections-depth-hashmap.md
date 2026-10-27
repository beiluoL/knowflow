# HashMap：从键契约到源码决策

## 学习目标与完成标准
Java 17；进阶；重要度 S；建议学习 50 分钟（内容规划，非已学习时长）。闭卷解释 put/get/resize，运行两个实验并说明观察边界，回答八层追问后再自行标记完成。阅读资料不产生掌握证据。

## Map 契约与不可变键
Map 表达键到值的映射，一个等价键只有一个值。equals 相等要求 hashCode 相等，反过来不成立。散列用于缩小候选范围，equals 判断键身份。键入表后不得改变参与等价和散列的状态，否则查询的新散列与节点保存的旧散列不一致。优先使用不可变值对象；只覆盖 equals 不覆盖 hashCode 违反约定。

HashMap 允许一个 null 键和 null 值；get 返回 null 不能区分不存在和映射到 null，要用 containsKey。不保证遍历顺序；LinkedHashMap 提供明确顺序语义，TreeMap 按比较规则排序。

## 图解：从散列到桶
键 → hashCode → h xor (h >>> 16) → (n - 1) & hash → 桶 → hash/equals 比较。
table 是 Node 数组，桶中可能为空、链表或树。Node 保存 hash、key、value、next；TreeNode 额外维护树关系。混合高位帮助低位掩码利用更多信息，不消除冲突。

## 源码入口：put 与 get
OpenJDK 17 的 put 调用 putVal；首次需要桶数组时通过 resize 分配。空桶放入节点，非空桶比较首节点，再走树或链表分支。相同键替换值，新增键才增加 size；超过 threshold 扩容。get/getNode 先定位桶并检查首节点，再进入树查找或沿链表比较。不要把 get 的平均常数复杂度说成任何冲突分布都恒定。

## 扩容、负载因子与预估容量
默认负载因子 0.75，是空间和冲突概率的折中；threshold 通常由容量乘负载因子得到。容量取二次幂，构造时并不必然马上分配 table。resize 在常规扩容中翻倍；缓存 hash 与 oldCap 做按位与，结果为零留在旧下标，否则去旧下标加 oldCap。这是多了一位掩码，不是重新调用每个键的 hashCode；仍需遍历并重建桶连接。迭代成本与容量加元素数量有关，预估过大也有代价。

## 树化、退化与 8 / 6 / 64
TREEIFY_THRESHOLD=8、UNTREEIFY_THRESHOLD=6、MIN_TREEIFY_CAPACITY=64 是 OpenJDK 17 实现常量，不是 Map API 承诺。putVal 的链表计数和 treeifyBin 条件要一起看：不能断言“插入第八个节点必定树化”。桶容量小于64时优先扩容；扩容拆分树桶时小分支可能退为链表，删除时也存在基于结构的退化判定。8与6留出转换缓冲，树节点开销更大，普通散列下长桶罕见。相同散列且不可比较键并不适合绝对承诺所有操作严格 log n。

## 两个可运行实验
下面训练区提供 MutableKeyDemo 和 ResizeBitDemo 完整 Java 17 文件。先预测输出，再运行并记录原因。位拆分实验演示数学关系，不使用反射冒充观察内部 table。实验成功不是自动面试评分。

## 并发与 fail-fast
HashMap 非线程安全；并发结构修改可能造成丢失更新、可见性问题和不一致，不能把全部风险只归因于历史循环链表。JDK7头插迁移曾存在特定并发扩容环风险；JDK8改为分组迁移保序，但仍没有同步保障。fail-fast 根据 modCount 尽力检查，异常是诊断提示而非同步机制。单线程增强 for 中错误结构修改也可能触发它。

## ConcurrentHashMap 对照
本路径泛型与并发容器章节已有 ConcurrentHashMap 原子更新知识点，训练区返回真实关联。Java17 CHM 禁止 null 键和值；空桶CAS、非空桶局部同步、树桶及 ForwardingNode 支持并发操作与协作扩容。get 通常不获取桶锁，但不等于所有方法无锁。computeIfAbsent 的映射函数必须短小，不应递归更新同一映射或执行漫长IO；get+put 的复合业务并不自动原子。JDK7 Segment 是历史架构，不能当Java17主实现。

## 情境与取舍
缓存查不到值：先检查键是否可变、hash/equals契约、null语义，再检查同步和缓存淘汰，避免直接声称容器丢数据。CPU高：检验冲突分布、容量、遍历成本与无界增长，依据剖析证据选择容器与上限。需要并发计数优先使用CHM原子更新，要求多键事务则需更高层协议，不能把单键线程安全推成整体事务。

## 连续追问与总结
先解释结构，再解释散列和索引，再解释扩容位拆分，再讨论树化取舍，再回答并发失败，再落到容器选择。答案区区分30秒、90秒和深挖；先尝试口述再展开。总结：契约优先、源码有版本、实验有边界、并发依赖真实同步，勿背阈值代替解释。

## 官方来源与源码锚点
核验日期 2026-10-11；API Java SE 17；源码固定 OpenJDK jdk-17+35。正文为原创教学摘要，不复制源码整段。
- [HashMap Java17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html)：null、性能、同步与fail-fast契约。
- [Map Java17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html)：映射与键契约。
- [OpenJDK17 HashMap](https://github.com/openjdk/jdk/blob/jdk-17%2B35/src/java.base/share/classes/java/util/HashMap.java)：hash、putVal、getNode、resize、treeifyBin、TreeNode.split。
- [ConcurrentHashMap Java17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)：并发操作与computeIfAbsent约束。
