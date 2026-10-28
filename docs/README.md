# KnowFlow 文档中心

> CURRENT · 2026-10-24 · 当前文档入口，不把全部历史报告叫“最新版”。先看下表，再按功能查源码/证据。治理前目录保留于[历史快照](archive/2026-10/pre-governance/docs/README.md)。

## 接管顺序与权威分工

1. [AGENTS](../AGENTS.md)：工作区保护、架构唯一来源、测试安全与当前UI强制约束。
2. [VIBE_CONTEXT](../VIBE_CONTEXT.md)：快速产品背景和当前迭代。
3. [当前状态](roadmap/01_CURRENT_SYSTEM_AUDIT.md)：最近真实成果、阻断、历史与本轮验证边界。
4. [下一轮计划](roadmap/08_NEXT_EXECUTION_PLAN.md)：最小可执行任务；不从旧报告猜下一步。
5. [架构](PROJECT_ARCHITECTURE.md)与相关模块源码、[PRD](产品需求/PRD-功能需求文档.md)。

| 责任 | 唯一当前入口 | 状态 / 本轮依据 |
|---|---|---|
| 产品介绍/启动 | [根README](../README.md) | CURRENT，脚本/版本/模块源码核对 |
| 系统架构 | [PROJECT_ARCHITECTURE](PROJECT_ARCHITECTURE.md) | CURRENT，含6张真实/明确待补齐节点的Mermaid图 |
| 前端路由/依赖 | [FRONTEND_ROUTE_INVENTORY](FRONTEND_ROUTE_INVENTORY.md) | CURRENT，AST生成；静态依赖候选非运行调用认证 |
| 后端API/实体/DDL | [BACKEND_MODULE_INVENTORY](BACKEND_MODULE_INVENTORY.md) | CURRENT，注解/SQL生成，不代表迁移应用 |
| 产品需求 | [原PRD](产品需求/PRD-功能需求文档.md) | CURRENT，需求与实现/验证分离；不另造第二份PRD |
| 功能能力 | [roadmap/02](roadmap/02_PRODUCT_CAPABILITY_MATRIX.md) | CURRENT，66切片/源码/权限/存储/缺口/下一步 |
| 当前开发状态 | [roadmap/01](roadmap/01_CURRENT_SYSTEM_AUDIT.md) | CURRENT，本轮业务验证NOT_VERIFIED；发布NOT_READY |
| 风险/技术债 | [roadmap/03](roadmap/03_TECHNICAL_DEBT_AND_RISKS.md) | CURRENT，区分已证实缺口/静态风险/未验 |
| 当前Top10路线 | [roadmap/04](roadmap/04_MASTER_DEVELOPMENT_ROADMAP.md) | CURRENT，替代旧Phase G–K待办快照 |
| 依赖 / 首版范围 / 门禁 | [05](roadmap/05_PHASE_DEPENDENCIES.md)、[06](roadmap/06_V1_RELEASE_SCOPE.md)、[07](roadmap/07_TEST_AND_RELEASE_GATES.md) | CURRENT；范围仍待批准，门禁未认证 |
| 下一轮执行 | [roadmap/08](roadmap/08_NEXT_EXECUTION_PLAN.md) | CURRENT；不是重复实施已完成T1/G1/G2 |
| 工程开发 | [DEVELOPMENT_PROTOCOL](DEVELOPMENT_PROTOCOL.md) | CURRENT，分层/权限/事务/异常/调试/测试/Git |
| UI控件与字体 | [UI_DESIGN_SYSTEM_V2第0节及正文](UI_DESIGN_SYSTEM_V2.md#0-当前有效开发规范2026-10-24) | CURRENT，V3统一组件规范已合并；浅灰输入无蓝/灰聚焦外框；实现/验收分开 |
| 产品哲学/导航 | [UI_UX_SYSTEM](UI_UX_SYSTEM.md) | CURRENT，专项外观以V2现行规则为准 |
| 文档维护 | [DOCUMENTATION_GOVERNANCE](DOCUMENTATION_GOVERNANCE.md) | CURRENT，状态/证据/同步/归档/校验 |
| 交接历史 | [AGENT_HANDOFF](AGENT_HANDOFF.md) | HISTORICAL日志，保留原始结论；最后入口不覆盖当前基线 |

## 专项参考与历史证据

- 学习/内容：[learning-product阶段总结](learning-product/19_HASHMAP_PHASE_SUMMARY.md)、[Phase G历史工程报告](roadmap/13_PHASE_G_REAL_E2E_AND_TEST_REPORT.md)、[Phase H范围参考](roadmap/14_INTERVIEW_REMEDIATION_NEXT_PHASE_HANDOFF.md)。
- 题库/考试/闪卡：[Question Catalog](QUESTION_CATALOG_ARCHITECTURE.md)、[Exam](EXAM_SYSTEM_ARCHITECTURE.md)、[Flashcards](flashcards/FLASHCARD_V2_ARCHITECTURE.md)。REFERENCE，不自动认证现工作树。
- UI历史：[最终输入](borderless-forms/no-focus-frame/REPORT.md)、[确认弹窗](confirm-dialog/REPORT.md)、[教练字体](coach-typography/REPORT.md)、[收藏图标](favorites-icons/REPORT.md)。原测试日期/范围不可冒充本轮结果。
- AI 助手思考轨迹：[深度思考 + 折叠 UI + RAG/Agent 轨迹执行报告](ai-thinking/REPORT.md)。CURRENT，2026-10-25 本轮前后端与真实浏览器证据；聊天链路 Agent 工具轨迹端到端未接入，不按已验证计。
- 并行新增参考：[技术英语P2](tech-english/06_P2_EXECUTION.md)、[技术英语V3词库扩充](tech-english/07_VOCAB_EXPANSION_EXECUTION.md)、[技术英语P3跟读](tech-english/08_PRONUNCIATION_P3_EXECUTION.md)、[DatePicker单日期](ui-v2/DATE_PICKER_UI_V2_EXECUTION.md)；其验证属于原任务，不计本轮新业务验证。
- 日期时间组件：[Date & Time Picker V4](ui-v2/DATE_TIME_PICKER_V4_EXECUTION.md)（CURRENT，2026-10-25；年份/年月/日期/时分/时分秒/三种范围的显示与合同分离、面板几何与浏览器证据）。V2/V3 报告只证明当次场景。
- 部署/宿主安全：[部署模板](部署与接入/DEPLOY.md)、[Host能力安全](HOST_CAPABILITY_SECURITY.md)。不表示已部署、备份恢复或生产沙箱通过。
- ContentStudio教材与审核原文在content-studio；不因目录名production或技术PASS就视为人工批准/正式发布。

## 查找、归档与检查

- [完整文档索引](DOCUMENT_INDEX.md)：每份Markdown路径、标题、mtime、分类/权威级别、源码一致性检查状态、引用、重复组和处理建议。
- [迁移映射](DOCUMENT_MIGRATION_MAP.md)、[历史归档入口](archive/README.md)、[删除候选](DELETE_CANDIDATES.md)：本轮不删除原始证据，不让移动破坏旧入口。
- [治理执行结果](document-governance/REPORT.md)：计数、检查命令、已处理/未处理问题和最终PASS/PARTIAL边界。

当前状态看roadmap/01而不是从几百份日志求“最新”；模块报告的VERIFIED只属于其当次用例/环境。新旧冲突按源码→本轮实际验证→有效执行报告→历史→提案判定，不能用文档美化功能状态。

开发者组件中心：开发环境 `/dev/ui-components`，21个真实组件与Token计算值，详见[实施报告](component-playground/REPORT.md)。规范仍以UI_DESIGN_SYSTEM_V2为唯一入口，展示不等于全站验收。

### 2026-10-25 · 组件中心V6

[V6实施与验收](component-playground/v6/V6_DELIVERY_REPORT.md)：沿用现有三栏，Radio/Switch/Tabs/SearchInput公共实现、状态/证据分离与七个隔离业务Fixture；全局发布门禁仍NOT_READY。

### 2026-10-26 · V7–V8 17项学习功能规划

[17项学习功能规划入口](roadmap/learning-v7-v8/README.md)：真实源码审计、10个指定开源来源研究（Studyield原地址不可读/镜像单列）、17份规格、55项任务、架构/API/迁移/UI/性能与验收。仅文档规划，无新业务VERIFIED；权威矩阵/路线仍在roadmap/02、04、08。下一轮[只执行V70-T01](roadmap/learning-v7-v8/NEXT_CODEX_PROMPT.md)。

### 2026-10-26 · Java + AI 面试雷达训练

[实施与验收报告](interview-radar-v1/REPORT.md)：当前本人H265章/65待审题/5图/4参考索引，39可参考训练；原V2四维评分与首答/复述/雷达、原FSRS/错题/任务接入。来源审核/真实Provider/MySQL/全量Evidence失败仍单列，不提升发布门禁。

2026-10-26 产品优先开发已开始，首批F03/F16/F17逐切片验收。见[PRODUCT_MVP_01](roadmap/learning-v7-v8/executions/PRODUCT_MVP_01/IMPLEMENTATION_REPORT.md)。NOT_READY不变。
