# 泛型、集合边界与 ConcurrentHashMap

## 学习目标
- 根据数据流设计 extends / super 接口，并解释类型擦除的边界
- 用单键原子操作修复丢失更新，说明为什么不能把它当作多键事务
- 区分映射安全、可变值安全和快照一致性，用实验验证而非凭感觉判断

难度：2；预计 35 分钟；面试重要度：S

从“编译器允许什么”走到“多个线程同时操作时保证什么”：以 Java 17 为基准，把泛型与并发集合的契约落到工程代码。

## 本章解决什么问题

上一章回答 HashMap 如何定位键。本章换两个视角：API 的类型约束，以及共享数据的原子边界。你要能解释三个看似矛盾的现象：`List<Integer>` 不能赋给 `List<Number>`；并发 Map 的 `get` 与 `put` 各自安全却会丢计数；值放进并发容器后，值内部的列表仍可能发生竞争。

它们不需要拆成新的 DAG 节点。本章是“集合使用边界”的完整单元；AQS、线程池的实现细节仍留给并发路径。

## 前置知识与数据流

先确认你能解释对象相等与散列契约、接口与实现、线程之间共享对象的含义。这里的“生产者”不是创建对象的线程，而是**给当前方法提供可读取元素的参数**；“消费者”是接收当前方法写入元素的参数。

:::tip 学习提示
先判断当前方法对参数的读写方向，再选择通配符。不要只看到“父类”“子类”就套 PECS。
:::

## 泛型：约束比语法更重要

### 为什么 List 不是协变的

假设允许 `List<Integer>` 赋给 `List<Number>`，后者就可以写入一个 `Double`，破坏前者“元素为 Integer”的约束。因此参数化 List 不协变。`List<? extends Number>` 表示一个具体但未知的 Number 子类型，读出可视为 Number，但不能安全加入任意 Integer 或 Double。`null` 是类型层面的例外；集合实现仍可能拒绝它。清空、移除元素不等于写入新元素，不能简单说 extends 列表“完全不可修改”。

### PECS 与同类型读写

```java
static <T> void copyInto(List<? extends T> source, List<? super T> target) {
    for (T item : source) target.add(item);
}
// List<Integer> -> List<Number> 或 List<Object>
```

| 参数 | 本方法能安全做什么 | 不能推断什么 |
| --- | --- | --- |
| `? extends Number` | 读取为 Number | 不能任意加入 Integer |
| `? super Integer` | 写入 Integer | 读取只能安全视作 Object |
| `List<T>` | 保留同一个类型变量的关系 | 不能当成任意类型 List |

既读又写、且输入输出需要同一类型关系时，具体类型参数常更清晰。PECS 是 API 设计准则，不是每个 List 参数都必须加通配符。上面的循环还要求 target 可添加；对不可变列表，类型正确也会抛 UnsupportedOperationException。

### 类型擦除不是“类型信息全部消失”

编译器对泛型做静态检查，再将类型变量擦除到边界（无界时为 Object），必要时插入强转与桥接方法。`List<String>` 和 `List<Integer>` 通常具有同一个运行时实现类，但不能因此互换。原始类型可绕开部分检查，产生 heap pollution，错误可能在读取强转时才暴露。

类文件可保留泛型签名供反射查询；这不等于每个对象携带完整实际类型参数。不能写 `new T()`，也不能用 `instanceof List<String>` 检查元素类型。边界处应消除 raw type，而不是捕获 ClassCastException 假装安全。

## ConcurrentHashMap：先讲契约，再看源码

### 单次安全，不代表组合安全

```java
// 错误示例：两个线程都可能读取 0，然后都写回 1。
counts.put(key, counts.getOrDefault(key, 0) + 1);

// 正确示例：把同键的读改写交给原子操作。
counts.merge(key, 1, Integer::sum);
```

这里“错误”指不能实现无丢失的并发计数，不是 put 本身损坏了 Map。`putIfAbsent` 解决条件插入；`computeIfAbsent` 解决缺失时初始化；`compute` 表达同键变换；`merge` 把新值合并到旧值。选择与你的业务语义一致的 API。

映射函数返回 null 可以不建立映射或删除映射（取决于具体方法），这与“存入 null 值”不是一回事。回调应短小，不在其中做慢网络请求或递归更新同一映射；不要把外部副作用的 exactly-once 承诺绑定到回调。

### 为什么禁止 null

Map 查询会与其他线程更新重叠，null 可清晰表示本次查询没有得到映射值。`containsKey` 再 `get` 也不是一个不可分割的事务。需要表达“已缓存的空结果”时，使用明确的非 null 哨兵或包装类型，并定义 TTL、容量和失效策略，不把 null 塞入 CHM。

### OpenJDK 17 的源码路线

固定参考 `jdk-17+35`，不是逐行背源码。入口是 `putVal` / `get`，核心是 Node 数组、链/树桶；空桶插入用 CAS，非空桶更新有桶级同步和重新检查，迁移节点引导访问新表并协助扩容。读取不通过锁住全表来排斥所有更新。

