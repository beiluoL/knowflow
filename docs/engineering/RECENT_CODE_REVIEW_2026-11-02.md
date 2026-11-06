# 最近两天前后端代码规范审计与重构

2026-11-02；结论：NOT_VERIFIED（范围审计未全部完成，不能作为全部代码合规证明）。

## A. 范围与证据

分支 main、HEAD 99850cc7。执行了 status、branch、工作树/暂存区 diff、48 小时 all log、最近 10 条提交和未跟踪文件收集。最近 48 小时日志未给出可归属的业务提交；纳入未提交后端 18、前端 133、其中迁移 2、测试 8。具体清单见 RECENT_CODE_REVIEW_2026-11-02_SCOPE.json；均 TIME_UNCERTAIN，不把 mtime 当开发日期。工作树/暂存区包含前轮治理及其他开发改动，未覆盖、未重新暂存。审计前业务文件副本及 SHA256 保存在 /tmp/knowflow-review/baseline。未修改 InterviewGuide 或旧迁移工程。

## B. 问题与修复

| ID | 位置 | 规则 | 等级 | 证据/影响 | 处理 |
|---|---|---|---|---|---|
| R01 | InterviewRagGenerationService.update | BJ-12/BJ-31 | P1 | 先读 DRAFT，条件 UPDATE 可能影响 0 行，仍继续写 QuizQuestionMapper 并返回成功 | 检查行数，失败抛 409，阻止后续写 |
| R02 | 同上 | BJ-12 | P1 | 草稿与正式题库两次写无共同事务，第二次失败可能残留前次写 | 公共 Service 编辑入口加 rollbackFor=Exception 事务；真实回滚集成验证尚缺 |
| R03 | InterviewTrainerHub.vue onBeforeUnmount | FE 异步生命周期约束 | P2 静态风险 | 卸载只递增 pollToken；skills/caps/questions 序号未失效，未返回请求仍可能写组件状态 | 尚未修复；需组件回归及浏览器验证，不称已复现 |

R01 回归：新增 InterviewGenerationUpdateConcurrencyTest，模拟合法草稿读后条件更新为 0。修复前宿主执行退出 1：1 test/1 failure/0 error，明确“预期 BusinessException 但未抛出”。修复后相关测试第一次重跑遇到测试编译缺符号（日志 green.log），不能算修复通过；完整测试另行运行。未删除或放宽任何断言。

真实调用链：InterviewRagGenerationController 从 SecurityUtils 取 user → Service ownQuestion 限 user_id/deleted → 条件更新生成表 → QuizQuestionMapper 同步题库。未改变字段合同、Schema、Evidence/NextAction 或路由。

## C. 前端/UI 与静态范围

现有请求复用 apiGet/apiPost/apiPut/apiDelete；生成组件使用序号和 pollToken 防请求覆盖、已有 Loading/Empty/Error。完整范围执行关键词初筛 28 条命中，证据 /tmp/knowflow-review/static-hits.json；命中只标 KEYWORD_ONLY_NOT_DEFECT，未把注释或历史上下文判成新增缺陷。

ESLint 本轮 npm run lint 退出 0：172 current/172 historical/0 new，存量问题并未因此修复。133 文件尚未逐一完成语义审阅，范围矩阵 PENDING 保留，不宣称所有前端合规。未修改公共 UI 组件、Token、输入焦点合同；BROWSER_NOT_VERIFIED。

## D. 安全/AI/数据库

抽查生成 Controller 的认证身份来源、Service 的 owner SQL、请求参数 @Valid、Skill 只读注册表；生成服务验证 evidence 引用、隔离模型输出，并未把 AI 评价写成掌握度。上述是静态审阅，不等于完整权限矩阵运行通过。迁移有中文注释与逻辑关联，UNIQUE(user_id,canonical_key) 与软删除重复标题冲突仍是待评估风险，未擅改数据库。MySQL、真实模型、SSRF/上传/Tool Calling 未验证，不扩展到未涉及模块。

## E. 本轮修改

- InterviewRagGenerationService.java：局部事务与更新行数校验。
- InterviewGenerationUpdateConcurrencyTest.java：新增并发状态变化回归。
- 本报告与 scope JSON；AGENT_HANDOFF 追加记录。

