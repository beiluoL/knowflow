---
id: v4-java-arraylist
version: 4.1.0
status: pending_human_review
reviewState: code_verified
batch: 5
priority: P0
domain: java
module: M02
topic: JAVA-COLLECTIONS-ARRAYLIST
sourceVersion: "OpenJDK 17.0.14+7"
level: L1-L5
---

# ArrayList：底层结构、扩容机制与 fail-fast 迭代

## 1. 学习目标

- 讲清 `ArrayList` 的底层是**动态数组**（`elementData`），`size` 与 `capacity` 是两回事。
- 理解扩容：`add` 触发 `ensureCapacityInternal` → `grow`，**新容量 = 旧容量 + 旧容量/2（约 1.5 倍）**，JDK 7+ 初始为 **10**。
- 理解 `modCount` 与 **fail-fast**：迭代过程中若结构被修改（除迭代器自身 `remove`），抛 `ConcurrentModificationException`。
- 能解释 `subList` 是**视图**而非拷贝，以及为何 `trimToSize` 在批量添加后有用。

## 2. 问题动机

数组长度固定，业务里元素数量常是运行期才知道的。若每次满都 `new` 一个更大数组并拷贝，频繁 `add` 的成本会很高。我们需要一个「自动变长」的数组容器：`ArrayList` 用**成倍预留容量 + 必要时拷贝**来摊还成本，使 `add` 的均摊时间为 O(1)。

另一个动机是**并发安全的失败可见性**：单线程用迭代器遍历时，若别的代码（或自己用 `list.remove`）在中途改了结构，继续遍历会得到错乱结果。ArrayList 用 `modCount` 让迭代器在「发现被偷偷改了」时立刻失败，而不是带着错误数据静默跑下去。

## 3. 直观理解（类比）

`ArrayList` 像一个**带伸缩功能的有序货架**：货道（`elementData`）预先留了 10 个格子；装满后再进货时，管理员建一条 15 个格子的新货道（1.5 倍），把旧货搬过去。货架上贴了张「修改次数单」`modCount`；盘点员（迭代器）开工前记下单号，盘点中若发现单号变了（有人动了货），立刻举手报错而不是继续瞎盘。

## 4. 专业术语

- **elementData**：实际存元素的 `Object[]`（JDK 7+ 非序列化字段，按需序列化）。
- **size**：当前元素个数；**capacity** = `elementData.length`（容量）。
- **grow / hugeCapacity**：扩容核心，负责算新容量并 `Arrays.copyOf`。
- **modCount**：结构修改计数器，供迭代器做 fail-fast 校验。
- **fail-fast**：迭代器发现 `modCount` 与自身记录不符即抛 `ConcurrentModificationException`。
- **subList**：返回原 list 的**视图**，操作会反映到原 list。

## 5. 知识结构地图

```text
add(E) ─▶ ensureCapacityInternal(size+1)
              │ 容量够? ──是──▶ 直接放入 elementData[size++]
              │ 不够
              ▼
         grow(minCapacity)
              │ newCap = old + (old>>1)  // 1.5x
              ▼
         elementData = Arrays.copyOf(elementData, newCap)
              │
              ▼
         elementData[size++] = e

迭代: Itr 记录 expectedModCount = modCount
       next() 先 checkForComodification(): modCount != expected → CME
```

- **前置**：[HashMap 定位与 put](hashmap.md)（同样用数组+扩容思想）、[equals/hashCode 契约](equals-hashcode.md)（`contains/indexOf` 依赖 `equals`）。
- **后续关联**：[LinkedList](linkedlist.md)（对比数组 vs 链表）、集合迭代与一致性视图（规划中）。

## 6. 核心技术原理

- 默认构造器在 **JDK 7+ 延迟初始化**空数组（首次 `add` 才扩到 10）；指定 `initialCapacity` 则直接建对应大小数组。
- `add` 流程：`size+1` 是否超过 `elementData.length`？是则 `grow`。`grow` 计算 `newCapacity = oldCapacity + (oldCapacity >> 1)`（右移一位=除以 2，故约 1.5 倍），再 `Arrays.copyOf` 拷贝；若仍不够则用 `minCapacity`（如 `addAll` 大集合）。
- `ensureCapacity(int min)` 是公共方法，可**提前**扩容避免多次 `grow`（批量插入前调用能省拷贝）。
- `modCount` 在 `add/remove/clear/addAll` 等**结构性修改**时自增；`Itr`（迭代器）构造时记 `expectedModCount = modCount`，每次 `next/remove` 前比对，不等即抛 `CME`。
- `subList(from,to)` 返回 `SubList`（内部类），**没有复制元素**；对子列表的改动直接作用于原 list，且会改 `modCount`。

