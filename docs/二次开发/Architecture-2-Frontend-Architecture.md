# Architecture-2 · Frontend Architecture（前端架构）

> 复用 Architecture-1 的 `src/apps/` + `stores/apps.ts`；新增钱包/商店模块，遵循同一「静态数据 + Pinia + 组件」模式。

## 一、目录组织（在既有结构上叠加）

```
frontend/src
 ├─ apps/                    （Architecture-1 已有，复用）
 │   ├─ types.ts
 │   └─ registry.ts
 ├─ economy/                 （新增，钱包+商店）
 │   ├─ types.ts             WalletVO / LedgerVO / ProductVO / OrderResultVO
 │   └─ constants.ts         （文案映射：错误码→友好提示，§136）
 ├─ stores/
 │   ├─ apps.ts              （Architecture-1 已有）
 │   └─ wallet.ts            （新增：wallet 余额 + ledger 分页，Pinia 缓存）
 ├─ api/
 │   ├─ wallet.ts            （GET /wallet, GET /wallet/ledger）
 │   └─ store.ts             （GET /store/products, GET /store/owned, POST /store/purchase）
 ├─ components/
 │   ├─ apps/AppCard.vue     （Architecture-1 已有）
 │   └─ economy/
 │       ├─ WalletCard.vue   （余额/等级展示）
 │       ├─ LedgerList.vue   （流水，§130 不显示内部字段）
 │       ├─ ProductCard.vue  （商品卡片：价格/拥有态/兑换按钮）
 │       └─ CoinInsufficient.vue （余额不足引导，§56）
 └─ views/
     ├─ AppCenterPage.vue    （Architecture-1 已有，扩展「精选」区）
     └─ WalletPage.vue       （新增 /wallet，§128-129）
```

## 二、页面接入

| 页面 | 改动 |
| --- | --- |
| AppCenterPage（/apps） | 增加「精选/付费」区：ProductCard 列表（owned → 打开 / 未拥有 → 显示价格+兑换）；余额不足 → CoinInsufficient + [去学习赚取]（§56-57） |
| Home | 「我的应用」保持 enabledApps（§116）；可在学习区加轻量「今日 +20 XP +10 Coin」Toast（§125） |
| LearningCenter | 今日行动区可显示「今日获得 +20 XP +10 Coin」（只读，不重复整个 Wallet，§118） |
| Profile（我的） | 增加 WalletCard（Lv + XP 进度条 + Coin + 入口 奖励记录/购买记录，§119） |
| WalletPage（新增） | 钱包总览 + 最近流水（§128-129） |

## 三、状态流

```
购买（前端）
  ├─ POST /store/purchase {productId}
  ├─ 成功 → wallet store 刷新余额 + apps store install/enable + Toast「兑换成功，已加入我的应用」
  └─ 失败 → 错误码 → 友好文案（INSUFFICIENT_BALANCE 显示还差 X Coin）

奖励（前端只读）
  └─ Home/LearningCenter 完成动作后 → 后端入账 → 下次 /wallet 或事件后刷新展示
       （前端不再本地乐观加 exp，修 G7，§192 兼容：CheckIn.vue:211 内存累加需移除）
```

## 四、UI 原则（§124-130）

- 经济 UI 走「专业/学习工具/克制」语言：无老虎机、无抽奖动画、无氪金促销（§120-123）。
- 展示字段只含用户可理解文案：`+20 Coin · 完成章节`；禁止 `eventId/sourceType/rewardRuleId`（§130）。
- 颜色全部 `--kb-*` 令牌；Coin 用 `--kb-warning` 系、XP 用 `--kb-primary` 系（图标+文字双表达，不纯靠颜色）。
- 深色模式随令牌自动；390px 下单列。
