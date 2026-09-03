-- 适用：已部署的 MySQL 8 生产库（新库直接执行 db/mysql/schema.sql + data.sql 即可，无需本脚本）
-- 目的：新增场景练习内容表 learning_scenario 并灌入演示场景（Architecture-4.0-4 Scenario Lab）
-- 说明：CREATE TABLE IF NOT EXISTS 幂等；种子用 INSERT ... SELECT ... WHERE NOT EXISTS 守卫按标题去重，重复执行不产生脏数据。
--       Flyway 未启用时，可在「数据库设置」页或手动执行本脚本为存量库补表补数据。
--       存量库已有 learning_path / kg_entity 等表；knowledge_id 为逻辑外键（可空），此处种子不关联，避免悬挂引用。

CREATE TABLE IF NOT EXISTS learning_scenario (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  title         VARCHAR(200) NOT NULL COMMENT '场景标题，如「线上 Full GC 频繁排查」',
  context_text  TEXT NULL COMMENT '情景背景描述',
  question_text VARCHAR(1000) NOT NULL COMMENT '要用户回答的问题',
  key_points    TEXT NOT NULL COMMENT '要点清单 JSON：[{label, keywords[]}]',
  difficulty    INT DEFAULT 1 COMMENT '难度 1-3',
  knowledge_id  BIGINT NULL COMMENT '关联知识点ID（逻辑外键 kg_entity.id，可空）',
  status        INT DEFAULT 1 COMMENT '1 上架 / 0 下架',
  create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted       INT DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
  KEY idx_lsc_status (status),
  KEY idx_lsc_knowledge (knowledge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO learning_scenario (title, context_text, question_text, key_points, difficulty, knowledge_id, status)
SELECT '线上 Full GC 频繁，如何定位与排查？', '应用某时刻起告警不断：Full GC 频次升高、老年代占用持续爬升，接口 RT 明显变长，偶发接近 OOM 的前兆告警。你是当班工程师，需给出从观察、取证到定位、处置的完整排查思路。', '请描述你会按什么顺序排查 Full GC 频繁：用到哪些工具、每个工具解决什么问题、常见根因与处置手段。', '[{"label":"现象确认与 GC 日志","keywords":["gc日志","gc log","gclog","fullgc次数"]},{"label":"jstat 观察回收与堆","keywords":["jstat","老年代","ygc频次"]},{"label":"jmap 导出堆转储","keywords":["jmap","堆转储","heap dump"]},{"label":"MAT 分析对象引用链","keywords":["mat工具","支配树","retained heap"]},{"label":"定位根因并处置","keywords":["内存泄漏","大对象","调参","分代调整"]}]', 2, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM learning_scenario WHERE title = '线上 Full GC 频繁，如何定位与排查？');

INSERT INTO learning_scenario (title, context_text, question_text, key_points, difficulty, knowledge_id, status)
SELECT '接口变慢疑似慢 SQL，如何定位优化？', '核心列表接口近期 RT 上升明显，数据库 CPU 飙升，DBA 怀疑存在慢 SQL，但暂不清楚是哪一条。', '请给出从发现慢 SQL 到验证优化效果的完整链路：日志怎么开、执行计划看什么、索引怎么调、SQL 怎么写。', '[{"label":"开启慢查询日志","keywords":["慢查询日志","slow query log","long_query_time"]},{"label":"explain 看执行计划","keywords":["explain","执行计划","扫描行","rows"]},{"label":"索引与回表检查","keywords":["回表","覆盖索引","联合索引","最左前缀"]},{"label":"改写并验证","keywords":["深分页","延迟关联","limit","验证"]}]', 1, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM learning_scenario WHERE title = '接口变慢疑似慢 SQL，如何定位优化？');

INSERT INTO learning_scenario (title, context_text, question_text, key_points, difficulty, knowledge_id, status)
SELECT '缓存穿透、击穿、雪崩如何预防？', '高并发读接口依赖 Redis 缓存，最近出现大量请求直接打到数据库的现象，团队想系统性加固缓存链路。', '请分别说明缓存穿透、缓存击穿、缓存雪崩的成因与应对方案，并说明如何保证缓存与数据库的一致性。', '[{"label":"穿透：空值缓存与布隆过滤器","keywords":["布隆过滤器","空值缓存","bloomfilter"]},{"label":"击穿：互斥锁与逻辑过期","keywords":["互斥锁","单飞","逻辑过期"]},{"label":"雪崩：过期打散与多级缓存","keywords":["过期时间打散","随机过期","多级缓存","限流"]},{"label":"一致性思考","keywords":["延迟双删","binlog","canal","最终一致"]}]', 2, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM learning_scenario WHERE title = '缓存穿透、击穿、雪崩如何预防？');

INSERT INTO learning_scenario (title, context_text, question_text, key_points, difficulty, knowledge_id, status)
SELECT '大促秒杀瞬时流量，如何保障稳定？', '大促开启瞬间百万级用户同时抢购，既要防止超卖，又要保证核心链路不被瞬时流量打垮。', '请设计秒杀链路的稳定性方案：入口怎么做、流量怎么削、库存怎么扣、异常怎么兜底、上线前怎么验证。', '[{"label":"入口限流削峰","keywords":["限流","令牌桶","sentinel","削峰"]},{"label":"MQ 异步化","keywords":["消息队列","rocketmq","kafka","异步下单"]},{"label":"库存扣减原子性","keywords":["lua","原子扣减","乐观锁","分布式锁"]},{"label":"幂等与兜底","keywords":["幂等","重试","补偿","库存回滚"]},{"label":"压测与监控","keywords":["压测","容量评估","全链路监控","告警"]}]', 3, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM learning_scenario WHERE title = '大促秒杀瞬时流量，如何保障稳定？');
