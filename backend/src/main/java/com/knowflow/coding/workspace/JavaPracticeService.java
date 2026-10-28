package com.knowflow.coding.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.coding.entity.*;
import com.knowflow.coding.judge.*;
import com.knowflow.coding.mapper.CodeSubmissionMapper;
import com.knowflow.coding.service.CodeProblemService;
import com.knowflow.coding.vo.CodingVos;
import com.knowflow.exception.BusinessException;
import com.knowflow.learning.actions.*;
import com.knowflow.mapper.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 草稿版本与原提交的事务协议。快照只保存在服务端，绝不作为普通DTO或AI上下文返回。 */
@Service
@RequiredArgsConstructor
@DependsOn("javaPracticeSchema")
@ConditionalOnProperty(
  name = "knowflow.product.java-practice.enabled",
  havingValue = "true"
)
public class JavaPracticeService {

  private final CodeProblemService bank;
  private final CodeSubmissionMapper submissions;
  private final QuizQuestionMapper questions;
  private final DocDocumentMapper documents;
  private final PersonalLearningResourceScope scope;
  private final LearningWriteGuard guard;
  private final JdbcTemplate sql;
  private final ObjectMapper json;
  private final TransactionTemplate tx;
  private final CodeJudgeGateway gateway;
  private final com.knowflow.service.NoteService notes;
  private final com.knowflow.learning.tutor.TutorContextService tutor;

  @org.springframework.beans.factory.annotation.Value(
    "${knowflow.product.tutor.enabled:false}"
  )
  private boolean tutorEnabled;

  public record DraftInput(
    String requestId,
    Long expectedRevision,
    String language,
    String sourceCode,
    String baseHash
  ) {}

  public record JobInput(
    String requestId,
    String language,
    String sourceCode,
    String type
  ) {}

  public record RetryInput(String requestId) {}

  public record Snapshot(
    String title,
    String content,
    CodeProblem problem,
    List<CodeTestCase> cases
  ) {}

  private record Claim(long id, boolean dispatch) {}

  public boolean ready() {
    return (
      "ISOLATED".equals(gateway.mode()) &&
      gateway.executionEnabled() &&
      gateway.isolatedExecutionVerified()
    );
  }

  private CodingVos.ProblemDetail access(long user, long question) {
    var detail = bank.detailForUser(question, user);
    var q = questions.selectById(question);
    if (
      q == null ||
      !scope.category(q.getCategoryId(), user) ||
      (q.getDocId() != null &&
        !scope.document(documents.selectById(q.getDocId()), user))
    ) throw new BusinessException(404, "题目不存在或当前无权访问");
    return detail;
  }

  private long revision(long user, long question) {
    Long value = sql.queryForObject(
      "SELECT MAX(revision) FROM code_draft_revision WHERE owner_id=? AND question_id=?",
      Long.class,
      user,
      question
    );
    return value == null ? 0 : value;
  }

  public Map<String, Object> workspace(long user, long question) {
    var detail = access(user, question);
    var out = new LinkedHashMap<String, Object>();
    out.put("problem", detail);
    out.put("draftRevision", revision(user, question));
    out.put("draftHash", hash(Objects.toString(detail.getDraftCode(), "")));
    out.put("executionEnabled", ready());
    out.put("message", ready() ? "独立评测服务已就绪" : "安全评测环境尚未就绪");
    out.put(
      "drafts",
      publicRows(
        "SELECT revision,created_at FROM code_draft_revision WHERE owner_id=? AND question_id=? ORDER BY revision DESC LIMIT 50",
        user,
        question
      )
    );
    out.put(
      "jobs",
      publicRows(
        "SELECT s.id,s.judge_status,s.submission_type,s.problem_version,s.create_time FROM code_submission s JOIN code_practice_job j ON j.submission_id=s.id WHERE j.owner_id=? AND s.question_id=? AND s.deleted=0 ORDER BY s.id DESC LIMIT 50",
        user,
        question
      )
    );
    // 禁止旧全局mock/local配置在新工作台中显示为可安全执行。
    detail.setJudgeExecutionEnabled(ready());
    return out;
  }

