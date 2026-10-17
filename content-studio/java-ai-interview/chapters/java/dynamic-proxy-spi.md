---
id: v4-java-dynamic-proxy-spi
version: 4.1.0
status: pending_human_review
reviewState: code_verified
batch: 5
priority: P0
domain: java
module: M01
topic: JAVA-LANG-DYNAMIC-PROXY-SPI
sourceVersion: "OpenJDK 17.0.14+7"
level: L1-L5
---

# JDK 动态代理与 SPI：运行时生成代理、InvocationHandler 分发与服务提供者发现

## 1. 学习目标

- 能用 `Proxy.newProxyInstance` 在**运行时**为一组接口生成代理对象，并理解它**只能代理接口**。
- 理解 `InvocationHandler.invoke(proxy, method, args)` 的契约：所有代理方法调用都会被统一转发到这里。
- 掌握 JDK 动态代理与 CGlib/ByteBuddy/静态代理的边界与选型。
- 理解 SPI（Service Provider Interface）如何用 `META-INF/services` + `ServiceLoader` 解耦「接口定义方」与「实现提供方」。
- 能说清动态代理在 Spring AOP、RPC 客户端、MyBatis Mapper、框架可扩展点里的真实角色。

## 2. 问题动机

框架经常要在**不修改业务类源码**的前提下，给方法调用织入横切逻辑：事务开启/提交、日志埋点、权限校验、调用耗时统计、远程调用转发。

如果写「静态代理」，每个业务类都要手写一个代理类，类数量随业务类爆炸；更糟的是横切逻辑被复制得到处都是。我们需要一种机制：**在运行时、按接口动态生成**一个「中间人」对象，把所有方法调用统一拦下来，交给一段可复用的逻辑处理，再决定是否转给真实对象。

同时，框架希望自己的「能力」能被第三方无侵入地扩展——例如日志门面 `SLF4J` 不绑定具体实现。这就需要一种**标准的服务发现机制**：接口由框架定义，实现由 classpath 下的某个 jar 提供，框架在运行时「发现」它。这就是 SPI。

## 3. 直观理解（类比）

把代理想成老板的**秘书/前台**：客户（调用方）不直接找老板（真实对象），而是先找秘书。秘书可以做登记（日志）、检查身份（鉴权）、计时（监控），然后再把事情转给老板；老板的答复也可以经秘书回传。

SPI 则像公司的**供应商黄页**：公司（框架）只规定了「我需要一种能送货的服务」（接口），具体哪家供应商（实现）写在黄页（`META-INF/services/接口全限定名`）里；公司开门营业时按黄页把供应商都请来（`ServiceLoader.load`）。

## 4. 专业术语

- **Proxy**：`java.lang.reflect.Proxy`，提供 `newProxyInstance` 在运行时生成代理类与实例。
- **InvocationHandler**：所有代理方法调用的统一入口，只有一个方法 `invoke`。
- **$ProxyN**：JDK 在运行时生成的代理类的类名（如 `com.sun.proxy.$Proxy0`）。
- **SPI / ServiceLoader**：Java 标准的服务提供者发现机制，`META-INF/services/<接口全限定名>` 列出实现类全限定名。
- **桥方法（bridge method）**：泛型擦除后编译器生成的合成方法，代理也会转发它——理解它才能解释「为何有时 invoke 收到的是桥方法」。

## 5. 知识结构地图

```text
接口约束(只能代理接口)
      │
      ▼
Proxy.newProxyInstance(classLoader, ifaces[], handler)
      │  ① 查缓存/生成 $ProxyN 类(实现 ifaces, 继承 Proxy)
      ▼
$ProxyN 实例(持有 handler)
      │  ② 每个接口方法 mN 生成: handler.invoke(this, mN, args)
      ▼
InvocationHandler.invoke(proxy, method, args)
      │  ③ 用户逻辑: 日志/事务/鉴权/转发 target.method(args)
      ▼
真实对象(RealSubject)  ◄──── SPI 发现的实现也可作为 target/插件
```

