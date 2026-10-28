# KnowFlow 当前产品需求基线（PRD）

> CURRENT · 2026-10-24 · 延续原PRD入口，治理前v1.3保留于[历史快照](../archive/2026-10/pre-governance/docs/产品需求/PRD-功能需求文档.md)。本文件描述需求及源码实现状态，不是产品验收或发布批准。

## 1. 产品、用户与首版范围

KnowFlow面向技术学习者，首要场景是Java与Java+AI工程成长、自主学习、知识整理、主动回忆、间隔复习、刷题考试与AI面试辅助。当前开发目标为普通用户PC工作台；迁出桌面/移动/小程序不计本仓库首版交付。

核心价值：从学习目标找到可读内容，在真实练习/复习后保留可追溯记录，再展示有置信度的诊断和下一步。AI是可选辅助，不能用演示数据、随机分数或未经批准内容伪装学习效果。

首版范围为**PROPOSED / 待产品批准**，见[唯一发布范围](../roadmap/06_V1_RELEASE_SCOPE.md)；未得到实际批准前不能声称READY。本轮只治理文档，不授权实现下列新任务或修改正式内容。

## 2. 角色与授权合同

| 用户/职责 | 源码身份 | 权限合同 |
|---|---|---|
| 普通学习用户 | sys_user的USER | 本人笔记/卡片/答卷/任务/会话等Owner；其他用户不可越权 |
| 管理员 | ADMIN | 已有后台路由与服务端策略；管理员身份不允许绕过资源/审核业务门禁 |
| 知识库成员 | kb_member的EDITOR/READER | 读写按DocumentAccessPolicy与继承规则，不是新系统角色 |
| 内容编辑/审核人员 | 工作流职责，当前依赖已有ADMIN/资源权限及人工批准记录 | 未发现独立系统REVIEWER/EDITOR全局RBAC角色，不在文档虚构权限 |
| 匿名访客 | 未登录 | 只访问服务端明确允许的公开内容/公开分享；前端页面公开不等于API匿名可写 |

统一验证Anonymous、Owner、Other User、Admin，涉及知识库再加EDITOR/READER；UI隐藏不是授权。

## 3. 数据正确性与异常合同

1. 阅读/书签/人工勾选不等于真实掌握；无学习事件不生成Evidence，无证据不伪造“状态良好”。
2. Evidence样本上限、时间衰减、置信度独立，NextAction/路由盖章使用原唯一入口，不让页面自行推荐或拼假深链。
3. 技术审阅不等于人工批准，作者草稿目录名production不等于正式发布；PENDING映射不等于ACCEPTED。
4. AI评分不等于客观成绩；模拟分数不能作为真实评估、事件或掌握度输入。面试活动完成不是能力达标。
5. 复测分差不自动代表能力提升；同题/快照/内容版本/规则/条件一致才可标为客观可比，原答不覆盖。
6. FSRS到期由原服务端算法与真实评分决定，前端不改排程；普通SM2/笔记复习不能冒称均已FSRS化。
7. 本人卡、笔记、答卷、会话严格隔离；资源撤权、逻辑删除、改版本后重新验证来源权限，不信任客户端userId。
8. 数字空值不变0；保存revision/409与幂等requestId保持；旧NULL快照不自动补造成可靠历史，错误complete_time不凭猜测清空。
9. Loading/Success/Empty/Error/Retry分别展示；AI不可用、Judge pending、网络失败不能伪装成功/空数据。
10. 不可信执行默认关闭；宿主运行/危险确认不是生产沙箱。密钥、生产权限、付费Provider和人工内容批准单独授权/验收。

## 4. 需求与核心用户流程

MUST=首版批准范围内的硬要求；SHOULD=重要但可按门禁延期；COULD=扩展。状态依[能力矩阵](../roadmap/02_PRODUCT_CAPABILITY_MATRIX.md)：IMPLEMENTED仅指源码可追踪，本轮运行验证一律NOT_VERIFIED。需求状态不是实现验收。每行给出用户故事、流程/输入输出、权限/异常、验收和源码追踪。

