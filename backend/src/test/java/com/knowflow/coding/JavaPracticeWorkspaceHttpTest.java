package com.knowflow.coding;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.*;
import com.knowflow.coding.service.CodeProblemService;
import com.knowflow.coding.vo.CodingVos;
import com.knowflow.entity.SysUser;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.utils.JwtUtils;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 只验证持久化与安全拒绝合同，不在宿主执行Java。 */
@SpringBootTest(
  properties = {
    "jwt.secret=f11-contract-only-not-a-real-secret-20261027",
    "knowflow.product.java-practice.enabled=true",
    "knowflow.product.tutor.enabled=true",
    "knowflow.judge.mode=mock",
    "knowflow.coding.seed.algorithm.enabled=false",
  }
)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class JavaPracticeWorkspaceHttpTest {

  @Autowired
  com.knowflow.mapper.DocCategoryMapper categories;

  @Autowired
  com.knowflow.coding.workspace.JavaPracticeService workspace;

  @Autowired
  org.springframework.jdbc.core.JdbcTemplate sql;

  @Autowired
  MockMvc mvc;

  @Autowired
  ObjectMapper json;

  @Autowired
  JwtUtils jwt;

  @Autowired
  SysUserMapper users;

  @Autowired
  CodeProblemService bank;

  long owner, other, admin, question;

  @BeforeEach
  void setup() {
    long[] ids = new long[3];
    for (int i = 0; i < 3; i++) {
      var u = new SysUser();
      u.setUsername("f11-" + UUID.randomUUID());
      u.setPassword("test-only");
      u.setRole(i == 2 ? "ADMIN" : "USER");
      users.insert(u);
      ids[i] = u.getId();
    }
    owner = ids[0];
    other = ids[1];
    admin = ids[2];
    var p = new CodingVos.SaveProblemRequest();
    p.setTitle("Java合成加法题");
    p.setContent("读取两个整数并输出和。");
    p.setSupportedLanguages("java");
    p.setStarterCode("class Main {}");
    p.setFunctionSignature("public static void main(String[] args)");
    p.setConstraints("两个整数");
    p.setExamples("[{\"input\":\"1 2\",\"output\":\"3\"}]");
    p.setTimeLimitMs(1000);
    p.setMemoryLimitMb(128);
    p.setDifficulty(0);
    p.setStatus(1);
    var a = new CodingVos.SaveCaseRequest();
    a.setInput("[1,2]");
    a.setExpectedOutput("3");
    a.setVisibility("PUBLIC");
    a.setOrderNo(0);
    a.setWeight(1);
    var b = new CodingVos.SaveCaseRequest();
    b.setInput("\"PRIVATE_CASE_INPUT\"");
    b.setExpectedOutput("\"PRIVATE_CASE_ANSWER\"");
    b.setVisibility("HIDDEN");
    b.setOrderNo(1);
    b.setWeight(1);
    p.setReferenceSolution("PRIVATE_REFERENCE");
    p.setCases(List.of(a, b));
    question = bank.saveProblem(p, admin);
    bank.review(question, "APPROVED", "合成合同夹具");
  }

  JsonNode call(String method, String path, Object body, long user, int status)
    throws Exception {
    var r = method.equals("POST") ? post(path) : get(path);
    r.contentType("application/json");
    if (body != null) r.content(json.writeValueAsString(body));
    if (user > 0) r.header(
      "Authorization",
      "Bearer " +
        jwt.generateToken(user, "f11-test", user == admin ? "ADMIN" : "USER")
    );
    var response = mvc.perform(r).andReturn().getResponse();
    assertEquals(
      status,
      response.getStatus(),
      response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)
    );
    var result = json.readTree(
      response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)
    );
    assertEquals(status, result.path("code").asInt());
    return result.path("data");
  }

  String root() {
    return "/api/code-bank/problems/" + question + "/workspace";
  }

  @Test
  void draftsHaveVersionsConflictsAndOwnHistory() throws Exception {
    var d = call("GET", root(), null, owner, 200);
    assertEquals(0, d.path("draftRevision").asInt());
    var first = Map.of(
      "requestId",
      UUID.randomUUID().toString(),
      "expectedRevision",
      0,
      "language",
      "java",
      "sourceCode",
      "class First {}"
    );
    d = call("POST", root() + "/drafts", first, owner, 200);
    assertEquals(1, d.path("draftRevision").asInt());
    assertEquals(
      1,
      call("GET", root(), null, owner, 200)
        .path("drafts")
        .get(0)
        .path("revision")
        .asInt()
    );
    assertEquals(
      1,
      call("POST", root() + "/drafts", first, owner, 200)
        .path("draftRevision")
        .asInt()
    );
    call(
      "POST",
      root() + "/drafts",
      Map.of(
        "requestId",
        UUID.randomUUID().toString(),
        "expectedRevision",
        0,
        "language",
        "java",
        "sourceCode",
        "class Stale {}"
      ),
      owner,
      409
    );
    call(
      "POST",
      root() + "/drafts",
      Map.of(
        "requestId",
        UUID.randomUUID().toString(),
        "expectedRevision",
        1,
        "language",
        "java",
        "sourceCode",
        "class Second {}"
      ),
      owner,
      200
    );
    assertEquals(
      "class First {}",
      call("GET", root() + "/drafts/1", null, owner, 200)
        .path("sourceCode")
        .asText()
    );
    assertEquals(
      0,
      call("GET", root(), null, other, 200).path("draftRevision").asInt()
    );
    call("GET", root() + "/drafts/1", null, other, 404);
    call("GET", root() + "/drafts/1", null, admin, 404);
    call("GET", root(), null, 0, 401);
  }

  @Test
  void unavailableJudgePreservesFrozenCodeAndIdempotencyWithoutHiddenData()
    throws Exception {
    var input = Map.of(
      "requestId",
      UUID.randomUUID().toString(),
      "language",
      "java",
      "sourceCode",
      "class Original {}",
      "type",
      "SUBMIT"
    );
    var job = call("POST", root() + "/jobs", input, owner, 200);
    long id = job.path("id").asLong();
    assertTrue(id > 0);
    assertEquals(1, job.path("attempts").get(0).path("attempt_no").asInt());
    assertEquals(
      id,
      call("GET", root(), null, owner, 200)
        .path("jobs")
        .get(0)
        .path("id")
        .asLong()
    );
    assertEquals("JUDGE_UNAVAILABLE", job.path("judgeStatus").asText());
    assertTrue(job.path("score").isNull());
    assertEquals("class Original {}", job.path("sourceCode").asText());
    assertEquals(
      id,
      call("POST", root() + "/jobs", input, owner, 200)
        .path("id")
        .asLong()
    );
    var changed = new HashMap<String, Object>(input);
    changed.put("sourceCode", "class Changed {}");
    call("POST", root() + "/jobs", changed, owner, 409);
    String path = "/api/code-bank/workspace/jobs/" + id;
    var read = call("GET", path, null, owner, 200);
    assertFalse(read.toString().contains("PRIVATE_CASE"));
    assertFalse(read.toString().contains("PRIVATE_REFERENCE"));
    for (long u : new long[] { other, admin }) call("GET", path, null, u, 404);
    call("GET", path, null, 0, 401);
    assertEquals(
      "JUDGE_UNAVAILABLE",
      call(
        "POST",
        path + "/retry",
        Map.of("requestId", UUID.randomUUID().toString()),
        owner,
        200
      )
        .path("judgeStatus")
        .asText()
    );
  }

  @Test
  void concurrentSameIntentCreatesOneFrozenSubmissionAndRecoversInterruptedJob()
    throws Exception {
    var input = new com.knowflow.coding.workspace.JavaPracticeService.JobInput(
      UUID.randomUUID().toString(),
      "java",
      "class Immutable {}",
      "SUBMIT"
    );
    var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
    try {
      var tasks = new ArrayList<
        java.util.concurrent.Callable<Map<String, Object>>
      >();
      for (int i = 0; i < 4; i++) tasks.add(() ->
        workspace.submit(owner, question, input)
      );
      var ids = new HashSet<Object>();
      for (var future : pool.invokeAll(tasks)) ids.add(future.get().get("id"));
      assertEquals(1, ids.size());
      long id = ((Number) ids.iterator().next()).longValue();
      sql.update(
        "UPDATE code_submission SET judge_status='QUEUED' WHERE id=?",
        id
      );
      workspace.recoverInterruptedJobs();
      assertEquals(
        "JUDGE_UNAVAILABLE",
        workspace.job(owner, id).get("judgeStatus")
      );
      assertNull(workspace.job(owner, id).get("score"));
      assertEquals(
        "class Immutable {}",
        workspace.job(owner, id).get("sourceCode")
      );
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void oldWritesCannotBypassVersioningAndMockIsNotSafeExecution()
    throws Exception {
    call(
      "POST",
      "/api/code-bank/problems/" + question + "/draft",
      Map.of("language", "java", "sourceCode", "class Bypass {}"),
      owner,
      409
    );
    var old = call(
      "POST",
      "/api/code-bank/problems/" + question + "/submissions",
      Map.of(
        "language",
        "java",
        "sourceCode",
        "class Original {}",
        "type",
        "SUBMIT"
      ),
      owner,
      200
    );
    assertEquals("JUDGE_UNAVAILABLE", old.path("judgeStatus").asText());
    assertTrue(old.path("score").isNull());
  }

  @Test
  void frozenVersionSurvivesEditsAndRevocationBlocksAllReads()
    throws Exception {
    var job = call(
      "POST",
      root() + "/jobs",
      Map.of(
        "requestId",
        UUID.randomUUID().toString(),
        "language",
        "java",
        "sourceCode",
        "class Frozen {}",
        "type",
        "SUBMIT"
      ),
      owner,
      200
    );
    long id = job.path("id").asLong();
    String version = job.path("questionVersion").asText();
    sql.update(
      "UPDATE quiz_question SET title='修改后的题目',version=version+1 WHERE id=?",
      question
    );
    var old = call(
      "GET",
      "/api/code-bank/workspace/jobs/" + id,
      null,
      owner,
      200
    );
    assertEquals(version, old.path("questionVersion").asText());
    assertEquals("Java合成加法题", old.path("title").asText());
    assertFalse(
      workspace.tutorSource(owner, id).toString().contains("PRIVATE_")
    );
    bank.publish(question, false);
    call("GET", "/api/code-bank/workspace/jobs/" + id, null, owner, 404);
    call(
      "POST",
      "/api/code-bank/workspace/jobs/" + id + "/retry",
      Map.of("requestId", UUID.randomUUID().toString()),
      owner,
      404
    );
  }

  @Test
  void privateCategoryScopeAlsoProtectsLegacyEntryPoints() throws Exception {
    var category = new com.knowflow.entity.DocCategory();
    category.setName("私人测试分类");
    category.setOwnerId(owner);
    category.setStatus(1);
    category.setParentId(0L);
    categories.insert(category);
    sql.update(
      "UPDATE quiz_question SET category_id=? WHERE id=?",
      category.getId(),
      question
    );
    call("GET", root(), null, owner, 200);
    for (long user : new long[] { other, admin }) {
      call("GET", root(), null, user, 404);
      call("GET", "/api/code-bank/problems/" + question, null, user, 404);
      call(
        "POST",
        "/api/code-bank/problems/" + question + "/submissions",
        Map.of(
          "language",
          "java",
          "sourceCode",
          "class Leak {}",
          "type",
          "RUN"
        ),
        user,
        404
      );
      call(
        "POST",
        "/api/code-bank/problems/" + question + "/ai-assist",
        Map.of("kind", "review"),
        user,
        404
      );
    }
  }

  @Test
  void tutorReusesOriginalConversationAndLimitsOnlyItsContext()
    throws Exception {
    String code = "class LongSource { /*" + "x".repeat(50000) + "*/ }";
    var submitted = call(
      "POST",
      root() + "/jobs",
      Map.of(
        "requestId",
        UUID.randomUUID().toString(),
        "language",
        "java",
        "sourceCode",
        code,
        "type",
        "SUBMIT"
      ),
      owner,
      200
    );
    long id = submitted.path("id").asLong();
    String path = "/api/code-bank/workspace/jobs/" + id + "/tutor";
    var first = call("POST", path, Map.of(), owner, 200);
    var again = call("POST", path, Map.of(), owner, 200);
    assertEquals(first.path("conversationId"), again.path("conversationId"));
    String content = sql.queryForObject(
      "SELECT content FROM learning_note WHERE id=?",
      String.class,
      first.path("noteId").asLong()
    );
    assertTrue(content.length() <= 20000);
    assertTrue(content.contains("完整源码仍保存在原提交"));
    assertFalse(content.contains("PRIVATE_CASE"));
    assertFalse(content.contains("PRIVATE_REFERENCE"));
    assertEquals(code, workspace.job(owner, id).get("sourceCode"));
    for (long user : new long[] { other, admin })
      call("POST", path, Map.of(), user, 404);
    call("POST", path, Map.of(), 0, 401);
  }
}