- **前置**：反射机制（规划中）、泛型与类型擦除（规划中）。
- **后续关联**：[Spring AOP 代理与自调用](../spring/aop-proxy-self-invocation.md)（JdkDynamicAopProxy 正是用本篇机制）。

## 6. 核心技术原理

`Proxy.newProxyInstance(ClassLoader loader, Class<?>[] interfaces, InvocationHandler h)` 做三件事：

1. **生成/复用一个代理类**：该类**实现给定的所有接口**，并**继承 `java.lang.reflect.Proxy`**（因此 JDK 代理类不能是 final、目标必须是接口——Java 单继承已经给了 Proxy，无法再继承业务类）。
2. **构造代理实例**：代理类唯一的构造器接收 `InvocationHandler` 并传给父类 `Proxy.handler` 字段。
3. **方法转发**：代理类为每个接口方法生成一段代码，调用 `super.h.invoke(this, <对应的 Method 对象>, args)`。

`InvocationHandler.invoke` 的签名是 `Object invoke(Object proxy, Method method, Object[] args)`。注意第三个参数是 `Object[]`（基本类型会被装箱）；`proxy` 是代理自身，**不要在 handler 内对 `proxy` 调用会被再次转发的方法**，否则会无限递归。

## 7. 源码与底层实现（基于 OpenJDK 17 机制）

- 代理类由 **`ProxyGenerator` / `ProxyBuilder`** 在内存中生成字节码，类名形如 `com.sun.proxy.$Proxy0`，并定义 `static` 的 `Method` 字段缓存（`m0=Object.hashCode` 等，`m1=equals`，`m2=toString`，`m3+` 为业务接口方法）。
- 生成的类构造器为 `public $Proxy0(InvocationHandler h) { super(h); }`。
- 每个方法体大致是：`return (ReturnType) h.invoke(this, m3, new Object[]{arg0, arg1});`（参数不足时最后一个参数为 `null` 数组）。
- 类生成后存入 `WeakCache`（按 `ClassLoader + 接口列表` 缓存），同一组条件下**不会重复生成**，所以「频繁创建代理」的成本主要在首次生成与类加载。
- **桥方法**：当接口方法涉及泛型，编译器会生成桥方法保证多态正确；代理会同时转发桥方法与真实方法，`invoke` 可能收到 `synthetic` 的桥方法（可用 `method.isBridge()` 识别）。

> ⚠️ 上述事实（缓存、`Method` 字段、`WeakCache`、桥方法转发）属于公开且稳定的 JDK 行为描述，依据 OpenJDK 17 官方文档与广泛验证的机制；本章**未逐行比对 `ProxyGenerator` 源码**，仅按规范与可观察行为陈述。

## 8. 完整执行流程

```text
调用方 ──proxy.request(arg)──▶
   $Proxy0.request  ──▶ h.invoke(proxy, m_request, [arg])
                            │
              ┌─────────────┼─────────────┐
          日志/计时       鉴权/限流      业务转发
                            │
                            ▼
                   return h(target.request(arg))
                            │
                          回传调用方
```

关键顺序：① 调用方拿到的永远是代理对象；② 任何接口方法进入都先到 `invoke`；③ handler 决定是否、何时、如何调用真实对象；④ 返回值原样（或经转换）回传。

## 9. 企业业务需求

- **Spring AOP（JDK 代理模式）**：当被代理对象是接口实现时，Spring 默认用 `JdkDynamicAopProxy`，事务/`@Cacheable`/日志切面即经 `InvocationHandler` 织入。
- **RPC 客户端**：服务接口在本地，实现在远端；代理把方法调用序列化成网络请求发出去（如早期 Hessian、部分内部框架）。
- **MyBatis Mapper**：`UserMapper` 只是接口，MyBatis 用代理在运行时把方法名+注解映射成 SQL 执行。
- **框架可扩展点**：日志门面、序列化组件、插件体系用 SPI 让使用方无侵入替换实现。

## 10. Spring Boot 项目架构

一个最小可运行演示：定义 `Greeter` 接口，提供两个 `META-INF/services` 实现，用 `ServiceLoader` 加载后，再用动态代理给每个实现统一加上「调用耗时日志」。

