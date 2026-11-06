# AI 面试中心 V2 增量执行记录

日期 2026-11-02。PARTIAL；不是第一、第二批完成验收。沿用既有工程规范、面试状态机和学习引擎。

## 实现/缺口矩阵

| 功能 | KnowFlow 现状 | InterviewGuide 参考 | 缺口 | 复用 | 优先级 |
|---|---|---|---|---|---|
| Skill | Registry/Controller/catalog 与 TrainerHub | modules/interview/skill | 真实启动流程验收 | 是 | P0 |
| RAG 出题 | generation Service、权限策略、引用校验与草稿 | knowledgebase/service/KnowledgeBaseQuestionGenerationService | 任务租约/超时恢复；历史软删除键 | 是 | P0 |
| 评分 | V2 + RadarRubric，4/3/2/1 权重已存在 | AnswerEvaluationService 转接 UnifiedEvaluationService | 可配置8.5阈值、历史重评和来源契约完整验收 | 是 | P1 |
| 报告 | InterviewReportAggregator + V2.report，混版不合并 | InterviewReportDTO/AnswerEvaluationService | 完整前端、学习映射闭环验收 | 是 | P1 |
| 简历/JD | 尚未完成本轮源码审计 | resume 模块 | 等前两批验收后复用文档解析/Skill映射，保留版本及用户确认 | 待核对 | P2 |
| 语音 | 本轮未核实既有能力 | voiceinterview | 等文字链稳定后复用V2，定义turn/request/音频序号与降级 | 待核对 | P3 |

参考项目真实存在于给定路径；只读查看 AnswerEvaluationService 的 DTO→统一评估适配流程，未复制代码、未改参考项目。其他参考路径部分仅发现文件，不冒充完整审阅。

## 本轮实际代码

InterviewRagGenerationService.remove：删除生成题时使用 deleted/user/id 的 SHA256 替换 canonical_key；正式题库使用 interview-gen/deleted/user/id 独立键，二者与逻辑删除在原 TransactionTemplate 内完成。条件更新不是1行则409，不继续写题库。保留旧记录，不恢复私有内容，不改Schema/API字段；删除行为仍不可见且禁用。重复生成日志移除题目原文，仅记录taskId。

InterviewGenerationTransactionTest 扩充删除后释放去重键断言；修复前退出1，明确 assertNotEquals 失败。修复后执行事务/并发/生成结构/H2迁移测试，结果见 /tmp/interview-v2/green.log 和独立退出文件。

限制：仅后续删除操作释放键，历史已删除数据未自动改写；重生成完整持久化链与并发删除/生成交错仍需测试，不能将键释放断言称完整端到端通过。无租约任务恢复尚未实施，因此未越过第一批门禁进入第二批开发。

## API、数据库与验收边界

沿用 DELETE /api/interview/generation/questions/{id}，身份仍 SecurityUtils→Service owner约束；无新接口、新表、DDL或第三方依赖。未触发付费模型。生产/用户数据未修改。未创建分支、worktree、commit或push，保留已有暂存及工作树。

真实 MySQL、模型/429/5xx、浏览器与Network：NOT_VERIFIED。原前端配置/草稿预览/引用/失败重试均保留，没有重做UI。全局发布仍NOT_READY。

未完成：第一批剩余恢复/超时、全链路权限与浏览器验收；第二批评分来源/版本化阈值和完整报告学习闭环；第三、第四批只保留依赖顺序，不开发。

### 本轮已收齐验证

后端指定四类测试：9项、0失败/0错误/0跳过，退出0。前端本轮 npm test：242文件/1774项，退出0（15:11:46开始）；双vue-tsc、生产build退出0。工程JSON检查与npm run lint退出0；git diff --check退出0。上述均本轮实际运行。完整后端、API全矩阵、真实MySQL/模型/浏览器未执行，不能记PASS。最终交付状态 NEEDS_FIX，本次需求尚未全部完成。