> ⚠️ 关于「JDK 6 初始容量为 10、JDK 7+ 延迟到首次 add 才 10」：这是历史事实；本章以 **OpenJDK 17** 为准（延迟初始化，首次 add 后容量为 10）。

## 7. 源码与底层实现（基于 OpenJDK 17 公开行为）

- 字段：`transient Object[] elementData; private int size; protected transient int modCount;`
- `grow(int minCapacity)`：`int oldCapacity = elementData.length; int newCapacity = oldCapacity + (oldCapacity >> 1);`（10→15→22→33…）；若 `newCapacity - minCapacity < 0` 用 `minCapacity`；极大值走 `hugeCapacity`。
- `Itr.next()`：`checkForComodification();` 即 `if (modCount != expectedModCount) throw new ConcurrentModificationException();`
- `subList` 的 `add/remove` 会调用外层的 `ArrayList.this.add/remove` 并 `modCount++`，所以外部也能看到变更。

> ⚠️ 上述字段名与分支依据 OpenJDK 17 源码结构描述；验证工程通过**反射读取 `elementData.length`** 与**观察 `modCount` 抛异常行为**来佐证，而非逐行引用源码行号。

## 8. 完整执行流程

```text
list.add("x")
  └─ ensureCapacityInternal(size+1=1)   // 首次: elementData 空, minCapacity=10
       └─ grow(10) → elementData = new Object[10]
  └─ elementData[0]="x"; size=1; modCount++

list.add 第 11 个元素
  └─ ensureCapacityInternal(11) → capacity 10 不够
       └─ grow(11) → newCapacity = 10 + 5 = 15
       └─ elementData = Arrays.copyOf(elementData, 15)

for (String s : list) { ... }   // 迭代中 list.add("y")
  └─ Itr.next() → checkForComodification() → modCount 已变 → CME
```

## 9. 企业业务需求

- **DTO/结果集收集**：接口返回 `List<Order>` 多用 `ArrayList` 装载。
- **批量导入**：先 `new ArrayList<>(expectedSize)` 或 `ensureCapacity`，避免反复 1.5x 拷贝拖慢导入。
- **遍历中删除**：用 `Iterator.remove()` 而非 `fori` 里 `list.remove(i)`，否则既可能漏元素又会触发 fail-fast 或索引错位。
- **分页/子段处理**：`subList` 做分段，但要注意它是视图、且外部修改会影响它。

## 10. Spring Boot 项目架构

在 Spring Boot 服务里，典型的「查询→组装→返回」：Mapper 返回 `ArrayList`，Service 用 `forEach/stream` 转换，Controller 返回 `List<Vo>`。注意：**不要**把 `subList` 结果直接序列化返回（它是视图，且 `ArrayList` 子类无法被某些序列化器稳定处理），应 `new ArrayList<>(list.subList(...))` 固化为独立列表。

```text
Controller → Service → Mapper(返回 ArrayList) → 转换为 Vo → 返回 List<Vo>
```

## 11. 完整代码与验证

```java
List<String> list = new ArrayList<>(2);  // 预分配容量 2
list.add("a"); list.add("b");
list.ensureCapacity(100);                // 提前扩容，避免多次 grow
list.add("c");
// 迭代中安全删除用迭代器
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    if ("b".equals(it.next())) it.remove();   // 安全：不会触发 CME
}
// 错误示范（会触发 fail-fast）：
// for (String s : list) { if (s.equals("a")) list.remove(s); }
```text

## 12. 环境运行与验证

- 工程：`examples/arraylist-verify/`，环境 **OpenJDK 17.0.14 + Maven 3.8.3**，依赖 `junit-jupiter` + `assertj-core`。
- 复现命令：

```bash
cd examples/arraylist-verify && mvn -q test
```

- 预期结果：`Tests run: 6, Failures: 0, Errors: 0, BUILD SUCCESS`（断言：添加第 11 个元素后 `elementData.length` 经反射读为 15；`ensureCapacity(100)` 后容量 ≥100；`for-each` 中移除首元素触发 `CME`；`Iterator.remove` 不触发；`subList` 改动反映到原 list）。

## 13. 企业工程分析

