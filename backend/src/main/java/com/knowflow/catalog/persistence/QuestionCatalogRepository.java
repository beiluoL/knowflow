package com.knowflow.catalog.persistence;

import com.knowflow.catalog.vo.QuestionCatalogItem;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Catalog 数据访问层：集中存放本模块的原生 SQL 与执行细节。
 *
 * <p>目录页的「题库视图」是四类来源表的 UNION ALL 只读投影，用于一次查询完成跨来源的筛选、真实
 * 总数与确定性排序。来源表由 learning / coding 各自拥有，Catalog 不建立第二套 Entity 或 Mapper，
 * 因此这里以只读 SQL 投影访问，不写回来源表（唯一写入来源的语句是 `editBasic` 的基础字段更新，
 * 沿用迁移前语义）。
 *
 * <p>SQL 文本、参数顺序与排序规则与重构前完全一致，本类只做「位置搬移」，不改变查询语义。
 */
@Repository
@RequiredArgsConstructor
public class QuestionCatalogRepository {

  private final JdbcTemplate jdbc;

  /**
   * 题库视图：四类来源统一投影到同一列集。
   *
   * <p>列语义：`source_type` 区分来源，`source_question_id` 是来源表主键（不等同目录主键），
   * `canonical_key` 只对具备规范身份的来源有值。各来源的难度基数不同（旧版代码题 +1 归一），
   * 因此同时保留 `difficulty` 与 `source_difficulty` 两列。
   */
  private static final String BANK =
      """
SELECT 'QUIZ' source_type,q.id source_question_id,q.canonical_key,q.question_type,q.title,q.content search_text,q.category_id,q.topic_code,NULL knowledge_id,q.difficulty,q.difficulty source_difficulty,q.tags,q.status,q.review_status,q.version source_version,q.create_time,q.update_time FROM quiz_question q WHERE q.deleted=0 AND (q.question_type IS NULL OR q.question_type<>'CODE')
UNION ALL
SELECT 'CODE_PROBLEM',q.id,q.canonical_key,'CODE',q.title,q.content,q.category_id,q.topic_code,NULL,q.difficulty,q.difficulty,q.tags,q.status,q.review_status,q.version,q.create_time,q.update_time FROM quiz_question q LEFT JOIN code_problem c ON c.question_id=q.id AND c.deleted=0 WHERE q.deleted=0 AND q.question_type='CODE'
UNION ALL
SELECT 'SCENARIO',s.id,NULL,'SCENARIO',s.title,CONCAT(s.context_text,' ',s.question_text),NULL,NULL,s.knowledge_id,s.difficulty,s.difficulty,NULL,s.status,NULL,NULL,s.create_time,s.update_time FROM learning_scenario s WHERE s.deleted=0
UNION ALL
SELECT 'LEGACY_CODE',c.id,NULL,'LEGACY_CODE',c.title,c.description,NULL,NULL,NULL,c.difficulty+1,c.difficulty,c.tags,c.status,NULL,NULL,c.create_time,c.update_time FROM code_question c WHERE c.deleted=0
""";

  /** 真实总数；筛选条件已由调用方拼为 ` WHERE ...` 片段。 */
  public long count(String where, List<Object> args) {
    Long total =
        jdbc.queryForObject("SELECT COUNT(*) FROM (" + BANK + ") catalog" + where, Long.class, args.toArray());
    return total == null ? 0 : total;
  }

  /** 分页查询；排序固定为「创建时间倒序 → 来源 → 来源主键倒序」，保证翻页确定性。 */
  public List<QuestionCatalogItem> page(
      String where, List<Object> args, int size, long offset, RowMapper<QuestionCatalogItem> mapper) {
    List<Object> paged = new java.util.ArrayList<>(args);
    paged.add(size);
    paged.add(offset);
    return jdbc.query(
        "SELECT * FROM ("
            + BANK
            + ") catalog"
            + where
            + " ORDER BY create_time DESC, source_type ASC, source_question_id DESC LIMIT ? OFFSET ?",
        mapper,
        paged.toArray());
  }

  /** 规范身份相同的题目数量，用于提示重复录入。 */
  public long countByCanonicalKey(String canonicalKey) {
    Long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM (" + BANK + ") bank WHERE canonical_key=?", Long.class, canonicalKey);
    return count == null ? 0 : count;
  }

  /** 按来源与来源主键读取单行投影。 */
  public List<QuestionCatalogItem> findBySourceAndId(
      String source, long id, RowMapper<QuestionCatalogItem> mapper) {
    return jdbc.query(
        "SELECT * FROM (" + BANK + ") bank WHERE source_type=? AND source_question_id=?",
        mapper,
        source,
        id);
  }

  /** 算法题扩展数据主键；缺失时返回空列表。 */
  public List<Long> findCodeProblemStorageId(long questionId) {
    return jdbc.queryForList(
        "SELECT id FROM code_problem WHERE question_id=? AND deleted=0", Long.class, questionId);
  }

