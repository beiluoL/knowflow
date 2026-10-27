# 泛型、集合边界与 ConcurrentHashMap

## 学习目标
- 解释 PECS 与类型擦除，用 Java 17 代码验证类型边界
- 使用 ConcurrentHashMap 的单键原子操作，修复丢失更新
- 区分容器、可变值与跨键业务不变量，说明工程选型依据

难度：2；预计 35 分钟；面试重要度：S

从编译期类型约束，到运行时原子边界：用代码、反例与工程场景，讲清泛型和并发集合分别保证什么。

## 01. 本章解决什么问题

上一章回答 `HashMap` 如何定位键。本章先检查 API 的类型约束，再检查共享数据的并发边界。

读完后，你应能解释三个现象：

1. `List<Integer>` 不能赋给 `List<Number>`。
2. 并发 Map 的 `get()`、`put()` 各自安全，但组合起来仍会丢计数。
3. 把 `ArrayList` 放进并发 Map，并不会让 `list.add()` 自动线程安全。

前置知识是对象相等与散列契约、接口与实现，以及线程共享对象的含义。本章不扩展 AQS 或线程池，也不新增 DAG 节点。

## 02. Java 泛型与类型擦除

### 2.1 为什么需要泛型

泛型把“这个容器接收什么类型”的约束交给编译器，让类型不匹配尽早暴露，而不是等到读取时强转失败。

`List<Integer>` 不是 `List<Number>` 的子类型。否则，通过后者写入一个 `Double`，就会破坏前者的元素约束。这是参数化类型的不变性，不能直接照搬数组的协变规则。

### 2.2 编译后发生了什么

编译器进行静态类型检查，将类型变量擦除到边界；无界变量擦除到 `Object`。它还会按需插入类型转换与桥接方法。

原始类型（raw type）绕开部分检查，可能造成 heap pollution。异常可能直到读取并转换类型时才暴露；捕获 `ClassCastException` 不是修复类型边界的方法。

### 2.3 运行时仍有哪些类型信息

类文件可保留泛型签名供反射查询，但普通对象并不因此保存完整的实际类型参数。

不能写 `new T()`，也不能用 `instanceof List<String>` 检查列表元素类型。“类型擦除”不意味着所有参数化 List 都可互换。

### 2.4 Java 17 代码验证

以下片段放在有 `java.util.*` 导入的 `main()` 中运行：

```java
List<String> names = new ArrayList<>();
List<Integer> numbers = new ArrayList<>();
System.out.println(names.getClass() == numbers.getClass());
System.out.println(names.getClass().getName());
```

输出：

```text
true
java.util.ArrayList
```

:::tip 关键理解
运行时实现类相同，不等于编译期类型约束相同。先区分“对象的类”和“变量的参数化类型”。
:::

## 03. PECS 原则

### 3.1 Producer Extends：从参数读取

`List<? extends Number>` 表示某个具体但未知的 `Number` 子类型列表。读取时可以把元素当作 `Number`，但不能安全加入任意 `Integer` 或 `Double`。

这里的“生产者”指给当前方法提供元素的参数，不是创建对象的线程。先判断当前方法的读写方向，再选择通配符。

### 3.2 Consumer Super：向参数写入

`List<? super Integer>` 可以接收 `Integer`，读取时只能安全地视作 `Object`。底层也可能是 `List<Number>` 或 `List<Object>`，不能推断所有元素都是 Integer。

| 参数 | 当前方法可安全做什么 | 不能推断什么 |
| --- | --- | --- |
| `? extends Number` | 读取为 `Number` | 不能任意加入 `Integer` |
| `? super Integer` | 写入 `Integer` | 读取不能直接当作 `Integer` |
| `List<T>` | 保留同一类型变量的关系 | 不能替代任意类型的 List |

### 3.3 典型反例与使用边界

:::warning 不要把 extends 理解成“完全不可修改”
类型规则禁止任意加入非 null 元素，但移除、清空不等于写入新元素。`null` 是类型层面的例外，具体集合实现仍可能拒绝它。
:::

当方法既读又写，并需要保留同一类型关系时，具体类型参数往往更清晰。不要为每个参数机械增加通配符。

### 3.4 工程场景：设计复制接口

在类中声明方法：

```java
static <T> void copyInto(List<? extends T> source, List<? super T> target) {
    for (T item : source) {
        target.add(item);
    }
}
```

