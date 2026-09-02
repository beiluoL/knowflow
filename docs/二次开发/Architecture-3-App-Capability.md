# Architecture-3 · App Capability（能力声明设计）

> Capability = 「App 能调用什么 Core 能力」的**元数据声明**；**不是** Spring Security 权限、不是沙箱执行（§49-52/§65）。

## 1. 现状（真实）

- `AppDefinition.capabilities: AppCapability[]` 已存在（Architecture-1 types.ts）。
- 枚举值：knowledge.read / knowledge.write / mastery.read / review.read / review.write / learning-event.write / learning-path.read / learning-path.write / ai.chat。
- **当前仅元数据，无任何权限联动、无沙箱**——保持（§51）。

## 2. Capability 与权限区分（§50）

| 维度 | App Capability | Spring Security Permission |
| --- | --- | --- |
| 主语 | App（能力声明） | 用户（身份权限） |
| 作用 | 声明 App 调用哪些 Core 能力 | 控制用户能访问哪些 API |
| 第一版 | 仅 metadata（§51） | 既有 JWT + 注解（不动） |

- **Capability 永远不能绕过 Spring Security**（§293：Security Matrix）。

## 3. 各 App 能力声明（§61-64，真实建议）

| App | capabilities |
| --- | --- |
| flashcard | knowledge.read, mastery.read, review.read, review.write, learning-event.write |
| quiz | mastery.read, learning-event.write |
| mistake | mastery.read, review.read |
| focus | learning-event.write |
| feynman | knowledge.read, learning-event.write |
| cornell | knowledge.read, knowledge.write |
| memory-palace | knowledge.read, mastery.read, learning-event.write |
| knowledge-graph | knowledge.read, mastery.read |
| coding | knowledge.read, learning-event.write |
| mindmap / drawing / notes | knowledge.read, knowledge.write |
| ai-writing | ai.chat |

（当前 registry 已含以上大部分；如需对齐可在实施阶段微调，本阶段不改代码。）

## 4. 高危能力默认禁止（§83-84/§294）

```
wallet.write / reward.write / admin.* / user.other.* / community.manage
```
- 任何 App（含未来第三方）默认**不允许**声明/获取（§79-84/§294）。
- 官方内置 App 也不存在上述能力——经济层只由 RewardEngine/PurchaseService 操作（§57-58）。

## 5. 未来第三方信任模型（§178-181）

```
第三方 App → AppManifest.capabilities[]（白名单）→ Core API（Capability-aware）
```
- 第三方只能访问：**用户授权的能力 + 当前用户数据**（§81）。
- 第一版：全官方 Built-in，无真实授权弹窗（§112）；Capability 白名单结构未来随 Developer 平台启用（§82）。

## 6. 第一版结论

- 保持 `AppDefinition.capabilities` 为纯元数据（已达标）。
- 不建 Capability 表、不建沙箱、不建 API Gateway（§180/§182）。
- 唯一相关的后端动作：**UserApp 后端化后，APP_DISABLED 门禁可基于后端 App 可用性判断**（§47-48）——这是 Capability 元数据之外、真正需要后端的地方。