```text
src/main/java/com/knowflow/demo/
  ├─ Greeter.java            // 业务接口
  ├─ CachedGreeter.java      // 实现 A
  ├─ RemoteGreeter.java      // 实现 B
  └─ TraceHandler.java       // InvocationHandler: 打印入参/耗时后转发
src/main/resources/META-INF/services/com.knowflow.demo.Greeter
  └─ com.knowflow.demo.CachedGreeter
  └─ com.knowflow.demo.RemoteGreeter
```

## 11. 完整代码与验证

接口与真实实现：

```java
public interface Greeter {
    String hello(String name);
}

public class CachedGreeter implements Greeter {
    public String hello(String name) { return "cache:hi " + name; }
}
```java

统一织入的 `InvocationHandler`（计数 + 耗时 + 转发）：

```java
public class TraceHandler implements InvocationHandler {
    private final Object target;
    public int invokes = 0;
    public TraceHandler(Object target) { this.target = target; }
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        invokes++;
        long t0 = System.nanoTime();
        try {
            return method.invoke(target, args);   // 转发真实对象
        } finally {
            long ms = (System.nanoTime() - t0) / 1_000_000;
            System.out.printf("%s 耗时 %dms%n", method.getName(), ms);
        }
    }
}
```

创建代理并调用：

```java
Greeter real = new CachedGreeter();
Greeter proxy = (Greeter) Proxy.newProxyInstance(
        Greeter.class.getClassLoader(),
        new Class<?>[]{Greeter.class},
        new TraceHandler(real));
proxy.hello("Bei");   // 实际走到 TraceHandler.invoke → 再转发 real.hello
```java

SPI 服务发现：

```java
ServiceLoader<Greeter> loader = ServiceLoader.load(Greeter.class);
for (Greeter g : loader) {
    System.out.println(g.hello("spi"));   // 依次输出两个实现的问候
}
```

## 12. 环境运行与验证

- 工程：`examples/dynamic-proxy-verify/`，环境 **OpenJDK 17.0.14 + Maven 3.8.3**，依赖 `junit-jupiter` + `assertj-core`，无 Spring、无数据库。
- 复现命令：

```bash
cd examples/dynamic-proxy-verify && mvn -q test
```text

- 预期结果：`Tests run: 4, Failures: 0, Errors: 0, BUILD SUCCESS`（断言：代理调用必触发 handler、`invoke` 计数正确、返回值正确透传、`ServiceLoader` 加载到 2 个实现、`invoke` 内调用 `proxy` 自身方法会抛出 Error 被捕获）。

## 13. 企业工程分析

- **只能代理接口**：这是 JDK 代理的硬约束；要代理类（如遗留无接口 bean）需 CGlib/ByteBuddy（继承式）。Spring 根据「是否接口」自动二选一。
- **InvocationHandler 建议无状态/线程安全**：同一个 handler 可能被多个代理、多个线程共用；用 `method` 对象区分方法，不要存可变实例状态。
- **代理创建成本**：首次数生成字节码 + 类加载，应缓存代理实例（如 Spring 的 `AdvisedSupport` 缓存单例代理），不要在热循环里 `newProxyInstance`。
- **SPI 风险**：`META-INF/services` 文件必须放在 `resources` 根、`ServiceLoader` 加载的实现必须有**公共无参构造器**，否则运行期 `ServiceConfigurationError`。

## 14. 技术对比与选型

| 方案 | 机制 | 能否代理类 | 性能 | 典型用途 |
|---|---|---|---|---|
| 静态代理 | 手写包装类 | 能 | 编译期确定 | 简单、少量 |
| JDK 动态代理 | 接口+运行时生成 $Proxy | **仅接口** | 调用经反射转发，中等 | Spring AOP(接口)、RPC、Mapper |
| CGlib | 继承目标类生成子类 | 能(非 final) | 更快(直接方法调用，但生成慢) | Spring AOP(类)、旧框架 |
| ByteBuddy | 运行时生成字节码 | 能 | 灵活高性能 | 现代框架、Agent |
| ASM | 直接字节码 | 能 | 最高 | 底层工具，开发成本高 |