  public Map<String, Object> save(long user, long question, DraftInput input) {
    if (
      input == null ||
      input.expectedRevision() == null ||
      input.expectedRevision() < 0
    ) throw new BusinessException(400, "缺少草稿版本");
    validate(input.requestId(), input.language(), input.sourceCode(), false);
    return tx.execute(status -> {
      guard.lock(user);
      var detail = access(user, question);
      String fingerprint = hash(
        write(
          List.of(
            question,
            input.expectedRevision(),
            input.language(),
            input.sourceCode(),
            Objects.toString(input.baseHash(), "")
          )
        )
      );
      var old = sql.queryForList(
        "SELECT input_hash,question_id,revision FROM code_draft_revision WHERE owner_id=? AND request_id=?",
        user,
        input.requestId()
      );
      if (!old.isEmpty()) {
        if (!fingerprint.equals(old.get(0).get("input_hash"))) conflict();
        return saved(
          ((Number) old.get(0).get("revision")).longValue(),
          input.sourceCode()
        );
      }
      long current = revision(user, question);
      if (
        current != input.expectedRevision() ||
        (input.baseHash() != null &&
          !input
            .baseHash()
            .equals(hash(Objects.toString(detail.getDraftCode(), ""))))
      ) conflict();
      var draft = new CodingVos.DraftRequest();
      draft.setLanguage("java");
      draft.setSourceCode(input.sourceCode());
      bank.saveDraft(user, question, draft);
      sql.update(
        "INSERT INTO code_draft_revision(owner_id,question_id,revision,request_id,input_hash,language,source_code) VALUES(?,?,?,?,?,?,?)",
        user,
        question,
        current + 1,
        input.requestId(),
        fingerprint,
        "java",
        input.sourceCode()
      );
      return saved(current + 1, input.sourceCode());
    });
  }

  private Map<String, Object> saved(long revision, String source) {
    return Map.of(
      "draftRevision",
      revision,
      "draftHash",
      hash(source),
      "saved",
      true
    );
  }

  public Map<String, Object> draft(long user, long question, long version) {
    access(user, question);
    var rows = sql.queryForList(
      "SELECT revision,source_code FROM code_draft_revision WHERE owner_id=? AND question_id=? AND revision=?",
      user,
      question,
      version
    );
    if (rows.isEmpty()) throw new BusinessException(404, "草稿版本不存在");
    return Map.of(
      "revision",
      version,
      "sourceCode",
      rows.get(0).get("source_code")
    );
  }

  public Map<String, Object> submit(long user, long question, JobInput input) {
    if (input == null) throw new BusinessException(400, "缺少提交内容");
    validate(input.requestId(), input.language(), input.sourceCode(), true);
    String type = input.type() == null ? "SUBMIT" : input.type();
    if (!Set.of("RUN", "SUBMIT").contains(type)) throw new BusinessException(
      400,
      "无效提交类型"
    );
    Claim claim = tx.execute(status -> {
      guard.lock(user);
      // 与原管理端更新争用同一题目行锁，保证题版和全部用例一致冻结。
      sql.queryForList(
        "SELECT id FROM quiz_question WHERE id=? FOR UPDATE",
        question
      );
      var detail = access(user, question);
      String fingerprint = hash(
        write(List.of(question, input.language(), input.sourceCode(), type))
      );
      var rows = sql.queryForList(
        "SELECT submission_id,input_hash FROM code_practice_job WHERE owner_id=? AND request_id=?",
        user,
        input.requestId()
      );
      if (!rows.isEmpty()) {
        if (!fingerprint.equals(rows.get(0).get("input_hash"))) conflict();
        return new Claim(
          ((Number) rows.get(0).get("submission_id")).longValue(),
          false
        );
      }
      var problem = bank.loadProblemForJudge(question);
      var q = questions.selectById(question);
      var submission = new CodeSubmission();
      submission.setUserId(user);
      submission.setQuestionId(question);
      submission.setLanguage("java");
      submission.setSourceCode(input.sourceCode());
      submission.setProblemVersion(
        q.getVersion() + ":" + problem.getTestCaseVersion()
      );
      submission.setSubmissionType(type);
      submission.setJudgeStatus(JudgeStatus.QUEUED);
      submission.setContextType(CodeSubmission.CONTEXT_PRACTICE);
      submissions.insert(submission);
      sql.update(
        "INSERT INTO code_practice_job(submission_id,owner_id,request_id,input_hash,snapshot_json) VALUES(?,?,?,?,?)",
        submission.getId(),
        user,
        input.requestId(),
        fingerprint,
        write(
          new Snapshot(
            detail.getTitle(),
            detail.getContent(),
            problem,
            bank.loadAllCases(problem.getId())
          )
        )
      );
      attempt(user, submission.getId(), input.requestId());
      return new Claim(submission.getId(), true);
    });
    if (claim.dispatch()) dispatch(user, claim.id());
    return job(user, claim.id());
  }