- **预估容量是性价比最高的优化**：知道大概数量就传 `initialCapacity`，或在批量 `addAll` 前 `ensureCapacity`，把多次 1.5x 拷贝压成一次。
- **fail-fast 不是线程安全**：`modCount` 只是单线程下「检测偷偷修改」的机制，**不保证并发安全**；多线程写要用 `CopyOnWriteArrayList` 或加锁。
- **subList 陷阱**：它是视图，原 list 结构变化会让子列表失效；序列化/跨方法传递前先 `new ArrayList<>(sub)`。
- **随机访问友好、插入中间贵**：`get(i)` O(1)、`add(0, e)` / `remove(i)` 需 `System.arraycopy` 搬移 O(n)。

## 14. 技术对比与选型

| 容器 | 底层 | 随机读 | 头插/中间插 | 线程安全 | 适用 |
|---|---|---|---|---|---|
| ArrayList | 动态数组 | O(1) | O(n) 搬移 | 否(fail-fast) | 读多写少、尾部追加 |
| LinkedList | 双向链表 | O(n) | O(1) 有节点 | 否 | 频繁头尾/中间插删 |
| CopyOnWriteArrayList | 数组副本 | O(1) | O(n) 复制 | 是(读无锁) | 读极多写极少 |
| Vector | 数组 | O(1) | O(n) | 是(方法 synchronized) | 遗留，基本不用 |

## 15. 常见错误与反例

- **遍历中用 `list.remove`**（for-each 或 fori） → `ConcurrentModificationException`（for-each）或漏删/越界（fori）。正确做法：`Iterator.remove()`。
- **以为 `subList` 是独立副本** → 改原 list 后子列表抛 `CME`；或序列化视图出问题。
- **频繁 `add` 不预估容量** → 反复 1.5x 拷贝，大批量导入性能差。
- **误用 `trimToSize` 在还会继续 add 的场景** → 之后又立刻扩容，白做无用功；只在「确定不再增长」后调用以省内存。

## 16. 标准面试回答（话术）

> ArrayList 底层是 `Object[] elementData`，`size` 是元素数、容量是数组长度。默认空数组，首次 add 扩到 10；之后满了就 `grow`，新容量是旧的 1.5 倍（旧 + 旧>>1），用 `Arrays.copyOf` 拷贝。所以 `add` 均摊 O(1)。迭代器靠 `modCount` 做 fail-fast：迭代中若结构被改（非迭代器自身 remove）就抛 `CME`——注意这只是失败快速暴露，不是线程安全。读多写少、尤其尾部追加时选它；频繁中间插入用 LinkedList。

## 17. 高频追问与主动回忆

- 扩容倍数是多少？为什么是 1.5 而不是 2？（旧 + 旧>>1；1.5 在内存浪费与拷贝次数间折中）
- 初始容量真的是 10 吗？（JDK7+ 延迟初始化，首次 add 后才 10）
- `modCount` 能当并发安全用吗？（不能，只是单线程 fail-fast 检测）
- 为什么 `for-each` 里 `remove` 会 CME，而 `Iterator.remove()` 不会？（后者会同步 `expectedModCount`）
- `subList` 修改会影响原 list 吗？（会，它是视图）

## 18. 五层总结

1. **是什么**：基于动态数组的 `List`，自动扩容。
2. **为什么**：数组定长，业务需变长容器且要均摊 O(1) 追加。
3. **怎么做**：满则 1.5x 扩容 + 拷贝；迭代器用 `modCount` 暴露并发/偷偷修改。
4. **约束边界**：非线程安全、中间插入 O(n)、`subList` 是视图。
5. **怎么用**：预估容量、用迭代器删除、`CopyOnWriteArrayList` 应对读多写少并发。

## 19. 知识关联

- → [LinkedList](linkedlist.md)：链表 O(1) 插删 vs 数组 O(n) 搬移，选型对照。
- → [HashMap](hashmap.md)：同样「数组+扩容」思想，但 HashMap 扩容是 2 倍且搬移同时重哈希。
- → [equals/hashCode 契约](equals-hashcode.md)：`indexOf/contains` 依赖 `equals` 判等。

## 20. 事实标记

- ✅ **已执行**：`examples/arraylist-verify` 验证工程（6/6），反射读取 `elementData.length` 验证 10→15 扩容、容量预分配，并复现 fail-fast 与 `Iterator.remove` 差异。
- 📄 **源文档陈述**：`elementData`/`size`/`modCount` 字段、`grow` 的 1.5x 分支、`Itr.checkForComodification` 行为来自 OpenJDK 17 源码结构描述。
- ⚠️ **未验证**：未逐行引用 OpenJDK 源码行号；`hugeCapacity` 的 `MAX_ARRAY_SIZE` 边界未实测；并发正确性未做多线程实测（fail-fast ≠ 线程安全已明确标注）。
