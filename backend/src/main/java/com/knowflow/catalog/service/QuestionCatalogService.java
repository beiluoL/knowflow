package com.knowflow.catalog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.knowflow.catalog.dto.QuestionCatalogBasicEditRequest;
import com.knowflow.catalog.util.QuestionEditToken;
import com.knowflow.catalog.vo.QuestionCatalogAuditVO;
import com.knowflow.catalog.vo.QuestionCatalogItem;
import com.knowflow.catalog.vo.QuestionCatalogTopicVO;
import com.knowflow.coding.contract.CodeProblemCatalogPort;
import com.knowflow.coding.contract.LegacyCodeCatalogQueryPort;
import com.knowflow.common.PageResult;
import com.knowflow.exception.BusinessException;
import com.knowflow.learning.contract.LearningQuestionCatalogQueryPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * 题目目录服务：把 learning / coding 四个来源的题目聚合成统一的只读目录。
 *
 * <p>来源表由各模块拥有，Catalog 不建立第二套 Entity 或 Mapper；目录视图用一次 UNION ALL
 * 查询完成跨来源筛选、真实总数与确定性排序，避免内存合并分页导致的总数与排序偏差。
 *
 * <p>唯一写回来源表的操作是 {@link #editBasic} 的基础字段更新，沿用迁移前语义。
 *
 * <p>并发控制：编辑以「行锁 + 更新时间 + 版本号」三重条件判定，任一不匹配即 409， 防止并发编辑互相覆盖。
 */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class QuestionCatalogService {
  private final QuestionMutationAudit audit;
  private final QuizQuestionAdminService quizAdmin;
  private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
  private final JdbcTemplate jdbc;
  private final LearningQuestionCatalogQueryPort learningQuestions;
  private final LegacyCodeCatalogQueryPort legacy;
  private final CodeProblemCatalogPort code;
  private static final Set<String> SOURCES =
      Set.of("QUIZ", "SCENARIO", "LEGACY_CODE", "CODE_PROBLEM");
  private static final String BANK =
      """
SELECT 'QUIZ' source_type,q.id source_question_id,q.canonical_key,q.question_type,q.title,q.content search_text,q.category_id,q.topic_code,NULL knowledge_id,q.difficulty,q.difficulty source_difficulty,q.tags,q.status,q.review_status,q.version source_version,q.create_time,q.update_time,NULL source_storage_id FROM quiz_question q WHERE q.deleted=0 AND (q.question_type IS NULL OR q.question_type<>'CODE')
UNION ALL
SELECT 'CODE_PROBLEM',q.id,q.canonical_key,'CODE',q.title,q.content,q.category_id,q.topic_code,NULL,q.difficulty,q.difficulty,q.tags,q.status,q.review_status,q.version,q.create_time,q.update_time,c.id source_storage_id FROM quiz_question q LEFT JOIN code_problem c ON c.question_id=q.id AND c.deleted=0 WHERE q.deleted=0 AND q.question_type='CODE'
UNION ALL
SELECT 'SCENARIO',s.id,NULL,'SCENARIO',s.title,CONCAT(s.context_text,' ',s.question_text),NULL,NULL,s.knowledge_id,s.difficulty,s.difficulty,NULL,s.status,NULL,NULL,s.create_time,s.update_time,NULL source_storage_id FROM learning_scenario s WHERE s.deleted=0
UNION ALL
SELECT 'LEGACY_CODE',c.id,NULL,'LEGACY_CODE',c.title,c.description,NULL,NULL,NULL,c.difficulty+1,c.difficulty,c.tags,c.status,NULL,NULL,c.create_time,c.update_time,NULL source_storage_id FROM code_question c WHERE c.deleted=0
""";

  /**
   * 题库视图的显式投影列。
   *
   * <p>不用 `SELECT *`：列顺序与集合一旦变化会静默影响行映射，显式列出可在编译期之外 由 SQL 审查发现漂移，也避免把未来新增的内部列带到接口层。
   */
  private static final String CATALOG_COLUMNS =
      "source_type,source_question_id,canonical_key,question_type,title,search_text,category_id,"
          + "topic_code,knowledge_id,difficulty,source_difficulty,tags,status,review_status,"
          + "source_version,create_time,update_time,source_storage_id";

  /**
   * 分页查询目录。
   *
   * <p>关键字在标题、正文、标签三列做不区分大小写的模糊匹配，并对 `! % _` 转义， 避免用户输入被当作通配符。
   */
  public PageResult<QuestionCatalogItem> page(
      int page,
      int size,
      String keyword,
      String type,
      String source,
      Integer difficulty,
      Integer status,
      String review,
      Long category,
      String topicCode) {
    if (page < 1 || size < 1 || size > 100)
      throw new BusinessException(400, "分页范围为 page>=1、pageSize 1–100");
    if (source != null && !source.isBlank() && !SOURCES.contains(source))
      throw new BusinessException(400, "未知题目来源");
    List<Object> args = new ArrayList<>();
    StringBuilder where = new StringBuilder(" WHERE 1=1");
    if (keyword != null && !keyword.isBlank()) {
      where.append(
          " AND (LOWER(title) LIKE ? ESCAPE '!' OR LOWER(search_text) LIKE ? ESCAPE '!' OR"
              + " LOWER(tags) LIKE ? ESCAPE '!')");
      String value =
          "%"
              + keyword
                  .trim()
                  .toLowerCase(Locale.ROOT)
                  .replace("!", "!!")
                  .replace("%", "!%")
                  .replace("_", "!_")
              + "%";
      args.add(value);
      args.add(value);
      args.add(value);
    }
    filter(where, args, "question_type", type);
    filter(where, args, "source_type", source);
    filter(where, args, "difficulty", difficulty);
    filter(where, args, "status", status);
    filter(where, args, "category_id", category);
    filter(where, args, "topic_code", topicCode);
    if ("UNKNOWN".equals(review)) where.append(" AND review_status IS NULL");
    else filter(where, args, "review_status", review);
    String from = " FROM (" + BANK + ") catalog" + where;
    Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, args.toArray());
    List<Object> paged = new ArrayList<>(args);
    paged.add(size);
    paged.add(((long) page - 1) * size);
    List<QuestionCatalogItem> rows =
        jdbc.query(
            "SELECT "
                + CATALOG_COLUMNS
                + from
                + " ORDER BY create_time DESC, source_type ASC, source_question_id DESC LIMIT ?"
                + " OFFSET ?",
            this::row,
            paged.toArray());
    fillDuplicateCounts(rows);
    PageResult<QuestionCatalogItem> result = new PageResult<>();
    result.setRecords(rows);
    result.setTotal(total == null ? 0 : total);
    result.setPageNum(page);
    result.setPageSize(size);
    result.setPages((result.getTotal() + size - 1) / size);
    return result;
  }

  private static void filter(StringBuilder sql, List<Object> args, String column, Object value) {
    if (value != null && !(value instanceof String s && s.isBlank())) {
      sql.append(" AND ").append(column).append("=?");
      args.add(value);
    }
  }

  private QuestionCatalogItem row(ResultSet r, int index) throws SQLException {
    QuestionCatalogItem v = new QuestionCatalogItem();
    String source = r.getString("source_type");
    long id = r.getLong("source_question_id");
    v.setCatalogId(source + ":" + id);
    v.setSourceType(source);
    v.setSourceQuestionId(id);
    String canonical = r.getString("canonical_key");
    v.setCanonicalEvidence(canonical != null && !canonical.isBlank());
    v.setCanonicalKey(v.isCanonicalEvidence() ? canonical : v.getCatalogId());
    v.setQuestionType(r.getString("question_type"));
    v.setTitle(r.getString("title"));
    v.setCategoryId(number(r, "category_id", Long.class));
    v.setDifficulty(number(r, "difficulty", Integer.class));
    v.setSourceDifficulty(number(r, "source_difficulty", Integer.class));
    v.setStatus(number(r, "status", Integer.class));
    v.setReviewStatus(r.getString("review_status"));
    v.setSourceVersion(number(r, "source_version", Integer.class));
    v.setTags(split(r.getString("tags")));
    Long knowledge = number(r, "knowledge_id", Long.class);
    v.setKnowledgePointIds(knowledge == null ? List.of() : List.of(knowledge));
    String topic = r.getString("topic_code");
    v.setKnowledgePointCodes(topic == null ? List.of() : List.of(topic));
    v.setCreatedAt(
        r.getTimestamp("create_time") == null
            ? null
            : r.getTimestamp("create_time").toLocalDateTime());
    v.setUpdatedAt(
        r.getTimestamp("update_time") == null
            ? null
            : r.getTimestamp("update_time").toLocalDateTime());
    String path =
        switch (source) {
          case "QUIZ" -> "/admin/quiz";
          case "SCENARIO" -> "/admin/scenarios";
          case "LEGACY_CODE" -> "/admin/code-questions";
          default -> "/admin/code-problems";
        };
    v.setEditorPath(path + "?questionId=" + id);
    v.setRestrictions(
        source.equals("SCENARIO")
            ? List.of("场景目前不能直接加入普通组卷；原场景训练流程保留")
            : source.equals("LEGACY_CODE")
                ? List.of("旧版代码执行不直接加入考试；其浏览器执行/统计体系独立，不能当作安全Judge")
                : List.of());
    if (source.equals("CODE_PROBLEM")) {
      // 扩展数据主键已随 UNION 的 LEFT JOIN 一并投影，无需再按行查询；
      // 缺失时语义不变：仅保留基础编辑能力，并给出需要修复扩展关系的原因。
      Long storageId = number(r, "source_storage_id", Long.class);
      if (storageId == null) v.setRestrictions(List.of("缺少算法扩展数据；仅基本信息可编辑，专业编辑/判题/组卷不可用，需要修复扩展关系"));
      else v.setSourceStorageId(storageId);
    }
    v.setCapabilities(
        source.equals("QUIZ") || source.equals("CODE_PROBLEM")
            ? List.of("EDIT", "EXAM", "PRACTICE", "REVIEW", "PUBLISH")
            : List.of(
                "EDIT", source.equals("SCENARIO") ? "SCENARIO_PRACTICE" : "LEGACY_CODE_PRACTICE"));
    if (source.equals("CODE_PROBLEM") && v.getSourceStorageId() == null)
      v.setCapabilities(List.of("EDIT"));
    return v;
  }

  @SuppressWarnings("unchecked")
  private static <T> T number(ResultSet r, String column, Class<T> cls) throws SQLException {
    Number n = (Number) r.getObject(column);
    return n == null
        ? null
        : (T) (cls == Long.class ? (Object) n.longValue() : (Object) n.intValue());
  }

  private static List<String> split(String tags) {
    return tags == null
        ? List.of()
        : Arrays.stream(tags.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .distinct()
            .toList();
  }

  /**
   * 批量填充规范身份的重复计数。
   *
   * <p>原实现对每条规范记录单独执行一次 `COUNT(*)`，一页 20 行会产生最多 20 次额外查询。 这里改为对本页的规范键做一次 `IN (...) GROUP BY`
   * 聚合，查询次数从 O(页大小) 降为 O(1)； 计数口径与逐条查询完全一致（同一个题库视图子查询，同一个 canonical_key 等值条件）。
   *
   * <p>页内没有规范记录时不发查询（与原来的零次查询一致）。
   */
  private void fillDuplicateCounts(List<QuestionCatalogItem> rows) {
    List<String> keys =
        rows.stream()
            .filter(QuestionCatalogItem::isCanonicalEvidence)
            .map(QuestionCatalogItem::getCanonicalKey)
            .distinct()
            .toList();
    if (keys.isEmpty()) return;
    String placeholders = String.join(",", java.util.Collections.nCopies(keys.size(), "?"));
    Map<String, Long> counts = new HashMap<>();
    jdbc.query(
        "SELECT canonical_key,COUNT(*) AS duplicate_count FROM ("
            + BANK
            + ") bank WHERE canonical_key IN ("
            + placeholders
            + ") GROUP BY canonical_key",
        rs -> {
          counts.put(rs.getString("canonical_key"), rs.getLong("duplicate_count"));
        },
        keys.toArray());
    for (var item : rows)
      if (item.isCanonicalEvidence())
        item.setDuplicateCount(counts.getOrDefault(item.getCanonicalKey(), 0L));
  }

  /**
   * 目录项详情：目录投影 + 来源模块的编辑投影。
   *
   * <p>来源投影通过 contract 取得，仅用于计算编辑令牌；令牌不包含答案正文。
   */
  public Map<String, Object> detail(String catalogId) {
    var identity = parse(catalogId);
    var rows =
        jdbc.query(
            "SELECT "
                + CATALOG_COLUMNS
                + " FROM ("
                + BANK
                + ") bank WHERE source_type=? AND source_question_id=?",
            this::row,
            identity.source(),
            identity.id());
    if (rows.isEmpty()) throw new BusinessException(404, "题目不存在");
    fillDuplicateCounts(rows);
    Object payload =
        switch (identity.source()) {
          case "QUIZ" -> learningQuestions.findQuiz(identity.id());
          case "SCENARIO" -> learningQuestions.findScenario(identity.id());
          case "LEGACY_CODE" -> legacy.findDetail(identity.id());
          default ->
              rows.get(0).getSourceStorageId() == null
                  ? learningQuestions.findQuiz(identity.id())
                  : code.adminDetail(identity.id());
        };
    rows.get(0).setEditToken(token(payload));
    return Map.of("item", rows.get(0), "payload", payload);
  }

  private String token(Object value) {
    return QuestionEditToken.of(objectMapper, value);
  }

  public record Identity(String source, long id) {}

  public static Identity parse(String value) {
    if (value == null || !value.matches("(QUIZ|SCENARIO|LEGACY_CODE|CODE_PROBLEM):[1-9][0-9]*"))
      throw new BusinessException(400, "题目身份必须包含来源与ID");
    String[] p = value.split(":");
    try {
      return new Identity(p[0], Long.parseLong(p[1]));
    } catch (NumberFormatException e) {
      throw new BusinessException(400, "题目ID超出范围");
    }
  }

  @org.springframework.transaction.annotation.Transactional(
      isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  /**
   * 更新题目基础字段。
   *
   * <p>流程：入参校验 → 解析身份 → 来源行加锁 → 重读投影 → 令牌/版本校验 → 分类与知识点 存在性校验 → 条件更新 → 写审计。任一步失败整体回滚，不留半更新状态。
   *
   * <p>加锁与重读必须处于同一事务，否则乐观锁比较的是过期快照。
   */
  public void editBasic(String catalogId, QuestionCatalogBasicEditRequest req) {
    validateBasics(req);
    var identity = parse(catalogId);
    String lockedTable =
        switch (identity.source()) {
          case "SCENARIO" -> "learning_scenario";
          case "LEGACY_CODE" -> "code_question";
          default -> "quiz_question";
        };
    if (jdbc.queryForList(
            "SELECT id FROM " + lockedTable + " WHERE id=? AND deleted=0 FOR UPDATE",
            Long.class,
            identity.id())
        .isEmpty()) throw new BusinessException(404, "题目不存在");
    var detail = detail(catalogId);
    var item = (QuestionCatalogItem) detail.get("item");
    assertNoConcurrentEdit(identity, req, item);
    java.time.LocalDateTime expected = parseExpectedUpdatedAt(req);
    if (!Objects.equals(item.getUpdatedAt(), expected))
      throw new BusinessException(409, "题目已变更，请重新加载后编辑");
    List<Object> args = new ArrayList<>();
    args.add(req.title().trim());
    args.add(identity.source().equals("LEGACY_CODE") ? req.difficulty() - 1 : req.difficulty());
    String sql = "UPDATE ";
    String table =
        switch (identity.source()) {
          case "SCENARIO" -> "learning_scenario";
          case "LEGACY_CODE" -> "code_question";
          default -> "quiz_question";
        };
    sql += table + " SET title=?,difficulty=?,update_time=CURRENT_TIMESTAMP";
    if (!identity.source().equals("SCENARIO")) {
      sql += ",tags=?";
      args.add(req.tags() == null ? "" : req.tags());
    }
    if (identity.source().equals("QUIZ") || identity.source().equals("CODE_PROBLEM")) {
      if (req.categoryId() != null
          && jdbc.queryForObject(
                  "SELECT COUNT(*) FROM doc_category WHERE id=? AND deleted=0",
                  Long.class,
                  req.categoryId())
              != 1) throw new BusinessException(400, "知识库不存在");
      String topic =
          req.topicCode() == null || req.topicCode().isBlank() ? null : req.topicCode().trim();
      if (topic != null
          && jdbc.queryForObject(
                  "SELECT COUNT(*) FROM question_topic WHERE code=? AND deleted=0 AND status=1",
                  Long.class,
                  topic)
              != 1) throw new BusinessException(400, "知识点编码不存在或已停用");
      sql += ",category_id=?,topic_code=?,version=COALESCE(version,0)+1";
      args.add(req.categoryId());
      args.add(topic);
      if (identity.source().equals("CODE_PROBLEM")
          || requiresReview((JsonNode) detail.get("payload"))) {
        sql += ",review_status='PENDING_REVIEW',status=0";
      }
    } else if (identity.source().equals("SCENARIO")) {
      List<Long> knowledge = req.knowledgePointIds() == null ? List.of() : req.knowledgePointIds();
      if (knowledge.size() > 1) throw new BusinessException(400, "场景目前仅支持一个知识点");
      Long knowledgeId = knowledge.isEmpty() ? null : knowledge.get(0);
      if (knowledgeId != null
          && jdbc.queryForObject(
                  "SELECT COUNT(*) FROM kg_entity WHERE id=? AND deleted=0",
                  Long.class,
                  knowledgeId)
              != 1) throw new BusinessException(400, "知识点不存在");
      sql += ",knowledge_id=?";
      args.add(knowledgeId);
    }
    sql += " WHERE id=? AND deleted=0";
    args.add(identity.id());
    if (expected == null) sql += " AND update_time IS NULL";
    else {
      sql += " AND update_time=?";
      args.add(java.sql.Timestamp.valueOf(expected));
    }
    if (identity.source().equals("QUIZ") || identity.source().equals("CODE_PROBLEM")) {
      if (req.expectedSourceVersion() == null) sql += " AND version IS NULL";
      else {
        sql += " AND version=?";
        args.add(req.expectedSourceVersion());
      }
    }
    if (jdbc.update(sql, args.toArray()) != 1) throw new BusinessException(409, "题目已变更，请重新加载后编辑");
    audit.record(
        identity.source(),
        identity.id(),
        "EDIT_BASIC",
        "version=" + item.getSourceVersion(),
        "title/difficulty/tags/classification updated");
  }

  /** 基础字段入参校验；错误码与校验顺序与重构前保持一致。 */
  private static void validateBasics(QuestionCatalogBasicEditRequest req) {
    if (req.title() == null
        || req.title().isBlank()
        || req.title().length() > 200
        || req.difficulty() == null
        || req.difficulty() < 1
        || req.difficulty() > 3) throw new BusinessException(400, "标题不能为空且不超过200字，难度必须为1–3");
    if (req.tags() != null && req.tags().length() > 500)
      throw new BusinessException(400, "标签不超过500字");
  }

  /**
   * 并发冲突校验：编辑令牌与来源版本号必须与当前读取到的投影一致。
   *
   * <p>场景题与旧版代码题没有版本号，只能依赖令牌；其余来源两者都校验，任一不匹配即 409， 避免覆盖他人编辑。
   */
  private static void assertNoConcurrentEdit(
      Identity identity, QuestionCatalogBasicEditRequest req, QuestionCatalogItem item) {
    if ((identity.source().equals("SCENARIO")
            || identity.source().equals("LEGACY_CODE")
            || req.expectedEditToken() != null)
        && !Objects.equals(item.getEditToken(), req.expectedEditToken()))
      throw new BusinessException(409, "题目内容已变更，请重新加载后编辑");
    if (!Objects.equals(item.getSourceVersion(), req.expectedSourceVersion()))
      throw new BusinessException(409, "题目已变更，请重新加载后编辑");
  }

  /** 解析期望更新时间；允许前端用空格代替 `T`，格式非法统一按 400 返回。 */
  private static java.time.LocalDateTime parseExpectedUpdatedAt(
      QuestionCatalogBasicEditRequest req) {
    if (req.expectedUpdatedAt() == null) return null;
    try {
      return java.time.LocalDateTime.parse(req.expectedUpdatedAt().replace(' ', 'T'));
    } catch (java.time.format.DateTimeParseException e) {
      throw new BusinessException(400, "更新时间格式错误");
    }
  }

  private static boolean requiresReview(JsonNode quizDetail) {
    return "AI".equals(quizDetail.path("source").asText()) || quizDetail.hasNonNull("reviewStatus");
  }

  @org.springframework.transaction.annotation.Transactional
  /**
   * 批量发布/下架。
   *
   * <p>先整批校验再执行：任一条来源不支持或缺少专业扩展即整批拒绝，避免「部分成功」； 执行阶段任何一条失败都会回滚整个事务。
   */
  public void batchStatus(List<String> ids, boolean published) {
    if (ids == null || ids.isEmpty() || ids.size() > 50)
      throw new BusinessException(400, "批量操作需要1–50个题目身份");
    var identities =
        ids.stream()
            .distinct()
            .map(QuestionCatalogService::parse)
            .sorted(Comparator.comparingLong(Identity::id).thenComparing(Identity::source))
            .toList();
    for (var i : identities) {
      if (!Set.of("QUIZ", "CODE_PROBLEM").contains(i.source()))
        throw new BusinessException(400, "该来源不支持统一发布；请使用专业编辑器");
      var item = (QuestionCatalogItem) detail(i.source() + ":" + i.id()).get("item");
      if (!item.getCapabilities().contains("PUBLISH"))
        throw new BusinessException(409, "题目缺少专业扩展，不能发布");
    }
    for (var i : identities) {
      if (i.source().equals("QUIZ")) quizAdmin.publish(i.id(), published);
      else {
        code.publish(i.id(), published);
        audit.record(
            "CODE_PROBLEM",
            i.id(),
            published ? "PUBLISH" : "UNPUBLISH",
            null,
            String.valueOf(published));
      }
    }
  }

  /** 筛选面板数据：来源、分类、知识点（含题目数）与题型集合。 */
  public Map<String, Object> facets() {
    var counts =
        jdbc.queryForList(
            "SELECT topic_code,COUNT(*) question_count FROM ("
                + BANK
                + ") bank WHERE topic_code IS NOT NULL GROUP BY topic_code");
    Map<String, Long> countByCode = new HashMap<>();
    for (var row : counts)
      countByCode.put(
          String.valueOf(row.get("topic_code")), ((Number) row.get("question_count")).longValue());
    var topics =
        jdbc.query(
            "SELECT code,name,parent_code FROM question_topic WHERE deleted=0 AND status=1 ORDER BY"
                + " sort_order,id",
            (rs, index) ->
                new QuestionCatalogTopicVO(
                    rs.getString("code"),
                    rs.getString("name"),
                    rs.getString("parent_code"),
                    countByCode.getOrDefault(rs.getString("code"), 0L)));
    return Map.of(
        "sources",
        SOURCES.stream().sorted().toList(),
        "categories",
        jdbc.queryForList("SELECT id,name FROM doc_category WHERE deleted=0 ORDER BY id"),
        "topics",
        topics,
        "questionTypes",
        List.of(
            "SINGLE_CHOICE",
            "MULTIPLE_CHOICE",
            "TRUE_FALSE",
            "FILL_BLANK",
            "SHORT_ANSWER",
            "SCENARIO",
            "LEGACY_CODE",
            "CODE"));
  }

  /**
   * 审计分页；按写入顺序倒序，便于定位最近一次变更。
   *
   * <p>持久化与行映射由 {@link QuestionMutationAudit} 负责，本方法只做分页参数校验与分页元数据组装。
   */
  public PageResult<QuestionCatalogAuditVO> auditPage(int page, int size) {
    if (page < 1 || size < 1 || size > 100) throw new BusinessException(400, "分页范围不正确");
    var result = new PageResult<QuestionCatalogAuditVO>();
    result.setTotal(audit.count());
    result.setPageNum(page);
    result.setPageSize(size);
    result.setPages((result.getTotal() + size - 1) / size);
    result.setRecords(audit.page(size, ((long) page - 1) * size));
    return result;
  }
}
