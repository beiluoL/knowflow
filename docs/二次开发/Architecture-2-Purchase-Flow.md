# Architecture-2 · Purchase Flow（购买流程设计）

> 完整闭环：学习赚 Coin → 进 Store → 购买 → Entitlement → App 可用（§57/§184）。
> PurchaseService 是购买事务的唯一负责者（§90/§40）。

## 一、购买时序（§39）

```
User(前端 /apps)
  ↓ POST /api/store/purchase { productId }          ← 不含价格、不含 userId（§38/§102）
PurchaseService.purchase(userId, productId)
  ├─ 1. 查 product（服务端读价格 price_coin，§103）
  ├─ 2. 校验 product.status == ACTIVE（§133 PRODUCT_DISABLED 拒绝新购买）
  ├─ 3. 校验 entitlement 是否已拥有（ALREADY_OWNED 幂等拒绝）
  ├─ 4. 条件扣款 UPDATE wallet SET coin=coin-? WHERE user_id=? AND coin>=?（§105 防并发超扣）
  │       └─ 影响行数=0 → INSUFFICIENT_BALANCE（回滚）
  ├─ 5. 写 order_（order_no 唯一）+ order_item（price_coin 快照，§43/§80）
  ├─ 6. 写 entitlement（user_id+product_id 唯一）
  ├─ 7. 写 reward_ledger（-price_coin / PURCHASE_APP / source_id=orderId）
  └─ 8. 通知前端更新 UserApp（installed=true, enabled=true，§46）
      → 响应 { orderNo, productName, coin, balanceAfter }
```

**全部在一个 `@Transactional` 内（§40），任一步失败整体回滚——绝不出现「扣了 Coin 没给资格」的部分成功。**

## 二、商品定义（Product 与 App 分离，§34-35）

| product_key | name | type | app_key | price_coin | status |
| --- | --- | --- | --- | ---: | --- |
| feynman_pro | Feynman Pro | APP | feynman | 200 | ACTIVE（示例，最终以真实功能定价） |
| graph_pro | 知识图谱 Pro | APP | knowledge-graph | 150 | ACTIVE（示例） |
| coding_pro | 代码练习 Pro | APP | coding | 180 | ACTIVE（示例） |

- **只为真实存在的 App 建 Product（§143-144）**；默认 4 个 App（flashcard/quiz/mistake/focus）保持免费（§49），不建收费商品。
- MVP 商品数量 ≤ 5，不做 20 种类型（§36）。

## 三、状态组合（§132-133）

| App | Product | Entitlement | UserApp | 含义 |
| --- | --- | --- | --- | --- |
| ACTIVE | ACTIVE | ACTIVE | ENABLED | 正常可用 |
| ACTIVE | DISABLED | ACTIVE | ENABLED | 老用户可用，新用户不可购买（下架不收回资格） |
| ACTIVE | ACTIVE | — | — | 未购买，可购买 |
| — | — | ACTIVE | DISABLED | 拥有但停用（数据/资格保留，§47） |

## 四、幂等与并发

- **重复购买**：entitlement 唯一键 → 第二次返回 `ALREADY_OWNED`（不重复扣款）。
- **并发购买两个商品**：条件更新 `coin >= ?` 保证余额不足的商品失败（§28）。
- **订单号**：`order_no` 唯一（时间戳+随机），支持未来真实支付（§42）。

## 五、余额不足体验（§56）

```
余额不足
当前：120 Coin   需要：200 Coin   还差：80 Coin
[去学习赚取]  → 跳 Learning Center（闭环：学习→Reward→Coin→再购买，§57）
```

## 六、MVP 不包含（§185）

支付 / 充值 / 提现 / 转账 / 优惠券 / 退款 / 抽奖 / 订阅 / 第三方开发者 / Token Billing。
（Order/Entitlement 结构已为未来支付预留，§108，但本阶段不实现。）
