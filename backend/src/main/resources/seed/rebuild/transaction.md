# Spring 事务：代理边界与真实数据库结果

版本 java-ai-content-rebuild-v1；工程基准 Spring Framework6.1 / Spring Boot3.2.5 / Java17；核验2026-10-11。官方 current 文档可能更新，版本新增能力不能套用于本项目。

## 01. 从失效场景进入

冲刺先回答“同类自调用为什么没有新事务”，再追传播、回滚、线程边界；深入按代理入口、事务管理器、连接绑定、调用、提交/回滚顺序阅读。前置：AOP代理、JDBC连接、异常类型、数据库隔离。

事务不是注解自身执行SQL魔法。判断正确性要看代理是否被调用、选了哪个管理器、在哪个线程、哪个数据库连接，以及最终数据和异常。

## 02. 源码入口与调用链

外部调用代理 → TransactionInterceptor.invoke → TransactionAspectSupport.invokeWithinTransaction → 解析事务属性/选择TransactionManager → 获取或参与事务 → 调用目标方法 → 按异常规则完成事务 → 清理上下文。

`PlatformTransactionManager` 处理具体资源事务；JDBC管理器管理连接、提交回滚和线程绑定，JPA等有自身实现。注解定义边界/策略，不保证远程HTTP、消息或多个独立数据源自然成为同一事务。

## 03. 代理、自调用与可见性

默认 proxy 模式只拦截经过代理的调用。`this.inner()` 直接进入目标方法，因此内层注解不能重新开启 REQUIRES_NEW 或覆盖传播。外层已有事务时内部代码可能仍运行在外层事务中，不能笼统说“自调用没有事务”。

修复优先拆成独立协作 Bean，通过注入的代理调用；或明确使用 TransactionTemplate。不要依赖 private/final 方法和任意代理形态都能拦截。接口代理关注接口public方法，类代理对非public支持还要区分版本/方法可覆盖性。

```java
// 外部代理调用外层，内部 this 调用不产生新的传播边界
@Transactional
public void outer() {
    inner();
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void inner() { /* 不应据此断言新连接、新事务 */ }
```

## 04. 传播、逻辑事务与物理事务

| 策略 | 机制 | 工程边界 |
| --- | --- | --- |
| REQUIRED | 有事务参与，无则新建 | 内层rollback-only可能令外层UnexpectedRollbackException |
| REQUIRES_NEW | 挂起外层，独立物理事务 | 需要额外连接，池容量与并发必须测量 |
| NESTED | 支持时使用保存点 | 管理器/驱动支持；不是独立提交 |
| NOT_SUPPORTED | 挂起事务，非事务执行 | 外部副作用仍需补偿 |

内层 REQUIRED 的逻辑边界可以把共享物理事务标 rollback-only；外层捕获异常后继续并不保证提交成功。REQUIRES_NEW 日志可能独立提交，主事务失败时留下记录是设计语义，不是偶然。

## 05. 回滚规则与异常处理

Spring6.1默认对 RuntimeException/Error 回滚，checked exception 默认不回滚；`rollbackFor`/`noRollbackFor` 明确业务规则。回滚是事务管理器针对受管资源的动作，不等于所有外部行为撤销。

:::warning
方法内部吞掉异常并正常返回，拦截器可能按成功提交；若下层已标rollback-only又会出现另一种失败。不能只用“捕获了就不会回滚”概括全部情况。
:::

优先按异常类型声明规则而非模糊名称模式；不要将最新文档的全局 ALL_EXCEPTIONS 默认配置能力当作6.1现有特性。事务只读通常是优化提示，不是统一权限安全防线。

## 06. 隔离与异步边界

隔离级别作用于实际新事务；参与现有事务时不能假定内层要求重新配置物理连接。数据库实现决定锁/MVCC可见性；Spring注解不替代MySQL隔离机制。

普通线程绑定事务不会自动传播到新线程/异步任务。异步失败也不一定回滚已提交主事务。响应式事务使用不同上下文模型，不能按ThreadLocal模型生搬。设计异步业务时明确事件提交时机、outbox、幂等和补偿，不声称项目已经实现这些。

## 07. 可运行工程实验

配套 `SpringTransactionRebuild` 使用本项目真实 Spring6.1 Bean代理、JDBC管理器与H2：外部代理方法先插入再抛RuntimeException，最终行数0；自调用内层同样插入抛错，却未经过新代理边界，最终行数1。实验核查实际数据库结果，不靠打印注解。

运行需项目依赖classpath。H2只能证明代理/事务边界，不能证明MySQL间隙锁、隔离级别或生产连接池配置。源码涉及库类时固定6.1.6，而非最新分支。

## 08. 排障顺序与设计权衡

教学场景：订单部分写入。先确认入口Bean是否代理、真实调用链是否跨代理，再看线程与事务管理器、连接/资源、传播、异常是否逸出、rollback-only及最终数据库状态。不要仅看日志“方法异常”就断言已经回滚。

缩短事务范围，避免慢HTTP/模型调用占住数据库连接。跨资源一致性需要明确模式；无法原子提交时用状态机/可靠事件与重试。不是所有问题都应该套 REQUIRES_NEW，连接池耗尽时它可能加剧问题。

## 09. 追问与标准回答

代理 → 自调用 → REQUIRED逻辑/物理 → rollback-only → REQUIRES_NEW连接 → checked exception → 异步 → 外部副作用。

30秒自调用：默认代理模式内层this调用绕过代理，内层传播不生效，但外层事务仍可能存在。

60秒自调用：从TransactionInterceptor入口解释拦截边界，用独立Bean代理调用对照；验证连接和数据，不只看注解。若outer已事务，inner参与的是outer执行范围，REQUIRES_NEW不会凭自身注解生效。

30秒回滚：默认RuntimeException/Error回滚，checked exception需明确规则；吞异常可能改变拦截器看到的结果。

60秒回滚：区分异常传播、回滚规则和已标rollback-only。外层捕获REQUIRED内层异常不清除标志，可能UnexpectedRollbackException；数据库回滚不撤销HTTP/消息副作用。

30秒传播：REQUIRED复用物理事务，REQUIRES_NEW独立提交，NESTED依赖保存点支持。

60秒传播：从业务是否允许独立提交出发选择，而不是为“事务不生效”盲目加新事务；明确连接池额外资源、内外状态一致性和补偿。深入追问隔离/线程边界时回到具体管理器与数据库。

## 10. 回忆、总结与来源

闭卷画invokeWithinTransaction流程；列三种“注解在但边界不对”；解释吞异常两种结果；实验先预测数据库行数再执行。

A级，核验2026-10-11。current reference用于通用原则，6.1.6源码用于本项目版本定位：

- [声明式注解](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)：proxy/self-invocation。
- [事务传播](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html)：物理/逻辑、保存点、连接资源。
- [回滚规则](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html)：默认规则与版本新增配置区分。
- [6.1.6 TransactionInterceptor](https://github.com/spring-projects/spring-framework/blob/v6.1.6/spring-tx/src/main/java/org/springframework/transaction/interceptor/TransactionInterceptor.java)：invoke。
- [6.1.6 TransactionAspectSupport](https://github.com/spring-projects/spring-framework/blob/v6.1.6/spring-tx/src/main/java/org/springframework/transaction/interceptor/TransactionAspectSupport.java)：invokeWithinTransaction、commit/complete。
