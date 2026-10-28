# dynamic-proxy-verify（M01 JDK 动态代理与 SPI）

验证 JDK 动态代理与 SPI 的可观测行为。

## 验证点
- 代理对象的方法调用**必然**转发到 `InvocationHandler.invoke`（计数断言）。
- 返回值经 handler 正确透传。
- JDK 动态代理**只能代理接口**：传入类（`String`）抛 `IllegalArgumentException`。
- 在 `invoke` 内调用 `proxy` 自身方法会无限递归 → `StackOverflowError`。
- `ServiceLoader.load` 依据 `META-INF/services` 加载到 2 个实现。

## 运行
```bash
mvn -q test
```
预期：`Tests run: 4, Failures: 0, Errors: 0, BUILD SUCCESS`

## 诚实边界（见章节 §20）
- ✅ 已执行：上述 4 项可观测行为。
- ⚠️ 未执行：CGlib/ByteBuddy 对比、代理类字节码生成细节（属源码层，按规范描述）。