  private void attempt(long user, long id, String request) {
    Integer count = sql.queryForObject(
      "SELECT COUNT(*) FROM code_practice_attempt WHERE submission_id=?",
      Integer.class,
      id
    );
    sql.update(
      "INSERT INTO code_practice_attempt(submission_id,attempt_no,owner_id,request_id,state) VALUES(?,?,?,?,?)",
      id,
      count + 1,
      user,
      request,
      JudgeStatus.QUEUED
    );
  }

  public Map<String, Object> retry(long user, long id, RetryInput input) {
    if (input == null) throw new BusinessException(400, "缺少重试编号");
    request(input.requestId());
    boolean run = Boolean.TRUE.equals(
      tx.execute(status -> {
        guard.lock(user);
        var s = owned(user, id);
        var previous = sql.queryForList(
          "SELECT submission_id FROM code_practice_attempt WHERE owner_id=? AND request_id=?",
          user,
          input.requestId()
        );
        if (!previous.isEmpty()) {
          if (
            ((Number) previous.get(0).get("submission_id")).longValue() != id
          ) conflict();
          return false;
        }
        if (
          !JudgeStatus.isPlatformFailure(s.getJudgeStatus())
        ) throw new BusinessException(
          409,
          "只有平台失败可重试；原提交不会被修改"
        );
        attempt(user, id, input.requestId());
        s.setJudgeStatus(JudgeStatus.QUEUED);
        submissions.updateById(s);
        return true;
      })
    );
    if (run) dispatch(user, id);
    return job(user, id);
  }

  private CodeSubmission owned(long user, long id) {
    var rows = sql.queryForList(
      "SELECT submission_id FROM code_practice_job WHERE owner_id=? AND submission_id=?",
      user,
      id
    );
    var s = submissions.selectById(id);
    if (
      rows.isEmpty() || s == null || !Objects.equals(s.getUserId(), user)
    ) throw new BusinessException(404, "提交不存在");
    access(user, s.getQuestionId());
    return s;
  }

  public Map<String, Object> job(long user, long id) {
    // 未配置Runner时回收中断的QUEUED任务；同键读取绝不能永久停在排队中。
    if (!ready()) tx.executeWithoutResult(status -> {
      guard.lock(user);
      var current = owned(user, id);
      recoverUnavailable(current);
    });
    var s = owned(user, id);
    var snapshot = snapshot(id);
    var out = new LinkedHashMap<String, Object>();
    out.put("id", id);
    out.put("questionId", s.getQuestionId());
    out.put("questionVersion", s.getProblemVersion());
    out.put("title", snapshot.title());
    out.put("content", snapshot.content());
    out.put("sourceCode", s.getSourceCode());
    out.put("language", s.getLanguage());
    out.put("type", s.getSubmissionType());
    out.put("judgeStatus", s.getJudgeStatus());
    out.put("score", s.getScore());
    out.put("passedCount", s.getPassedCount());
    out.put("totalCount", s.getTotalCount());
    out.put("executionTimeMs", s.getExecutionTimeMs());
    out.put("memoryUsedKb", s.getMemoryUsedKb());
    out.put("executionEnabled", ready());
    out.put(
      "message",
      JudgeStatus.JUDGE_UNAVAILABLE.equals(s.getJudgeStatus())
        ? "安全评测环境尚未就绪；原代码已保存，未计分"
        : s.getJudgeStatus()
    );
    out.put(
      "attempts",
      publicRows(
        "SELECT attempt_no,state,created_at,finished_at FROM code_practice_attempt WHERE owner_id=? AND submission_id=? ORDER BY attempt_no",
        user,
        id
      )
    );
    return out;
  }