| 需求ID / 优先级 / 当前状态 | 用户价值与故事 | 流程 / 输入 → 输出 | 权限 / 异常 | 验收标准 / 代码与能力 |
|---|---|---|---|---|
| PRD-01 MUST IMPLEMENTED | 学习者希望安全进入本人工作区 | 注册/登录→me→导航；凭据→会话 | USER/ADMIN；删除后注册不继承旧身份，401/409明确 | 正反凭据/退出/失效/Owner-Other；AuthController、Login；KF-001–007 |
| PRD-02 MUST IMPLEMENTED | 学习者知道目标与当前章节 | 选目标/路径→继续章节；goal/path→上下文路由 | 路径/章节可读；失败可重试，禁止假深链 | 上下文仅传页面真正消费参数，刷新/重登继续；learning Store/route/action-contract；KF-008–013 |
| PRD-03 MUST IMPLEMENTED | 阅读者保留真实位置和进度 | 开章节→滚动/阅读→保存→恢复 | 本人进度；旧错误complete_time保留待人工规则，不猜修 | 来源版本变化/撤权/保存失败/重登；ChapterLearn/LearningController；KF-009 |
| PRD-04 MUST IMPLEMENTED | 学习者通过真实专项练习发现错误 | 选可读已审题→作答→快照/事件/错题 | 题目访问与来源审核；旧NULL快照明确不可比 | 重试不重复记录、题目改版保留旧答；Quiz/Mistake；KF-024/025/028/030 |
| PRD-05 MUST IMPLEMENTED/PARTIAL | 学习者可信地组卷和继续考试 | 规则组卷→paper快照→draft revision→提交→评分/诊断 | 本人答卷/私有来源；409/重复交卷/AI pending | 客观规则评分正确、草稿冲突不覆盖、未认证题型不报最终成绩；ExamController/service、exam页面；KF-026–029 |
| PRD-06 MUST IMPLEMENTED | 学习者主动回忆并按到期复习 | 本人卡→翻面→四档评分→原FSRS→历史/设置 | 卡片Owner/来源可读；requestId/revision/settingsRevision冲突 | 四评分、重试、时区、到期、失败留卡；FSRS原服务；KF-031–034 |
| PRD-07 MUST IMPLEMENTED/PARTIAL | 学习者理解诊断和下一步依据 | 事件→批准映射→Mastery；前端Evidence→NextAction→任务 | 无证据不造结论；证明任务不能手动伪完成 | 原链一致、低样本置信度诚实、afterCommit失败可解释；KF-010–014 |
| PRD-08 MUST IMPLEMENTED | 学习者管理知识与服务端笔记 | 文档读取/上传→笔记编辑→revision保存→版本/回收站/迁移 | 知识库角色/笔记Owner；409/撤权/账号切换/图像失败 | 可靠保存、本人恢复、跨账号隔离、旧本地显式迁移；NotesWorkspace/NoteController/NoteService；KF-015–020 |
| PRD-09 SHOULD IMPLEMENTED/PARTIAL | 学习者用图谱定位真实资源 | 分类/实体/技术/概念模式→搜索→内容入口 | 真实图数据可读；AI输出标来源、费用/失败诚实 | 图谱布局/缩放/重试无假边；大图独立门禁；KnowledgeGraph/KnowledgeController；KF-021–023 |
| PRD-10 SHOULD IMPLEMENTED/PARTIAL | 学习者获得可停止、可追溯AI辅助 | 配置模型→知识关联→对话/SSE→保存→教练/会话管理 | 本人会话/公开分享确认与撤销；网络/取消/额度失败 | 真实Provider与权限独立验收；重新生成/反馈目前DISABLED；Chat/AIComposer/AiService；KF-035–040 |
| PRD-11 SHOULD PARTIAL | 学习者写作/费曼/场景训练得到诚实反馈 | 草稿→生成/评价→保存/导出 | 写作仍本地草稿；模拟回退不可称客观成绩 | 去除误导性随机分数或明确关闭/标注，不能写入Mastery；SmartWriting/FeynmanLab；KF-041–043 |
| PRD-12 SHOULD IMPLEMENTED/PARTIAL | 求职者面试后找到明确补练与复测 | 原session/turn→回答/评价→不足→真实练习→原题复测 | Owner/可读已审资源；评价来源/规则/条件明确 | 原回答不覆盖、任务证据可追踪；完整不足闭环仍待Phase H合同；InterviewV2/Bridge；KF-044–046 |
| PRD-13 SHOULD IMPLEMENTED/DISABLED | 学习者安全阅读和编辑代码 | 题库→工作区草稿→保存；执行另经能力门禁 | 本人草稿/隐藏答案保护；Host与Judge默认禁用 | 编辑保存可用、执行状态诚实；生产沙箱未认证不得开启；CodeWorkspace/HostGuard；KF-047–050 |
| PRD-14 COULD IMPLEMENTED | 学习者按批准范围使用辅助工具 | 专注/习惯/日历/英语/宫殿/绘图/社区 | 各专业模型权限，不把所有行为算客观学习证据 | 路由/存储/错误逐项验收；未批准不营销为首版；KF-051–053、KF-063–065；英语P3未开工，真实模型/录音未本轮验 |
| PRD-15 MUST PARTIAL/BLOCKED | 内容负责人只发布有来源、已批准资源 | 作者稿→技术审阅→人工批准/去重→canonical/version/SHA→真实KG语义审阅/批准→受控导入→资源/映射回读 | 审核职责非虚构新角色；humanApproved=false必须阻断 | 首次/重入/改版/撤权/回滚/历史保护通过才发布；原ContentStudio/Catalog；KF-054–057 |
| PRD-16 MUST PARTIAL/NOT_VERIFIED | 运维能安全升级、恢复和回滚 | 隔离演练→候选冻结→迁移/备份恢复→发布监控 | 显式安全配置，测试隔离不等于生产安全 | 目标MySQL版本、数据对账、回滚/HTTPS/监控/负责人证据齐备；KF-058–062 |
| PRD-17 MUST IMPLEMENTED/PARTIAL | 用户在一致UI中无干扰完成操作 | 浅灰无外框字段→对齐表单→轻菜单/确认 | 不改变业务绑定/快捷键；按钮和菜单保留键盘语义 | 共享单日期DatePicker见KF-066；不把range/datetime写成已完成。按[UI第0节](../UI_DESIGN_SYSTEM_V2.md#0-当前有效开发规范2026-10-24)验收；并非所有旧表单均迁移 |

每个需求的具体源码/API/测试线索在对应KF能力行和[路由表](../FRONTEND_ROUTE_INVENTORY.md)、[后端表](../BACKEND_MODULE_INVENTORY.md)；不是只有页面就算完成。

## 5. V1纳入、条件与延期

MUST候选：认证/授权、知识/笔记、目标路径与阅读连续性、规则练习/考试/错题、本人闪卡/FSRS、真实事件/任务最小链、必要管理审核与安全迁移。批准范围内出现演示CRUD、随机评价或越权则阻断对应入口。

条件开放：真实AI对话/教练、图谱AI、面试、写作等，必须明确支持的Provider/费用/质量/权限及降级。真实ASR/TTS、危险执行、安全在线Judge、MCP协议、全部辅助工具和跨端能力不自动纳入。写作云草稿、全站旧布局结构升级是独立未完成范围，不以普通笔记/公共UI已改造代替。

## 6. 首要未完成需求与发布门禁

- HashMap人工批准、q01/q04复用合并、真实KG语义审阅，以及新作者包受控导入成功/重入/撤权/回滚合同。
- 目标MySQL新字段/旧数据/多实例DDL和恢复回滚演练；历史5.7局部测试不认证8或正式库。
- 批准范围下完整角色/API/浏览器失败矩阵、关键后台演示与写作模拟回退收口、任务/事件投影对账。
- 生产配置/成本/密钥/日志/告警/回滚负责人；候选快照全量验证。当前NOT_READY，不由本PRD批准发布。

实施顺序、估算与依赖见[当前路线图](../roadmap/04_MASTER_DEVELOPMENT_ROADMAP.md)和[下一轮计划](../roadmap/08_NEXT_EXECUTION_PLAN.md)。

## 7. 变更与验证

功能新增同步KF能力行、相关PRD流程/验收和测试状态；架构变更同步架构与迁移/配置；UI按唯一UI规范更新；完成后同步当前状态、路线图和交接。技术审阅/运行验证/人工批准分别保留责任人与版本，不覆盖原证据。

本轮仅源码/文档审计，未执行产品业务E2E、权限认证、数据库升级或真实Provider。历史原始结果在状态文档标日期/环境，不能把它们写成本轮VERIFIED。

### 2026-10-24 Checkbox V3增量

公共Checkbox及63处原生控件迁移已实现；20px正方形、无鼠标阴影、键盘轮廓保留，闪卡准备行对齐。仅组件合同与四宽度/双主题等浏览器范围已验证，不扩大为全功能VERIFIED；英语测试及Date/TimePicker类型旧失败另行修复。规范以UI_DESIGN_SYSTEM_V2第8.1节为准，结果见[Checkbox报告](../checkbox-v3/REPORT.md)。

### 2026-10-25 UI V3规范同步（仅文档）

V3统一视觉/布局规则已合并到[现行UI规范](../UI_DESIGN_SYSTEM_V2.md)第3/4/5/8节，覆盖Button/Icon/字体/表单/Checkbox/布局/状态和验收。普通输入浅灰、无蓝灰聚焦外框合同保留；Button阴影、loading占位、gap与尺寸变体差异待渐进实施。仅文档校验，不新增功能VERIFIED，不改变发布NOT_READY。下一步先收口公共Button，再按真实页面回归。

### 2026-10-25 UI组件展示与公共校准

开发专用`/dev/ui-components`已实现，21个真实组件、局部数据、计算样式/Token检查；Button与Input优先校准。仅指定自动检查和浏览器场景已验，全站历史自绘控件及P1/P2完整交互仍未认证；不改变全局NOT_READY。结果和下一步见[展示中心报告](../component-playground/REPORT.md)。

### 2026-10-25 · 组件开发中心V6增量

开发专用组件中心新增4个公共源码（Radio/Switch/Tabs/SearchInput），实现状态与七维质量证据分离；七个业务场景仅本地Fixture，未写学习/笔记/AI业务记录。数量、范围及本轮实际验证见[V6交付](../component-playground/v6/V6_DELIVERY_REPORT.md)。不替代业务App Registry，不改变全局NOT_READY、普通笔记服务端主库或SmartWriting本地草稿边界。后续优先完善Tooltip/Popover/Combobox定位与焦点，再接真实业务使用方。

### 2026-10-25 · Java + AI 面试雷达来源导入切片

PRD-12增量：Java+AI面试雷达要求同号合并保留全部来源，当前开发库本人账号为用户指定目标。已实现私有来源教材/待审题导入切片；10分四维评分、首轮保护、8.5通关、模拟面试与真实雷达仍未完成，旧1–5自评不可替代。 详细实现、测试与未验证项见[阶段报告](../interview-radar-v1/REPORT.md)。

2026-10-25 本任务后续更新：用户本人会话已真实导入当前文件型H2开发库，路径29新增65章/48待审题，报名后章节1270可读、0/65完成；原库关闭状态备份在`/private/tmp/knowflow-radar-pre-import-20261025`，未导入历史成绩。8份文件名/正文题号冲突和3组答案冲突待核定；严格训练/雷达未完成，维持PARTIAL/NOT_READY。最终验证边界以本任务报告为准。

### 2026-10-26 · V7–V8 17项学习功能规划（需求登记，PROPOSED）

将F01–F17完整候选纳入本PRD的规划附件：[17项需求映射](../roadmap/learning-v7-v8/01_FEATURE_MATRIX_17.md)、[独立规格入口](../roadmap/learning-v7-v8/README.md)。需求包括自适应路径、Teach-back、私人测验、面试复测、渐进提示、图谱掌握度、微挑战、错因、变式、阅读微课、Java评测、证据报告、场景面试、计划重排、交错练习、三模式Tutor和课程资料学习包。

永久F编号不改变现有模块事实；本轮只规划，未将需求当代码完成。F16统一编排、F02评价、F05提示明确分工；F17首个可学习结果30秒仅在限定文本资料/模型/并发条件下作为待验证目标，完整包SLA须真实P50/P95、成本和失败数据后批准。历史回答/首轮成绩不可覆盖，阅读完成不当掌握度，AI分/自评与客观分量纲分离。


### 2026-10-26 · 雷达训练与实际增量验收

PRD-12雷达当前实现增量：复用V2已接入本人来源训练与四维10分、首轮保护、8.5本轮通关、复述/变式及五种模拟；既有FSRS/错题/任务与实测雷达已接入。来源题全部65待审，39可经明确确认参考训练，26暂停待核定；没有自动人工批准、虚构首答或覆盖学习数据。尚缺内容全面审核、真实Provider质量/MySQL运行门禁，故需求整体PARTIAL/发布NOT_READY。来源缺失课程段落保留待补充，不用模板冒充原文。见[2026-10-26报告](../interview-radar-v1/REPORT.md)。


### 2026-10-26 · V70-T02学习证据基础合同验收增量

F12/原学习闭环基础可区分冻结原答与读时统计、已知首条与追踪起点；无法证明原始首轮时明确UNKNOWN，不以空表/剩余ID补造。有效0/100保留、旧NULL不当0，AI/自评/客观量纲分离；条件或历史规则未知不输出可比分差。重试、晚到评分、必要事务与撤权分页已完成限定验收；不等于F12/F04全部功能或V7已完成。完整后端保留既有闪卡Fixture错误，不称全绿；MySQL/生产/付费质量未认证，NOT_READY。见[独立报告](../roadmap/learning-v7-v8/executions/V70-T02_REPORT.md)。

### 2026-10-26 · PRODUCT_MVP_01 / F03隔离产品切片

真实源码新增私人3题测验草稿确认、冻结题版、复用ExamGrader判分、私人不可覆盖答卷及原NotesAiPanel交互。独立存储只承载公共Quiz缺失的owner私域，不写公开题库；来源经NoteService owner及DocumentAccessPolicy复验。AI调用复用AiService，闭卷练习仅发题干/选项，生成预览暴露条件明确。PRIVATE_QUIZ_SUBMITTED只记录事实，未接管Evidence/NextAction/FSRS或自动写知识映射。默认开关关闭。首批最新状态见下方2026-10-27收口记录，NOT_READY不变。详见产品执行目录docs/roadmap/learning-v7-v8/executions/PRODUCT_MVP_01/。

2026-10-27 PRODUCT_MVP_01：A/F03、B/F16、C/F17三个最小纵向切片DONE；完整高级规格仍PARTIAL。私人测验保存/冻结/客观判分/新旧历史，原Chat三模式Tutor及来源/逐轮模式持久化，受控TXT/Markdown/文本PDF异步学习包、分项重试、编辑采纳与原FSRS均已实施。真实DeepSeek合成资料链路及浏览器刷新/重登/四宽度通过；前端223文件1710项、类型/build通过。后端最终结果见执行目录TEST_REPORT.md，既有888001 Fixture红项不隐藏。六张新增表仅在隔离H2验证迁移与备份恢复；生产/MySQL未认证，开关默认关闭，T01/T02保持DONE、T03/T04仍未完成，全局NOT_READY。下一批F02/F05/F07/F10。


### 2026-10-27 第二批基础切片收口

2026-10-27 PRODUCT_MVP_02：F05/F02/F07/F10基础切片DONE，完整高级规格仍PARTIAL、NOT_READY。原Tutor/Chat/SSE扩展提示、严格来源评价与选区锚点；单题复用私题/原答卷/ExamGrader；显式确认才追加原笔记。新增三表开关默认关闭，H2验证不认证生产/MySQL。真实同源合成闭环16次调用（授权上限17）及刷新重登通过；前端1715项/类型/build通过，后端全量1165仅既有888001错误，最终修复相关独立验收。详见PRODUCT_MVP_02报告，下一批F04/F08/F09/F13。
