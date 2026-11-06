# Java 后端智能体入口

先读根 [AGENTS.md](../AGENTS.md)，再读 [后端规范](../docs/engineering/BACKEND_JAVA_STANDARD.md)。本文件从属根规则。

## 后端规则摘要

STATUS: FROZEN
VERSION: 2.0
DATE: 2026-11-05

正式名称：KnowFlow Modular Layered Architecture V2.0。采用 Package by Feature + Traditional Layered Architecture（业务模块优先、模块内部传统分层）。执行日期 2026-11-06；冻结规范不等于业务迁移完成。

规范权威顺序：根 AGENTS.md > backend/AGENTS.md > docs/engineering/BACKEND_JAVA_STANDARD.md > 架构文档 > scripts/engineering > 测试代码。事实证据另按源码/真实行为 > 本轮实际测试 > 有效报告 > 历史/提案判定；现有违规不能反向覆盖规范。

业务模块固定为：`identity learning interview exam coding flashcards mastery knowledge ai agent voice reward store catalog techenglish community productivity workbench administration apps`。不得任意拆分或新建全局业务目录。技术根包 common/config/exception/framework/security/util/utils/websocket/seed 保留职责；framework/web 不属于业务模板违规。

模块内按需创建 controller、dto、entity、mapper、service、service/impl、vo、contract、enums、constant、exception。Controller、Mapper、Entity、DTO、VO、Service、ServiceImpl 分别进入对应角色目录；专用子包按真实职责保留，但角色类仍归对应层（如 learning/service/actions）。禁止模块根目录堆积角色类，禁止 api/web/application/domain/infrastructure 作为业务模板。公开契约数据在 contract；不机械移回内部 dto/vo。

Controller 只处理请求、参数校验、服务调用、统一响应及必要转换，不得依赖 Mapper/JDBC、写 SQL 或声明业务事务。Service 负责业务规则、归属校验、事务及编排，不依赖 Controller。Mapper 只做本模块持久化，不编排业务。Entity 必须明确唯一表与模块归属，不直接作前端响应；DTO 为传输，VO 为响应投影，不承载持久化。

跨模块依赖通过 Contract：A Service → B contract → B Service → B Mapper。普通 Service 不自动成为公开契约；内部 Service 接口可以保留，多实现/替换/稳定契约/真实依赖倒置才需要接口，实现进入 service/impl，禁止空壳接口及重复实现。跨模块不得直接依赖 Entity、Mapper、ServiceImpl、内部 DTO/VO；Contract 不暴露 Entity、Mapper、Repository 实现、Wrapper 或内部 Domain 类型。Common 不能变成业务中转站。

Catalog 不获得来源题表第二所有权；Store 不拥有 Reward 钱包；Learning 不拥有 Knowledge/Flashcards/Identity 实体；Administration 管理权限不转移数据所有权。单表权威 Entity/Mapper 不重复创建。Contract 说明身份、权限、异常、空值、事务、幂等及副作用。

执行 ARCH-001～012：Controller 禁 Mapper/JDBC/事务；Service 禁 Controller；Mapper 模块所有权；Entity 不跨模块；禁跨 ServiceImpl；跨模块仅 Contract；Common 不依赖业务；业务模块无循环；角色按传统分层；根目录禁角色堆积。严格门禁报告存量，不新增永久豁免；历史 610 类 manifest 保留原路径→历史路径，V2 目标另列，不改写历史。

本批次只改规范、检查脚本和测试，不移动生产 Java、不改前端/Schema、不提交推送、不进入 Batch 2。后续批次需逐类台账、WIP 归属、权限/事务/API 回归及真实门禁证据。执行 `node scripts/engineering/check.mjs` 和相关 Maven 架构测试；FAIL/PARTIAL/NOT_VERIFIED/SKIPPED 不得写成 PASS。

代码质量（命名、格式化、注释、职责、事务、异常、交付流水线）以 [CODE_QUALITY_STANDARD](../docs/architecture/backend/CODE_QUALITY_STANDARD.md) 为准；格式化唯一工具为 Spotless + Google Java Format，只对本次改动文件执行 `-DspotlessFiles` 限定。

详细设计见 [架构规范](../docs/architecture/backend/ARCHITECTURE_STANDARD_V2.md)。docs 可按本地管理策略忽略；本入口及根规则包含可独立理解的核心约束。

Java 注释统一执行 `docs/engineering/BACKEND_JAVA_STANDARD.md` BJ-67（路径相对仓库根）：Entity/DTO/VO 全字段、record 组件、公共业务方法及复杂逻辑须说明真实业务约束；存量分批治理，禁止猜测或覆盖他人 WIP。