  @org.springframework.context.event.EventListener(
    org.springframework.boot.context.event.ApplicationReadyEvent.class
  )
  public void recoverInterruptedJobs() {
    if (ready()) return;
    var rows = sql.queryForList(
      "SELECT j.owner_id,j.submission_id FROM code_practice_job j JOIN code_submission s ON s.id=j.submission_id WHERE s.judge_status IN ('QUEUED','COMPILING','RUNNING') AND s.deleted=0"
    );
    for (var row : rows)
      tx.executeWithoutResult(status -> {
        long user = ((Number) row.get("owner_id")).longValue();
        guard.lock(user);
        recoverUnavailable(
          submissions.selectById(
            ((Number) row.get("submission_id")).longValue()
          )
        );
      });
  }

  private void recoverUnavailable(CodeSubmission s) {
    if (
      s == null ||
      !Set.of(
        JudgeStatus.QUEUED,
        JudgeStatus.COMPILING,
        JudgeStatus.RUNNING
      ).contains(s.getJudgeStatus())
    ) return;
    s.setJudgeStatus(JudgeStatus.JUDGE_UNAVAILABLE);
    s.setScore(null);
    submissions.updateById(s);
    sql.update(
      "UPDATE code_practice_attempt SET state=?,finished_at=CURRENT_TIMESTAMP WHERE submission_id=? AND state IN ('QUEUED','COMPILING','RUNNING')",
      JudgeStatus.JUDGE_UNAVAILABLE,
      s.getId()
    );
  }

  private Snapshot snapshot(long id) {
    String raw = sql.queryForObject(
      "SELECT snapshot_json FROM code_practice_job WHERE submission_id=?",
      String.class,
      id
    );
    try {
      return json.readValue(raw, Snapshot.class);
    } catch (Exception e) {
      throw new IllegalStateException("提交快照损坏", e);
    }
  }

  /** 先提交冻结快照，再调用唯一网关；绝不在数据库事务内等待外部Runner。 */
  private void dispatch(long user, long id) {
    CodeSubmission s = submissions.selectById(id);
    JudgeVerdict verdict = null;
    if (ready()) {
      var snap = snapshot(id);
      try {
        verdict = gateway.judge(
          new JudgeRequest(
            user,
            s.getQuestionId(),
            s.getLanguage(),
            s.getSourceCode(),
            "SUBMIT".equals(s.getSubmissionType()),
            snap.problem(),
            snap.cases(),
            "code-job-" + id
          )
        );
      } catch (Exception ignored) {
        /* 外部异常不能泄露Runner配置或丢失原代码。 */
      }
    }
    String result = !ready()
      ? JudgeStatus.JUDGE_UNAVAILABLE
      : JudgeStatus.SYSTEM_ERROR;
    // 当前尚无已验收Runner；未来适配只允许完整终态，不将不完整响应默认为通过。
    if (
      verdict != null &&
      (JudgeStatus.isFinished(verdict.status()) ||
        JudgeStatus.isPlatformFailure(verdict.status())) &&
      (JudgeStatus.isPlatformFailure(verdict.status()) ||
        (verdict.score() != null &&
          verdict.score() >= 0 &&
          verdict.score() <= 100))
    ) result = verdict.status();
    final String state = result;
    final JudgeVerdict v = verdict;
    tx.executeWithoutResult(status -> {
      guard.lock(user);
      var current = submissions.selectById(id);
      current.setJudgeStatus(state);
      if (JudgeStatus.isFinished(state) && v != null) {
        current.setScore(v.score());
        current.setPassedCount(v.passedCount());
        current.setTotalCount(v.totalCount());
        current.setExecutionTimeMs(v.executionTimeMs());
        current.setMemoryUsedKb(v.memoryUsedKb());
      } else {
        current.setScore(null);
      }
      // 原始stdout/隐藏诊断不能直接公开；独立Runner接入时需另行验收脱敏协议。
      submissions.updateById(current);
      sql.update(
        "UPDATE code_practice_attempt SET state=?,finished_at=CURRENT_TIMESTAMP WHERE submission_id=? AND state=?",
        state,
        id,
        JudgeStatus.QUEUED
      );
    });
  }