## F. 验证

- 工程检查 --json：本轮退出 0（/tmp/knowflow-review/check.json）。
- ESLint：退出 0，172 历史/0 新增。
- 前端 npm test、双 vue-tsc、build：本轮重新执行，日志 /tmp/knowflow-final，各命令完成后以 .exit 为准，不复用上轮数字。
- Maven：相关重跑曾编译失败；宿主全量运行结果记录 /tmp/knowflow-review/maven-full.log/.exit；未收齐前 NOT_VERIFIED。
- diff/status：最终执行并保留证据。不提交、不推送、不新分支、不调整 exclude。

## G. 遗留与结论

已确认 2 个 P1 点做局部修复，但 R02 缺真实失败回滚测试，不能称并发一致性已完整验收。R03 为 P2 静态风险，未复现；范围内其他文件尚缺逐文件语义审阅。没有证据断言不存在其他 P0/P1。下一步仍是完成本次审计与回归，不进入新功能或 V1.2。

### 本轮最终确认追加

`mvn -B -f backend/pom.xml -Dtest=InterviewGenerationUpdateConcurrencyTest,InterviewRagGenerationServiceTest,InterviewTrainerMigrationTest surefire:test` 退出 0：8 tests/0 failure/0 error/0 skipped，14:32:18 完成。使用全量编译后的类，未跳过测试；修复前并发用例失败、修复后通过已证明。事务回滚集成仍未验证。

本轮前端 npm test：242 文件、1773 项通过，14:28:42 开始，退出 0；双类型与生产 build 均退出 0。ESLint 与工程检查退出 0，diff --check 退出 0。完整 Maven 仍在运行，不把局部 8 项当全量通过。最终结论仍 NOT_VERIFIED：151 文件只完成范围初筛与重点链路审阅，未完成全部语义审计，不能宣称本轮请求全部完成。

## 2026-11-02 V1.1 续审真实记录

保留前文历史结论。上轮 Maven 全量已结束：退出 1，1226 tests、0 failures、1 error、10 skipped；错误为 TutorHttpTest.threeModesUseOriginalSseAndHistoryAndFreezeModePerTurn 的 HttpTimeout。原日志及独立退出码存在 /tmp/knowflow-review/maven-full.*；没有再启动重复全量。

R02 新增 InterviewGenerationTransactionTest：真实 Spring 代理 + 隔离 H2，没有测试 @Transactional；第二次 Mapper 写注入异常，事务外新查询确认第一张表回滚、题库未变；移除注入后验证两张表提交；验证其他用户与已删除草稿不可编辑。Maven 指定该测试退出 0，日志 /tmp/knowflow-review-v11/transaction.log。故障注入仅用于第二次写，持久化与事务真实，不是 Mockito rollback() 断言；仅证明 H2，不证明 MySQL。并发真实双连接和 HTTP 409 集成仍未补齐。

R03：卸载后迟到 skills getter 仍被消费的组件测试先失败（5 pass/1 fail，退出 1），补卸载 skillsSeq/capsSeq/questionsSeq 递增后 6/6、退出 0。源码未改布局或 API。能力列表延迟读取同时覆盖；questions 独立状态副作用断言、A/B乱序、快速筛选及推荐/提交动作卸载仍需扩充，不宣称全场景通过。

本轮实际修改：InterviewGenerationTransactionTest.java、InterviewTrainerHub.vue、InterviewTrainerHub.spec.ts、Scope JSON、本报告和交接。scope 原151项保持，时间维度与审阅状态分离，3项记录本轮重点审阅，其余148项仍 PENDING，不以 lint/关键词充当语义审阅。新增事务测试是补充证据，尚未并入原时间范围。

结论仍 NOT_VERIFIED，不允许结束本次审计专项：151文件语义审查、完整事务并发矩阵、本轮完整前后端门禁和浏览器验证均未完成。上轮 Maven FAIL 不能称已排除或本轮引入；没有修改基线、未知业务修改、Schema、正式数据或 Git 分支/提交/推送。diff --check 本轮退出 0。
