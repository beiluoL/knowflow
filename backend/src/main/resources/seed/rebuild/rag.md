# RAG 完整链路：检索证据、生成边界与工程验证

版本 java-ai-content-rebuild-v1；核验2026-10-11。Java应用工程课程，不假装实现或压测了任何外部模型/Milvus集群。

## 01. 先定义问题与两种训练顺序

RAG用检索到的外部材料支撑生成，适合可更新知识和可追溯回答；不是向量数据库的别名，也不自动消除幻觉。2020原始论文的可训练DPR+BART模型与现代应用层检索增强不同，不能当完全相同架构。

冲刺：RAG为何需要 → 错答分层诊断 → 权限 → 评估 → 系统设计；深入：离线索引到在线问答完整链路。前置HTTP、文档版本、向量相似度、权限、流式响应。原四个RAG章保留，完整链路作为其评估章的升级阅读资料，不改DAG。

## 02. 离线：解析与切分

上传文件先校验类型/大小与归属，解析保留标题、页码、表格语义和来源定位。OCR/多栏/扫描件是不同质量风险，不能默认Tika成功就代表内容准确。

Chunk按内容边界与模型预算设计，不存在全场景最佳固定字数。过碎失去上下文，过大稀释相关信息；overlap缓解边界丢失但增加存储和重复召回。记录docId、version、chunkId、source定位、owner/ACL和时间，不能只有裸文本。

## 03. Embedding 与索引一致性

Query/Document向量应来自兼容模型及预处理，维度相同不代表语义空间兼容。换模型通常需重嵌入/重索引；同时验证维度、归一化、距离指标和索引版本。

Milvus保存向量和元数据，ANN检索配置权衡召回与延迟。Dense检索适合语义，BM25等词法检索可补专有名称/编号；新版Milvus全文/BM25能力有版本条件，不宣称KnowFlow当前已经接入。服务端过滤租户/资源权限，再返回候选；不能靠前端隐藏。

## 04. 在线：召回、混合与重排

Query经过可控改写后产生检索请求，Top-K/阈值、过滤条件、版本都要可观察。混合检索需融合不同检索分数/排序，不能将不同尺度裸分直接相加当科学比较。

Rerank在候选集合内重新排序，无法挽回根本没召回的证据。Parent-child检索可用小chunk匹配、大上下文补充，但引用必须仍可定位真实出处。多query提高覆盖也增加延迟/成本，应黄金集验证而非默认更好。

## 05. Context、Prompt 与答案

Context assembly 去重、保留来源标识、控制token预算并避免把低相关内容挤满窗口。用户问题和检索文档是不同信任层级；文档中的“忽略权限/调用工具”不是系统指令。

Prompt要求基于提供证据回答、缺失时说不能确定，并输出可校验citation。返回的引用要逐项对应检索材料的版本/片段，不能仅用URL装饰。No Answer需要覆盖无召回、召回但不支持回答、权限不足和依赖故障；服务故障不能伪装“知识不存在”。

## 06. Streaming 与故障治理

流式SSE改善首token体验，但不降低最终生成时间的所有因素。处理断连/取消、超时、限流、重试预算和终止事件；若部分答案已经输出，重试不得悄悄拼接成双份回答。

Embedding写入、向量upsert和索引版本更新应有幂等身份与可恢复状态。数据库文档成功不等于向量成功；删除旧文档需同步清理向量/撤销可检索版本。变更ACL要使查询过滤与缓存失效同步，避免旧候选权限串数据。

## 07. 错答诊断链

1. Parse：原文答案是否保留，页码/表格是否损坏？
2. Chunk：答案是否跨边界，标题关联是否丢失？
3. Embedding：模型/维度/指标/预处理是否兼容？
4. Index：最新版本是否真正写入，旧数据是否残留？
5. Query：改写是否丢掉否定或实体？
6. Retrieval：过滤过严/权限正确吗，候选排名在哪里？
7. Rerank：支持答案的片段是否被降权？
8. Context：证据是否被截断或重复挤占？
9. Generation：是否忽略证据，citation是否支持结论？

保留每层requestId、版本、候选与耗时，注意日志敏感数据脱敏。不能只调temperature当检索排障。

## 08. 评估不是“试着问一下”

黄金集包含问题、可访问身份、相关证据、参考事实、无答案/越权样例、版本。训练/调参和最终留出集区分，避免根据测试集反复选参数导致乐观分数。

| 指标 | 定义与边界 |
| --- | --- |
| Recall@K | 相关证据中被前K召回的比例；相关集标注要完整 |
| MRR | 每个问题首个相关结果排名倒数的平均；不反映全部相关证据 |
| nDCG@K | 折损相关收益除理想排序收益；需要明确等级标注 |
| Answer Correctness | 答案事实是否正确，不由Recall直接推出 |
| Faithfulness | 结论是否受给定证据支持，不等同事实真值 |
| Citation Accuracy | 引用定位是否支持相应结论 |

还测No Answer质量、权限泄漏、P95/P99、token成本和故障恢复。LLM judge有偏差，抽样人工核验；没有实际训练事件则能力显示未验证，不显示假成绩。

## 09. 实验、项目映射与标准回答

配套 `RagMetricsRebuild` 在手工黄金相关集上计算Recall@3与reciprocal rank，复现“证据召回充分也不等于答案正确”。这是离线指标实验，不调用Milvus/模型。业务链路验证另需真实依赖。

KnowFlow真实代码应逐项核对Document解析、Chunk/Embedding、Retriever、引用与权限路径；没有找到的模块标Not Available。项目面试用自己实际实现的链路作证据，不能把本课程架构方案当履历。

30秒RAG：检索外部可更新材料作为生成上下文，给答案可追溯证据；不是向量库，也不保证无幻觉。

60秒RAG：离线解析切分、兼容Embedding与版本索引；在线权限过滤、召回/融合/重排、上下文装配、生成引用与拒答；用黄金集分别检索和答案评估，另看成本延迟与故障恢复。

30秒错答：按解析到生成逐层定位，先找答案在哪一层丢失。

60秒错答：保留原文、chunk、候选、重排、prompt和引用；若召回为空看索引/模型/过滤，若已召回看截断与生成。重排不救无候选，调temperature不修权限。深挖用版本/ACL/取消与幂等证明工程边界。

30秒评估：召回、排序、答案正确和忠实度分开测，Recall高不是答案正确率高。

60秒评估：黄金集标注证据和事实，含无答案/越权/版本更新；Recall/MRR/nDCG测检索，correctness/faithfulness/citation测回答，留出集和人工抽检控制judge偏差，同时回归成本/延迟。

## 10. 来源与适用范围

核验2026-10-11；A级官方/原始论文。在线产品文档未固定SDK版本，仅支持流程和概念；实际接入必须固定部署版本，不照搬最新API声明已运行。

- [RAG原始论文](https://arxiv.org/abs/2005.11401)：2020，Methods/RAG-Sequence与RAG-Token，非现代所有管线的实现规范。
- [LangChain4j RAG](https://docs.langchain4j.dev/tutorials/rag)：EmbeddingStoreContentRetriever、QueryTransformer、ContentAggregator/Injector，当前在线文档。
- [Milvus Hybrid Search](https://milvus.io/docs/multi-vector-search.md)：多检索请求与融合/reranking，部署版本须复核。
- [Milvus过滤](https://milvus.io/docs/filtered-search.md)：标量过滤和候选约束；不能替代服务端身份校验。
- [Spring AI RAG](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html)：模块化RAG流程，非本项目已安装框架事实。
