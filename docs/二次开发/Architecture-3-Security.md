# Architecture-3 · Security（平台安全边界）

> 第一版最大安全原则（§237）：**Store/Catalog 只能决定「展示什么能力」，永远不能决定「执行什么代码」。**

## 1. 可信来源矩阵（§289）

| 对象 | 可信来源 | 说明 |
| --- | --- | --- |
| App Key | **前端本地 Registry**（白名单，§236） | 后端/商店返回的 appKey 必须匹配本地 AppDefinition |
| Route | **前端已知本地路由**（白名单，§235） | 禁止远程返回任意 route（如 `javascript:`）并执行（§234） |
| Capability | AppDefinition.capabilities 元数据 | 不构成权限执行（§51/§65） |
| Product 价格 | **后端 product 表**（server-side truth，§127/§239） | 前端永不提交 priceCoin |
| Entitlement | **后端 entitlement 表**（§240） | 拥有判断不来自 coin>=price |
| UserApp | 后端 user_app（后端化后，§241-242） | localStorage 只作缓存，**不能决定权限**（§243） |
| App 可用性 | 后端（UserApp 后端化后） | 解决 Architecture-2 遗留 ③ APP_DISABLED 门禁（§47-48） |

## 2. 安全模型（§244）

```
Catalog（后端/前端数据）
   ↓ appKey 白名单校验
Local Registry（前端静态）
   ↓ route 白名单校验
Known Route → Built-in 组件（Vue Router）
```

- Store 返回的 appKey 不匹配本地 Registry → 不展示（丢弃）。
- AppDefinition.route 不匹配已注册本地路由 → 不可执行。

## 3. 攻击面分析（§238）

| 攻击 | 防护 |
| --- | --- |
| 恶意 route（javascript: 等） | route 只来自本地静态 AppDefinition；前端 router.push 仅接受白名单 path（§234-235） |
| 恶意 appKey | 后端 store 数据 → 前端按本地 registry 过滤（§236） |
| 恶意 price | 价格服务端读取，请求体忽略（§239，Architecture-2 已实现） |
| 恶意 productId / 越权购买 | SecurityContext userId + 单事务 + Entitlement 幂等（§77-83，Architecture-2 已实现） |
| 恶意 capability | Capability 仅是元数据，无执行路径（§65） |

## 4. 边界铁律（§293）

- Catalog **不能决定执行代码**。
- Product **不能决定权限**（价格≠资格）。
- UserApp **不能决定后端权限**（enabled ≠ Spring Security）。
- Capability **不能绕过 Spring Security**。

## 5. 未来第三方安全边界（§78-84/§294）

第三方 App 默认禁止：

```
wallet.write / reward.write / admin.* / user.other.* / 其他用户数据
```

- 第三方只能访问：**用户授权能力 + 当前用户数据**（§81）。
- 未来模型：`App → Signed Package → Sandbox`（§245），本阶段**不实现沙箱/签名/审核**（§246-248/§255）。

## 6. 第一版安全落地清单（实施阶段）

1. Store 商品数据前端按本地 registry 过滤 appKey（若后端未来返回更多）。
2. `router.push` 仅接受本地 AppDefinition.route 白名单（现状已天然满足——route 全静态）。
3. UserApp 后端化后，`APP_DISABLED` 校验由后端完成（商品购买前校验 App 可用性，§48）。
4. 禁止新增任何「用户可写 wallet/reward」接口（Architecture-2 已扫描通过，保持）。
