# string-verify（M01 String/StringBuilder/StringBuffer）

验证 String 不可变、常量池与拼接行为。

## 验证点
- 字面量池共享：`"abc" == "abc"` 为 true。
- `new String("abc")` 与字面量 `!=` 但 `equals`。
- `intern()` 回池：与字面量同引用。
- `toUpperCase/concat` 不改原串，返回新串（不可变）。
- 循环拼接用 `StringBuilder` 结果正确。
- `StringBuffer` 亦可正确拼接。
- `StringJoiner` 按分隔符拼接。

## 运行
```bash
mvn -q test
```
预期：`Tests run: 9, Failures: 0, Errors: 0, BUILD SUCCESS`

## 诚实边界（见章节 §20）
- ✅ 已执行：上述 9 项可观测行为。
- ⚠️ 未执行：StringTable 内部实现与 GC、`StringBuffer` synchronized 开销基准对比。
