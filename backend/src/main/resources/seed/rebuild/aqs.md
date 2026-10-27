# AQS 与 ReentrantLock：状态、等待与取消

版本 java-ai-content-rebuild-v1；Java17/OpenJDK jdk-17+35；核验 2026-10-11。前置 JMM、CAS、线程中断、monitor。

## 01. 为什么需要同步器框架

冲刺从“队列 FIFO 是否等于公平锁”开始；深入从 state 的业务含义进入 tryAcquire/release、队列、Condition。两种模式共享正文。目标是能解释锁竞争与取消，而不是背一张旧 JDK8 waitStatus 图。

AQS 为基于单个 int state 的阻塞同步器提供排队、挂起、唤醒和取消框架。state 不是统一的“剩余资源数”：ReentrantLock 表示重入次数，Semaphore 表示许可等，具体语义由子类定义。

## 02. 核心结构与版本

volatile state 配合 CAS/state 访问方法；CLH 变体队列有 head/tail、prev/next、节点状态与 waiter。队列局部链接与阻塞机制不同于纯自旋 CLH 锁。

Java17 此固定源码采用 ExclusiveNode/SharedNode/ConditionNode、WAITING/COND/CANCELLED 和统一 acquire 内部循环。旧材料中的 acquireQueued、SIGNAL=-1 等 JDK8细节不能不注明版本直接套用。

## 03. 获取与释放

### 3.1 acquire 不是“先入队才能竞争”

公开 acquire 首先尝试子类 tryAcquire，成功即返回；失败才进入排队和等待路径。内部循环检查前驱、首位资格、尝试获取、CAS 入队、设置等待位、重新检查后 park。取消前驱需要队列清理。

释放调用 tryRelease；真正完全释放时通知后继。unpark 是许可/唤醒，不直接把锁所有权转交给线程；被唤醒还需再次竞争并成功更新 state。

### 3.2 独占与共享

独占一次由一个所有者持有；共享能允许多个成功获取者，tryAcquireShared 的负/零/正返回值表达失败及后续共享获取可能性。不要把共享模式等同读写锁的读锁，它也承载 Semaphore、CountDownLatch 等不同语义。

## 04. ReentrantLock 的映射

Sync 在 state 为 0 时 CAS 获取并登记 owner；同一 owner 再获取则增加计数，释放减少，归零才清 owner 并完全释放。使用 try/finally 解锁；非持有者 unlock 抛异常，不是“帮别人释放”。

公平锁通过排队前驱检查限制插队，但不保证 OS 调度公平。非公平策略可减少交接等待，吞吐收益取决于负载；不得伪造 benchmark。无参数 tryLock 即便在公平锁上也可以插队；带超时版本具有不同公平/中断契约。

## 05. Condition 是另一条等待链

await 的关键不是只 park：检查独占持有，加入条件链、保存 state、完全释放锁，然后等待通知/中断；节点转入同步队列后重新获取锁并恢复重入状态才返回。

signal 要持有锁，移动等待资格，不承诺线程已经执行。条件可能虚假唤醒，所以 await 必须放在检查业务谓词的 while 中。不同 Condition 可分离不同业务条件，不是多个锁。

```java
lock.lock();
try {
    while (!ready) changed.await();
    consume();
} finally {
    lock.unlock();
}
```

## 06. 中断、超时与取消

| 获取方式 | 训练边界 |
| --- | --- |
| lock | 等待获取，不因中断直接抛 InterruptedException |
| lockInterruptibly | 中断可取消获取 |
| tryLock | 立即尝试，不等待 |
| tryLock(timeout, unit) | 有界等待，可响应中断 |

取消节点不能永久阻塞后继；源码的 cleanQueue/cancelAcquire 处理链接和唤醒。不要把中断说成强制终止任意业务线程。传播中断或恢复标志必须结合调用协议；吞掉异常会破坏取消链。

## 07. 工程实验与故障排查

配套 `LockConditionRebuild`：主线程先持锁，子线程有界 tryLock 确定失败；验证重入计数2，最后解锁后为0。实验不证明公平吞吐或所有 Condition 竞争路径。

死锁教学场景：先拿线程转储，识别等待资源/持有者/调用栈，建立等待环；不要只凭 BLOCKED 或 WAITING 就断言死锁。ReentrantLock 等待常体现 park；监视器锁和 ownable synchronizer 要分别看。解决方案考虑统一锁顺序、缩小临界区、超时和失败补偿；超时不自动回滚已经完成的外部副作用。

## 08. 误区与连续追问

AQS队列FIFO → 为什么仍能插队 → 哪种tryLock破坏公平 → await释放几次重入 → signal后为什么不能马上执行 → 中断取消节点如何不堵后继 → 跨资源死锁如何取证。

:::warning
“队列有序所以锁公平”“unpark后就是owner”“signal已经消费完成”“state对所有同步器都表示锁次数”均错误。必须把框架机制与具体同步器策略分开。
:::

## 09. 三个标准回答

30秒AQS：它复用 int state、CAS 和等待队列，为同步器提供阻塞/唤醒/取消；子类定义获取释放语义。

60秒AQS：先 tryAcquire，失败组织节点与队列，重试后才 park；release 完全释放后通知后继，唤醒后仍要竞争。独占/共享有不同返回契约，ReentrantLock 把 state 映射为重入次数。深挖用固定Java17节点与取消流程，不背旧版字段。

30秒Condition：await 保存状态并完全释放锁，signal 使节点获得回同步队列的资格，重新获取锁后返回。

60秒Condition：条件链与同步队列用途不同，await 必须持锁，并在 while 内重查谓词；恢复重入计数后才继续业务。signal也必须持锁，不转移所有权，更不代表执行完成。

30秒公平：公平限制新线程越过排队前驱，但调度不保证公平，无参数tryLock仍可插队。

60秒公平：解释获取快速路径、前驱检查与重入例外，区分tryLock/超时tryLock/lockInterruptibly；工程上权衡尾延迟、吞吐和取消，不给无实测性能倍数。

## 10. 闭卷标准与参考

闭卷画两条队列；解释被唤醒和拥有锁区别；运行实验、读异常；给出死锁证据链。完成这些后自行决定章节完成，不因阅读打勾。

A级，Java17，核验2026-10-11：
- [AQS API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/AbstractQueuedSynchronizer.html)：子类方法、独占/共享。
- [ReentrantLock API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html)：公平、中断、tryLock。
- [Condition API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/Condition.html)：await/signal/虚假唤醒。
- [固定AQS源码](https://github.com/openjdk/jdk/blob/jdk-17%2B35/src/java.base/share/classes/java/util/concurrent/locks/AbstractQueuedSynchronizer.java)：acquire、release、cleanQueue、ConditionObject.enableWait/await。
- [固定锁源码](https://github.com/openjdk/jdk/blob/jdk-17%2B35/src/java.base/share/classes/java/util/concurrent/locks/ReentrantLock.java)：Sync、FairSync、NonfairSync。
