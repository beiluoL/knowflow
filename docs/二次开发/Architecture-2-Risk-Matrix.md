# Architecture-2 · Risk Matrix（风险矩阵）

| 风险 | 等级 | 原因 | 解决方案 |
| --- | --- | --- | --- |
| 重复奖励（同事件多次入账） | P0 | 并发 / 重试 / 重复提交 | reward_ledger 唯一键（user_id, source_type, source_id, currency）+ 先查后插 + 每日上限 |
| 超扣 Coin（并发购买） | P0 | 两个商品同时购买、余额只够一个 | `UPDATE wallet SET coin=coin-? WHERE user_id=? AND coin>=?` 条件更新，行数=0 即回滚 |
| 购买部分成功（扣了钱没给资格） | P0 | 多步写库无事务 | PurchaseService 单 `@Transactional`（扣 coin+order+item+entitlement+ledger） |
| 商品价格篡改 | P0 | 前端提交 priceCoin / 改请求体 | 价格只从服务端 product 表读取；请求体忽略价格字段 |
| 奖励金额伪造 | P0 | 前端传 xp/coin | 无对外发奖 API；金额只来自 RewardRule |
| 越权访问他人钱包/订单/流水 | P1 | 信任请求体 userId | 一律 SecurityContext；查询强制 user_id=当前用户 |
| 老用户 XP 口径切换异常 | P1 | sys_user.exp ≠ stats.exp | wallet 惰性初始化种子=sys_user.exp；stats 改读 wallet；前端字段名不变 |
| 打卡奖励刷新丢失（G2/G7） | P1 | 前端乐观累加、后端不入账 | CHECK_IN 事件入账 + 移除 CheckIn.vue 内存累加 |
| 每日刷奖励（答题/复习高频） | P1 | 高频事件无上限 | dailyLimit 计数（user_id+event_type+date） |
| 数据孤儿（Entitlement/Order 悬空） | P1 | 删除用户未级联 | 跟随项目现有用户删除策略；逻辑外键 + 唯一约束 |
| 旧功能失效（回归） | P1 | 路由/接口改动 | 全部叠加式新增；旧 URL/API 不变；Regression 阶段全量回归 |
| 经济 UI 喧宾夺主 | P2 | 金币/动画过重 | 「专业/克制」设计语言；无抽奖/老虎机；余额不足引导去学习（§120-124） |
| 未来真实支付接入困难 | P2 | 无订单结构 | Order/OrderItem 已含价格快照 + 状态机，未来只加 Payment 模块（§108） |

## 风险优先级汇总（§176）

```
P0：Wallet/Ledger 一致性 · Purchase 并发 · Reward 幂等
P1：App Store · Entitlement · 前端状态同步 · 越权 · 每日上限
P2：UI 表达 · 未来支付预留
```