  /** 各知识点编码下的题目数，用于筛选面板计数。 */
  public List<Map<String, Object>> topicQuestionCounts() {
    return jdbc.queryForList(
        "SELECT topic_code,COUNT(*) question_count FROM ("
            + BANK
            + ") bank WHERE topic_code IS NOT NULL GROUP BY topic_code");
  }

  public <T> List<T> activeTopics(RowMapper<T> mapper) {
    return jdbc.query(
        "SELECT code,name,parent_code FROM question_topic WHERE deleted=0 AND status=1"
            + " ORDER BY sort_order,id",
        mapper);
  }

  public List<Map<String, Object>> categories() {
    return jdbc.queryForList("SELECT id,name FROM doc_category WHERE deleted=0 ORDER BY id");
  }

  public long countAudit() {
    Long total = jdbc.queryForObject("SELECT COUNT(*) FROM question_catalog_audit", Long.class);
    return total == null ? 0 : total;
  }

  public <T> List<T> pageAudit(int size, long offset, RowMapper<T> mapper) {
    return jdbc.query(
        "SELECT * FROM question_catalog_audit ORDER BY id DESC LIMIT ? OFFSET ?", mapper, size, offset);
  }

  /**
   * 对来源行加行锁并确认未逻辑删除。
   *
   * <p>表名来自内部枚举分支（固定白名单），不接受外部输入；加锁语句必须与随后的 `detail`
   * 读取处于同一事务，否则乐观锁判断会读到旧版本。
   *
   * @return 行存在且未删除时返回 true
   */
  public boolean lockSourceRow(String table, long id) {
    return !jdbc.queryForList(
            "SELECT id FROM " + table + " WHERE id=? AND deleted=0 FOR UPDATE", Long.class, id)
        .isEmpty();
  }

  public boolean existsDocCategory(long categoryId) {
    Long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM doc_category WHERE id=? AND deleted=0", Long.class, categoryId);
    return count != null && count == 1L;
  }

  public boolean existsActiveTopicCode(String code) {
    Long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM question_topic WHERE code=? AND deleted=0 AND status=1",
            Long.class,
            code);
    return count != null && count == 1L;
  }

  public boolean existsKnowledgeEntity(long knowledgeId) {
    Long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM kg_entity WHERE id=? AND deleted=0", Long.class, knowledgeId);
    return count != null && count == 1L;
  }

  /** 基础字段更新指令；字段为 null 表示该来源不更新此列。 */
  public record BasicUpdate(
      String table,
      String title,
      int difficulty,
      boolean updateTags,
      String tags,
      boolean updateClassification,
      Long categoryId,
      String topicCode,
      boolean resetReview,
      boolean updateKnowledge,
      Long knowledgeId,
      long id,
      LocalDateTime expectedUpdatedAt,
      boolean checkVersion,
      Integer expectedVersion) {}

  /**
   * 更新基础字段，并以「更新时间 + 版本号」做乐观并发控制。
   *
   * <p>WHERE 条件中的 `update_time IS NULL` / `version IS NULL` 分支与迁移前一致：来源表允许
   * 空值，直接等值比较会漏判，因此空值必须单独成条件。
   *
   * @return 实际影响行数；调用方据此判断是否发生并发冲突
   */
  public int updateBasic(BasicUpdate cmd) {
    List<Object> args = new java.util.ArrayList<>();
    StringBuilder sql = new StringBuilder("UPDATE ").append(cmd.table());
    sql.append(" SET title=?,difficulty=?,update_time=CURRENT_TIMESTAMP");
    args.add(cmd.title());
    args.add(cmd.difficulty());
    if (cmd.updateTags()) {
      sql.append(",tags=?");
      args.add(cmd.tags() == null ? "" : cmd.tags());
    }
    if (cmd.updateClassification()) {
      sql.append(",category_id=?,topic_code=?,version=COALESCE(version,0)+1");
      args.add(cmd.categoryId());
      args.add(cmd.topicCode());
      if (cmd.resetReview()) sql.append(",review_status='PENDING_REVIEW',status=0");
    }
    if (cmd.updateKnowledge()) {
      sql.append(",knowledge_id=?");
      args.add(cmd.knowledgeId());
    }
    sql.append(" WHERE id=? AND deleted=0");
    args.add(cmd.id());
    if (cmd.expectedUpdatedAt() == null) sql.append(" AND update_time IS NULL");
    else {
      sql.append(" AND update_time=?");
      args.add(Timestamp.valueOf(cmd.expectedUpdatedAt()));
    }
    if (cmd.checkVersion()) {
      if (cmd.expectedVersion() == null) sql.append(" AND version IS NULL");
      else {
        sql.append(" AND version=?");
        args.add(cmd.expectedVersion());
      }
    }
    return jdbc.update(sql.toString(), args.toArray());
  }

  /** 审计流水写入；与题目变更共用同一事务，保证「变更 + 留痕」原子。 */
  public void insertAudit(
      Long actorId, String source, Long id, String action, String before, String after) {
    jdbc.update(
        "INSERT INTO question_catalog_audit(actor_id,source_type,source_question_id,action,before_state,after_state,created_at) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)",
        actorId,
        source,
        id,
        action,
        before,
        after);
  }
}
