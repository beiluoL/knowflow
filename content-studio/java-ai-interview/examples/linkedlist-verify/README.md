# linkedlist-verify（M02 LinkedList）

验证 LinkedList 的可观测行为。

## 验证点
- 头尾插入/删除顺序正确（O(1) 语义）。
- `get(i)` 返回正确值（O(n) 但结果正确）。
- **非 `RandomAccess`**：随机访问是 O(n)。
- `ListIterator` 可 `next/previous` 双向遍历。
- 按值/按下标移除后长度与内容正确。
- `Deque` 栈/双端语义（`push/pop/offer/poll`）。

## 运行
```bash
mvn -q test
```
预期：`Tests run: 8, Failures: 0, Errors: 0, BUILD SUCCESS`

## 诚实边界（见章节 §20）
- ✅ 已执行：上述 8 项可观测行为。
- ⚠️ 未执行：内存开销/缓存友好性基准、`ArrayDeque`/`ConcurrentLinkedDeque` 对比实测。