  /** 同一提交重复点击复用原笔记/导师会话；准备失败时整笔回滚，不留下孤儿笔记。 */
  public Map<String, Object> openTutor(long user, long id) {
    if (!tutorEnabled) throw new BusinessException(
      409,
      "导师功能未开放，原代码提交已保留"
    );
    return tx.execute(status -> {
      guard.lock(user);
      var s = owned(user, id);
      Long note = sql.queryForObject(
        "SELECT tutor_note_id FROM code_practice_job WHERE submission_id=?",
        Long.class,
        id
      );
      if (note == null) {
        var source = tutorSource(user, id);
        var dto = new com.knowflow.dto.LearningNoteDTO();
        dto.setTitle(source.get("title").toString());
        dto.setContent(source.get("content").toString());
        dto.setSourceDocId(questions.selectById(s.getQuestionId()).getDocId());
        note = notes.create(user, dto).getId();
        sql.update(
          "UPDATE code_practice_job SET tutor_note_id=? WHERE submission_id=?",
          note,
          id
        );
      }
      return tutor.create(note, user, "DIRECT");
    });
  }

  public Map<String, Object> tutorSource(long user, long id) {
    var s = owned(user, id);
    var snap = snapshot(id);
    // 仅公开题意、本人冻结源码与状态，不复制快照内的任何用例或参考解答。
    return Map.of(
      "title",
      "代码练习：" +
        snap.title().substring(0, Math.min(150, snap.title().length())),
      "content",
      "题目：\n" +
        excerpt(snap.content(), 4000) +
        "\n\n我的Java源码：\n```java\n" +
        excerpt(s.getSourceCode(), 12000) +
        "\n```\n\n评测状态：" +
        s.getJudgeStatus() +
        "\n请解释可能原因并给出改进建议；AI不能改变客观判题结果。"
    );
  }

  private String excerpt(String value, int limit) {
    return value.length() <= limit
      ? value
      : value.substring(0, limit) +
          "\n（导师上下文仅保留前" +
          limit +
          "字；完整源码仍保存在原提交，未被修改。）";
  }

  /** JDBC驱动的列名大小写不同，公开历史DTO统一小写，避免H2/MySQL界面行为不一致。 */
  private List<Map<String, Object>> publicRows(String query, Object... args) {
    return sql
      .queryForList(query, args)
      .stream()
      .map(row -> {
        Map<String, Object> normalized = new LinkedHashMap<>();
        row.forEach((key, value) ->
          normalized.put(key.toLowerCase(Locale.ROOT), value)
        );
        return normalized;
      })
      .toList();
  }

  private void validate(
    String key,
    String language,
    String source,
    boolean nonempty
  ) {
    request(key);
    if (
      !"java".equals(language) ||
      source == null ||
      source.getBytes(StandardCharsets.UTF_8).length > 100000 ||
      (nonempty && source.isBlank())
    ) throw new BusinessException(400, "只支持Java，源码不得为空或超过100KB");
  }

  private void request(String key) {
    if (
      key == null || !key.matches("[A-Za-z0-9_-]{8,80}")
    ) throw new BusinessException(400, "无效请求编号");
  }

  private void conflict() {
    throw new BusinessException(
      409,
      "内容或版本冲突，请重新加载；本地代码未覆盖服务端版本"
    );
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String hash(String value) {
    try {
      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(
          value.getBytes(StandardCharsets.UTF_8)
        )
      );
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