调用并验证：

```java
var from = List.of(1, 2, 3);
var to = new ArrayList<Number>();
copyInto(from, to);
System.out.println(to); // [1, 2, 3]
```

该方法要求 `target` 支持添加。传入不可变列表，即使类型检查通过，也可能抛出 `UnsupportedOperationException`。

## 04. ConcurrentHashMap

### 4.1 为什么需要并发映射

共享 Map 需要明确读写协议。`ConcurrentHashMap` 提供并发映射操作，但它不是一个自动包裹整个业务逻辑的事务。

### 4.2 线程安全边界与 null 语义

单次查询、插入安全，不意味着“查询再插入”是不可分割的操作。`containsKey()` 再 `get()` 也不是事务。

不允许 null 键或值，使查询结果 `null` 可以表达本次没有取得映射值。需要缓存空结果时，应使用非 null 包装类型或哨兵，并明确 TTL、容量和失效规则。

### 4.3 原子复合操作

```java
// 错误：两个线程可能读到同一个旧值，再分别覆盖。
counts.put(key, counts.getOrDefault(key, 0) + 1);

// 正确：将同键读改写表达为一个原子映射操作。
counts.merge(key, 1, Integer::sum);
```

“错误”指不能保证并发计数不丢失，不是说 `put()` 会损坏 Map。原子边界是一个键的映射，不是多个键的共同业务不变量。

### 4.4 按业务语义选择 API

| API | 适合表达的操作 | 注意事项 |
| --- | --- | --- |
| `putIfAbsent()` | 缺失时条件插入 | 值通常已在调用前构造 |
| `computeIfAbsent()` | 缺失时初始化 | 返回 null 不建立映射 |
| `compute()` | 同键状态变换 | 返回 null 可删除已有映射 |
| `merge()` | 把非 null 新值合并到旧值 | 合并结果 null 可删除映射 |

回调要短小，避免慢网络请求和递归更新同一映射。Map 的原子性不为回调外部副作用提供跨服务的 exactly-once 保证。

### 4.5 Java 17 实验与源码路线

训练区保留完整 `GenericsBoundaryDemo.java`。两个线程与屏障先读取同一个旧值，再分别 `put()`，稳定演示错误计数为 1；对照组每个线程 `merge()` 一次，等待 `join()` 后结果为 2。

```text
get+put=1
merge=2
null rejected
```

固定源码标签为 `jdk-17+35`：从 `putVal()`、`get()` 看 `Node` 数组与链/树桶；空桶插入使用 CAS，非空桶更新有桶级同步与重检，迁移节点引导访问新表并参与扩容。

Java 7 的 Segment 不可直接套到 Java 17。不是所有操作都无锁，也不是每个键独占一把锁——冲突键可能落在同一个桶。实验不是吞吐量基准。

## 05. 容器安全与值对象安全

### 5.1 容器保证什么

Map 管理键与值的关联；它不会替可变值的所有方法加锁。遍历与 `size()` 也不是正在变化的整个 Map 的强一致快照。

### 5.2 可变 Value 的并发问题

`ConcurrentHashMap<String, ArrayList<String>>` 的映射操作安全，不代表多个线程可以无保护地调用同一个列表的 `add()`。

可选择不可变值加同键原子替换，或受独立同步协议保护的值对象。先定义谁读取、谁写入、谁删除映射，再选择容器。

### 5.3 LongAdder 与 AtomicInteger 如何选

| 需求 | 可考虑的工具 | 必须说明的边界 |
| --- | --- | --- |
| 统计事件频次 | `LongAdder` | 并发 `sum()` 不是原子快照 |
| 单个整数的 CAS 状态转换 | `AtomicInteger` | 多个原子变量的组合仍需协议 |
| 跨键账户转账 | 统一锁协议或数据库事务 | 不能仅把 Map 换为 CHM |

停止写入并等待工作线程结束后再读取统计值，与并发写入时观察统计值，是不同的验证条件。不在本章虚构性能数据。

## 06. 工程案例：标签频次统计

这是一个教学场景，不是虚构的生产事故：多个任务为文本标签累加计数，读取侧用于监控统计。

```java
ConcurrentHashMap<String, LongAdder> frequencies = new ConcurrentHashMap<>();
frequencies.computeIfAbsent("java", k -> new LongAdder()).increment();
```

