# arraylist-verify（M02 ArrayList）

验证 ArrayList 的底层行为。

## 验证点
- 延迟初始化：空 list 容量为 0，首次 add 后容量为 10。
- 1.5x 扩容：添加第 11 个元素后（反射读 `elementData.length`）容量 = 15。
- `ensureCapacity(100)` 预分配后容量 ≥ 100。
- fail-fast：`for-each` 中 `list.remove` 抛 `ConcurrentModificationException`；`Iterator.remove()` 安全。
- `subList` 是视图：改动反映到原 list。

## 运行
```bash
mvn -q test
```
预期：`Tests run: 6, Failures: 0, Errors: 0, BUILD SUCCESS`

## 诚实边界（见章节 §20）
- ✅ 已执行：上述 6 项（含反射白盒读容量）。
- ⚠️ 未执行：`hugeCapacity` MAX_ARRAY_SIZE 边界、并发正确性（fail-fast ≠ 线程安全）。