选型原则：**有接口优先 JDK 动态代理**（零额外依赖、行为可预测）；无接口或要更强能力再用 ByteBuddy/CGlib。

## 15. 常见错误与反例

- **把类（非接口）交给 JDK 代理** → `IllegalArgumentException: ... is not an interface` / `... is a class`。
- **在 `invoke` 内调用 `proxy` 的方法** → 再次进入 `invoke`，无限递归 `StackOverflowError`。要调用真实逻辑就用 `method.invoke(target, args)`。
- **handler 内保存可变状态且不线程安全** → 并发下计数/上下文错乱。
- **SPI 实现类没有公共无参构造器**，或 `META-INF/services` 文件名写错（必须是接口全限定名，不是实现类）→ `ServiceConfigurationError`。
- **把 `args` 当成基本类型数组** → 实际上基本类型已被自动装箱成 `Object[]`，拆箱到真实方法时由反射完成。

## 16. 标准面试回答（话术）

> JDK 动态代理要求目标必须是**接口**，运行时由 `Proxy` 生成实现这些接口的 `$Proxy` 类，把所有方法调用统一转发到 `InvocationHandler.invoke(proxy, method, args)`。我们在 handler 里做横切逻辑（日志、事务、鉴权），再用 `method.invoke(target, args)` 调真实对象。它和 CGlib 的区别是：JDK 代理基于接口、零额外依赖、行为稳定；CGlib 通过继承目标类生成子类，能代理普通类但无法代理 final 类/方法。Spring AOP 默认按「是否接口」自动选择。

## 17. 高频追问与主动回忆

- 为什么 JDK 动态代理只能代理接口？（单继承已被 `Proxy` 基类占用）
- `$Proxy0` 这个类存在哪里？会不会重复生成？（内存中生成，`WeakCache` 按 ClassLoader+接口缓存）
- `invoke` 的 `proxy` 参数有什么用？为什么不能拿它再调方法？（用于区分、equals/hashCode 特殊判断；再调会递归）
- 泛型接口下 `invoke` 收到的 `method` 可能是桥方法，怎么识别？（`method.isBridge()`）
- SPI 和 API 的区别？`ServiceLoader` 如何找到实现？（`META-INF/services/<接口全限定名>` 列出实现类全限定名，需公共无参构造）

## 18. 五层总结

1. **是什么**：运行时按接口生成代理对象，统一把方法调用转给 `InvocationHandler`。
2. **为什么**：避免静态代理类爆炸，优雅织入横切逻辑。
3. **怎么做**：`Proxy.newProxyInstance` 三参 + `InvocationHandler.invoke` 转发真实对象。
4. **约束与边界**：仅接口、单例 handler 需线程安全、首建成本需缓存。
5. **怎么用**：AOP/RPC/Mapper/SPI 扩展点；与 SPI 配合实现「接口归框架、实现归第三方」。

## 19. 知识关联

- → [Spring AOP 代理与自调用](../spring/aop-proxy-self-invocation.md)：Spring 的 JDK 代理正是本机制；自调用失效的根因是「调用没走代理」。
- → 反射机制（规划中）：`method.invoke(target, args)` 是反射调用，代理的转发成本主要来自这层。
- → 泛型与类型擦除（规划中）：擦除产生的桥方法会被代理转发，是 `invoke` 偶发收到桥方法的原因。

## 20. 事实标记

- ✅ **已执行**：`examples/dynamic-proxy-verify` 验证工程（4/4），断言覆盖「代理调用必触发 handler / 计数正确 / 返回值透传 / SPI 加载到 2 实现 / 递归自调用抛出 Error」等可观测行为。
- 📄 **源文档陈述**：`$Proxy` 继承 `Proxy`、仅实现接口、`Method` 字段缓存、`WeakCache`、`ServiceLoader` 机制来自 OpenJDK 17 官方文档与稳定机制描述。
- ⚠️ **未验证**：代理类字节码的具体生成算法（`ProxyGenerator`/`ProxyBuilder` 内部实现）未逐行核对 OpenJDK 源码，按公开规范描述；CGlib/ByteBuddy 对比未做实测基准。