面试表达必须分清 API 保证与实现：Java 7 的 Segment 不能直接套用到 Java 17；“CHM 所有方法都是无锁的”“每个键有一把锁”都不准确——冲突键可落在同桶。`size` 与弱一致遍历不是正在变化的全表强一致快照，不适合拿来守护业务额度不变量。

## Java 17 实验：预测，再验证

训练区提供完整 `GenericsBoundaryDemo.java`，可复制到可信本地环境执行，不在已登录网页中运行任意源码。

```java
var from = List.of(1, 2, 3);
var to = new ArrayList<Number>();
copyInto(from, to);
System.out.println(to); // [1, 2, 3]
```

计数实验用两个线程与屏障让它们先读到同一个旧值，再分别 put。这样稳定演示结果为 1，不依赖“多跑几次碰到竞争”。对照组每个线程 merge 一次，等待 join 后结果为 2。最后展示拒绝 null、擦除后的实现类，以及值对象自己的同步边界。

先写下四个预测：复制输出、错误计数、正确计数、null 的异常类型。运行后说明实验能证明什么、不能证明什么。它不是吞吐量基准，也不证明生产性能。

## 工程场景：频次统计与共享值

```java
ConcurrentHashMap<String, LongAdder> frequencies = new ConcurrentHashMap<>();
frequencies.computeIfAbsent("java", k -> new LongAdder()).increment();
```

Map 保证关联操作，LongAdder 负责值内部的并发累加。并发更新中的 sum 不是额度扣减需要的原子快照；若同时删除映射，某个线程仍可能递增它手中的旧 LongAdder。必须定义映射的生命周期，不只考虑 value 的数据类型。

若值是 ArrayList，Map 不会替 list.add 加锁。选择不可变值＋同键原子替换，或受同步保护的值对象；根据读取/写入和生命周期约束设计。两个账户跨键转账则要维护共同不变量，需要统一锁协议或数据库事务等方案，不能只换成 CHM。

## 常见误区与纠正

- **错误：** extends 什么都不能改。**纠正：** 无法安全写入任意非 null 子类型；移除与写入不是一件事。
- **错误：** 类型擦除让所有 List 都能互换。**纠正：** 编译期约束仍在，raw type 只是绕开检查。
- **错误：** get＋put 原子。**纠正：** 单次方法安全不等于组合原子。
- **错误：** computeIfAbsent 的返回对象自动线程安全。**纠正：** 映射与值的内部状态是两个边界。
- **错误：** Java 17 还是 Segment，或所有操作完全无锁。**纠正：** 使用对应版本源码，避免绝对化结论。
- **错误：** CHM 保证多键事务/遍历快照。**纠正：** 先明确不变量与观察一致性。

## 面试追问：从结论走到边界

先用 30 秒讲“结论＋原因＋一个反例”，再用 90 秒展开代码与工程选择。

1. 为什么 List<Integer> 不是 List<Number>？怎样设计复制接口？
2. `? super Integer` 为什么读取只能当作 Object？哪些场景不适合通配符？
3. 类型擦除后有哪些信息仍可查询？raw type 怎么造成延迟失败？
4. CHM get 再 put 为什么丢更新？merge 如何改变原子边界？
5. null 返回与 null 映射有什么区别？为什么回调里不应做慢 I/O？
6. Map 中的 value 为什么仍需保护？账户转账和频次统计分别如何设计？

训练区新增六个真实简答题，追问按类型、原子操作、工程边界分支；不再把泛型求和当作并发安全证据。

:::quiz id="java-generics-depth-v1/pecs"
:::

:::quiz id="java-generics-depth-v1/atomic"
:::

## 主动回忆与本章总结

合上正文，画出“编译期类型约束 → 单键原子操作 → 值对象 → 多键业务不变量”的四层边界。解释一个你真实遇到的集合 API 设计；没有项目经历就明确说是本章练习，不虚构事故。

完成顺序：预测实验 → 闭卷回答 → 提交训练题 → 对照反馈 → 写下仍讲不清的点 → 本章闪卡复习。看过正文或展开答案不等于掌握；确认能够讲清原理与边界后再主动标记章节完成。

:::details 闭卷自测清单（不记录完成状态）
- [ ] 用一个反例解释 List 为什么不协变
- [ ] 说明 get＋put 和 merge 的原子边界
- [ ] 区分容器、值对象与跨键业务不变量
:::

## 官方参考与版本

核验日期：2026-10-11。Java 17 API 契约优先；Java Tutorials 标记为 JDK 8，但此处引用的是泛型基础规则，不用其版本替代 Java 17 并发实现。

- [Java 17 ConcurrentHashMap API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)
- [Oracle 通配符使用指南](https://docs.oracle.com/javase/tutorial/java/generics/wildcardGuidelines.html)
- [Oracle 类型擦除](https://docs.oracle.com/javase/tutorial/java/generics/erasure.html)
- [OpenJDK 固定标签 jdk-17+35 源码](https://github.com/openjdk/jdk/blob/jdk-17%2B35/src/java.base/share/classes/java/util/concurrent/ConcurrentHashMap.java)