1. Map 原子地建立或取得同键的计数对象。
2. `LongAdder` 负责对象内部的并发累加。
3. 若要验证最终总数，先等待全部写入任务结束。
4. 若要定期清空或删除键，额外定义映射生命周期。

:::warning 删除映射不是停止写入
线程可能仍持有已删除映射的旧 `LongAdder` 并继续累加。不能把 `sum()` 当作额度扣减或资金结算需要的原子快照。
:::

## 07. 常见误区：错误与正确对照

错误的值对象用法：

```java
// Map 不会保护 ArrayList 内部状态。
groups.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
```

一种明确的替代方案是同键原子替换不可变列表：

```java
ConcurrentHashMap<String, List<String>> groups = new ConcurrentHashMap<>();
groups.compute(key, (k, old) -> {
    var next = new ArrayList<String>(old == null ? List.of() : old);
    next.add(value);
    return List.copyOf(next);
});
```

该方案有复制成本，并要求全部写入遵守同一协议；不是所有工作负载的最佳方案。这里假定 `value` 非 null。

| 常见错误 | 应纠正为 |
| --- | --- |
| 类型擦除使所有 List 可互换 | 编译期参数约束仍然有效 |
| `computeIfAbsent()` 的结果自动安全 | Map 与值对象是两个同步边界 |
| `get()` 加 `put()` 就是原子计数 | 使用匹配业务语义的原子复合操作 |
| CHM 保证多键事务 | 跨键不变量需要共同协议 |
| Java 17 使用 Segment | 分清版本与 API 契约 |

## 08. 面试追问：基础 → 原理 → 工程 → 边界

### 8.1 基础：30 秒先给结论

解释 `List<Integer>` 为什么不是 `List<Number>`；再说明 PECS 从当前方法的读写方向推导，而不是背“父类与子类”。

### 8.2 原理：90 秒展开原因

类型擦除后哪些信息仍可查询？CHM 为什么不接收 null？`merge()` 如何改变 `get()` 加 `put()` 的原子边界？

### 8.3 工程：给出可验证的选择

设计复制接口、标签频次统计或不可变值更新。说明数据规模、读写方式、对象生命周期和验证方法，而不是编造项目经历。

### 8.4 边界：承受连续追问

为什么回调里不应做慢 I/O？删除映射后旧值还能被更新吗？账户转账为什么不能靠 CHM？并发遍历能否作为强一致快照？

## 09. 主动回忆与练习

沿用本章已有题目、闪卡和 Java 实验，不增加第二套训练与判分系统。

1. 预测复制输出、错误计数、正确计数和 null 异常。
2. 合上正文，闭卷讲解四层边界：类型约束、单键原子操作、值对象、跨键不变量。
3. 在下方训练区提交答案，对照真实反馈，再写下仍讲不清的点。
4. 通过本章闪卡复习；理解并能说明边界后，再主动标记章节完成。

:::quiz id="java-generics-depth-v1/pecs"
:::

:::quiz id="java-generics-depth-v1/atomic"
:::

:::details 闭卷自测清单（不记录完成状态）
- [ ] 用反例解释 List 不变性
- [ ] 说明 `get()` 加 `put()` 与 `merge()` 的原子边界
- [ ] 区分容器、值对象和跨键业务不变量
:::

看过正文、展开答案或点击自测项，不等于完成训练或达到掌握水平。现有简答匹配也不是完整面试能力评分。

## 10. 本章总结与参考资料

类型约束回答“编译器允许什么”；并发集合回答“映射操作保证什么”。值对象和跨键业务规则仍需要自己的协议。

内容版本：`java-generics-reading-v5`。Java 17 API 契约优先；源码固定为 OpenJDK `jdk-17+35`。Java Tutorials 是 JDK 8 资料，此处只引用其泛型基础规则，不用其版本替代 Java 17 的并发实现。

- [Java 17 ConcurrentHashMap API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)
- [Java 17 LongAdder API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/LongAdder.html)
- [Oracle 通配符使用指南](https://docs.oracle.com/javase/tutorial/java/generics/wildcardGuidelines.html)
- [Oracle 类型擦除](https://docs.oracle.com/javase/tutorial/java/generics/erasure.html)
- [OpenJDK 固定标签源码](https://github.com/openjdk/jdk/blob/jdk-17%2B35/src/java.base/share/classes/java/util/concurrent/ConcurrentHashMap.java)
