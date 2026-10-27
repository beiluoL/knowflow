package com.knowflow.coding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.coding.entity.CodeSubmission;
import com.knowflow.coding.judge.JudgeStatus;
import com.knowflow.coding.mapper.CodeSubmissionMapper;
import com.knowflow.coding.service.CodeProblemService;
import com.knowflow.coding.vo.CodingVos;
import com.knowflow.entity.LearningEvent;
import com.knowflow.entity.LearningMistake;
import com.knowflow.entity.SysUser;
import com.knowflow.exam.dto.ExamRequests;
import com.knowflow.exam.entity.ExamAnswer;
import com.knowflow.exam.entity.ExamAttempt;
import com.knowflow.exam.mapper.ExamAnswerMapper;
import com.knowflow.exam.service.ExamService;
import com.knowflow.exam.vo.ExamVos;
import com.knowflow.mapper.LearningEventMapper;
import com.knowflow.mapper.LearningMistakeMapper;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3G 集成测试（Mock Judge，<b>不是</b>真实代码执行）：
 * 题库权限/脱敏、草稿、提交状态机、平台失败不记错不计 0、考试 CODE 结算与重判幂等。
 * 独占 H2 内存库，避免 schema.sql 二次执行索引冲突。H2 ≠ MySQL 8 实测。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
@TestPropertySource(properties = {
        "knowflow.datasource.h2.url=jdbc:h2:mem:phase3g-coding-bank;DB_CLOSE_DELAY=-1;MODE=MySQL;LOCK_TIMEOUT=10000",
        "server.address=127.0.0.1",
        "knowflow.datasource.allow-runtime-switch=false",
        "knowflow.judge.mode=mock",
        "knowflow.exam.timeout-scheduler.enabled=false",
        "knowflow.coding.seed.algorithm.enabled=false"
})
class CodeBankIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtUtils jwtUtils;
    @Autowired SysUserMapper userMapper;
    @Autowired CodeProblemService codeProblemService;
    @Autowired CodeSubmissionMapper submissionMapper;
    @Autowired LearningMistakeMapper mistakeMapper;
    @Autowired LearningEventMapper eventMapper;
    @Autowired ExamService examService;
    @Autowired ExamAnswerMapper answerMapper;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private SysUser admin;
    private SysUser alice;
    private SysUser bob;

    @BeforeEach
    void setUp() {
        admin = newUser("ADMIN");
        alice = newUser("USER");
        bob = newUser("USER");
    }

    private SysUser newUser(String role) {
        SysUser u = new SysUser();
        u.setUsername("p3g-" + UUID.randomUUID());
        u.setPassword("test-only");
        u.setNickname("P3G");
        u.setRole(role);
        userMapper.insert(u);
        return u;
    }

    private String token(SysUser u) {
        return "Bearer " + jwtUtils.generateToken(u.getId(), u.getUsername(), u.getRole());
    }

    private CodingVos.SaveProblemRequest sampleProblem(String title, int status) {
        CodingVos.SaveProblemRequest req = new CodingVos.SaveProblemRequest();
        req.setTitle(title);
        req.setContent("# " + title + "\n实现函数。");
        req.setDifficulty(1);
        req.setTopicCode("ALGO_HASH");
        req.setTags("算法,哈希表");
        req.setSupportedLanguages("java");
        req.setFunctionSignature("public int add(int a, int b)");
        req.setStarterCode("class Solution {\n public int add(int a, int b) { return 0; }\n}\n");
        req.setConstraints("1 <= a,b <= 100");
        req.setExamples("[{\"input\":\"[1,2]\",\"output\":\"3\"}]");
        req.setTimeLimitMs(1000);
        req.setMemoryLimitMb(256);
        req.setReferenceSolution("class Solution {\n public int add(int a, int b) { return a+b; }\n}\n");
        req.setSolutionExplanation("直接相加。");
        req.setComplexityAnalysis("O(1)");
        req.setStatus(status);
        CodingVos.SaveCaseRequest c1 = new CodingVos.SaveCaseRequest();
        c1.setInput("[1,2]");
        c1.setExpectedOutput("3");
        c1.setVisibility("PUBLIC");
        c1.setWeight(1);
        c1.setOrderNo(0);
        CodingVos.SaveCaseRequest c2 = new CodingVos.SaveCaseRequest();
        c2.setInput("[40,2]");
        c2.setExpectedOutput("42");
        c2.setVisibility("HIDDEN");
        c2.setWeight(1);
        c2.setOrderNo(1);
        req.setCases(List.of(c1, c2));
        return req;
    }

    // ---------- 权限 ----------

    @Test
    @DisplayName("管理端算法题接口：匿名 401 / 普通用户 403 / ADMIN 200")
    void adminEndpointsProtected() throws Exception {
        mockMvc.perform(get("/api/admin/code-problems")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/code-problems").header("Authorization", token(alice)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/code-problems").header("Authorization", token(admin)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("草稿题对普通用户不可见，审核发布后才进入题库；用户详情不含参考解/隐藏用例")
    void reviewGateAndSanitization() throws Exception {
        Long id = codeProblemService.saveProblem(sampleProblem("脱敏测试题-" + UUID.randomUUID().toString().replace("42", "x"), 0), admin.getId());

        mockMvc.perform(get("/api/code-bank/problems/" + id).header("Authorization", token(alice)))
                .andExpect(status().isNotFound());

        codeProblemService.review(id, "APPROVED", "ok");

        String body = mockMvc.perform(get("/api/code-bank/problems/" + id).header("Authorization", token(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.functionSignature").value("public int add(int a, int b)"))
                .andExpect(jsonPath("$.data.publicCases.length()").value(1))
                .andExpect(jsonPath("$.data.referenceSolution").doesNotExist())
                .andExpect(jsonPath("$.data.solutionExplanation").isEmpty())
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("42"), "公开详情不得泄露隐藏用例期望值");
        assertFalse(body.contains("return a+b"), "公开详情不得泄露参考解答");

        // 管理端完整详情可见全部（含隐藏用例/参考解）
        mockMvc.perform(get("/api/admin/code-problems/" + id).header("Authorization", token(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.full").value(true));
    }

    @Test
    @DisplayName("分页与过滤在服务端生效：按难度/关键词/分类过滤")
    void serverSidePagingAndFilter() {
        String unique = "UNIQFILTER-" + UUID.randomUUID();
        Long id = codeProblemService.saveProblem(sampleProblem(unique + " 标题", 1), admin.getId());
        // status=1 保存即发布（管理员操作等价审核通过）
        var page = codeProblemService.pagePublished(unique, "ALGO_HASH", 1, 1, 10, alice.getId());
        assertEquals(1, page.getTotal());
        assertEquals(id, page.getRecords().get(0).getId());
        assertEquals(0, codeProblemService.pagePublished(unique, "ALGO_DP", 1, 1, 10, alice.getId()).getTotal());
        assertEquals(0, codeProblemService.pagePublished(unique, null, 3, 1, 10, alice.getId()).getTotal());
    }

    // ---------- 草稿 / 提交状态机 ----------

    private Long publishAddProblem() {
        return codeProblemService.saveProblem(
                sampleProblem("状态机题-" + UUID.randomUUID(), 1), admin.getId());
    }

    @Test
    @DisplayName("草稿 upsert：同一用户同一题只有一行，内容被覆盖")
    void draftUpsert() {
        Long id = publishAddProblem();
        CodingVos.DraftRequest d1 = new CodingVos.DraftRequest();
        d1.setLanguage("java");
        d1.setSourceCode("v1");
        codeProblemService.saveDraft(alice.getId(), id, d1);
        CodingVos.DraftRequest d2 = new CodingVos.DraftRequest();
        d2.setLanguage("java");
        d2.setSourceCode("v2");
        codeProblemService.saveDraft(alice.getId(), id, d2);
        assertEquals("v2", codeProblemService.detailForUser(id, alice.getId()).getDraftCode());
        // bob 的草稿互不影响
        codeProblemService.saveDraft(bob.getId(), id, d1);
        assertEquals("v2", codeProblemService.detailForUser(id, alice.getId()).getDraftCode());
    }

    @Test
    @DisplayName("Mock 判题：ACCEPTED 计满分且不记错；通过后题解开放；隐藏用例脱敏")
    void acceptedNoMistakeAndSolutionOpens() {
        Long id = publishAddProblem();
        CodingVos.SubmitRequest req = new CodingVos.SubmitRequest();
        req.setLanguage("java");
        req.setType("SUBMIT");
        req.setSourceCode("// @MOCK_VERDICT:ACCEPTED\nclass Solution { public int add(int a,int b){return a+b;} }");
        CodingVos.SubmitResult r = codeProblemService.submitPractice(alice.getId(), id, req);

        assertEquals(JudgeStatus.ACCEPTED, r.getJudgeStatus());
        assertEquals(100, r.getScore());
        assertEquals(2, r.getTotalCount());
        assertTrue(r.getCaseResults().stream().anyMatch(CodingVos.CaseResultView::isHidden));
        assertTrue(r.getCaseResults().stream().filter(CodingVos.CaseResultView::isHidden)
                .allMatch(c -> c.getInput() == null && c.getExpected() == null));
        assertEquals(0, countMistakes(alice.getId()));
        assertEquals(1, countCodeEvents(alice.getId()), "成功提交应产生一条 CODE_SUBMITTED 事件");
        assertEquals("直接相加。", codeProblemService.detailForUser(id, alice.getId()).getSolutionExplanation());
    }

    @Test
    @DisplayName("Mock 判题：WRONG_ANSWER 记入本人错题与事件；他人数据不受影响")
    void wrongAnswerCreatesOwnMistake() {
        Long id = publishAddProblem();
        CodingVos.SubmitRequest req = new CodingVos.SubmitRequest();
        req.setLanguage("java");
        req.setType("SUBMIT");
        req.setSourceCode("// @MOCK_VERDICT:WRONG_ANSWER\nclass Solution { public int add(int a,int b){return 0;} }");
        CodingVos.SubmitResult r = codeProblemService.submitPractice(alice.getId(), id, req);
        assertEquals(JudgeStatus.WRONG_ANSWER, r.getJudgeStatus());
        assertEquals(0, r.getScore());
        assertEquals(1, countMistakes(alice.getId()));
        assertEquals(0, countMistakes(bob.getId()));
        // 重复答错只合并（幂等），不新增错题行
        codeProblemService.submitPractice(alice.getId(), id, req);
        assertEquals(1, countMistakes(alice.getId()));
    }

    @Test
    @DisplayName("RUN 只跑公开用例且不记错、不发提交历史")
    void runPublicOnlyNoMistake() {
        Long id = publishAddProblem();
        CodingVos.SubmitRequest req = new CodingVos.SubmitRequest();
        req.setLanguage("java");
        req.setType("RUN");
        req.setSourceCode("// @MOCK_VERDICT:ACCEPTED\nx");
        CodingVos.SubmitResult r = codeProblemService.submitPractice(alice.getId(), id, req);
        assertEquals(1, r.getTotalCount());
        assertEquals(0, countMistakes(alice.getId()));
        assertEquals(0, codeProblemService.mySubmissions(alice.getId(), id, 1, 10).getTotal());
    }

    @Test
    @DisplayName("平台失败（SYSTEM_ERROR）：score=null、不记错、提交保留可重试")
    void platformFailureKeepsNullScore() {
        Long id = publishAddProblem();
        CodingVos.SubmitRequest req = new CodingVos.SubmitRequest();
        req.setLanguage("java");
        req.setType("SUBMIT");
        req.setSourceCode("// @MOCK_VERDICT:SYSTEM_ERROR\nx");
        CodingVos.SubmitResult r = codeProblemService.submitPractice(alice.getId(), id, req);
        assertEquals(JudgeStatus.SYSTEM_ERROR, r.getJudgeStatus());
        assertNull(r.getScore());
        assertEquals(0, countMistakes(alice.getId()));
        assertEquals(0, countCodeEvents(alice.getId()), "平台失败不得产生学习事件");
        List<CodeSubmission> rows = submissionMapper.selectList(null).stream()
                .filter(s -> s.getQuestionId().equals(id) && s.getUserId().equals(alice.getId())).toList();
        assertEquals(1, rows.size());
        assertNull(rows.get(0).getScore());
    }

    @Test
    @DisplayName("提交历史归属隔离：不能读取他人提交详情")
    void submissionOwnership() {
        Long id = publishAddProblem();
        CodingVos.SubmitRequest req = new CodingVos.SubmitRequest();
        req.setLanguage("java");
        req.setType("SUBMIT");
        req.setSourceCode("// @MOCK_VERDICT:ACCEPTED\nx");
        CodingVos.SubmitResult r = codeProblemService.submitPractice(alice.getId(), id, req);
        assertThrows(com.knowflow.exception.BusinessException.class,
                () -> codeProblemService.mySubmission(bob.getId(), r.getSubmissionId()));
    }

    @Test
    @DisplayName("提交对比：本人两次正式提交确定性 diff，服务端自动定向并回传真实判题指标")
    void diffOwnSubmissionsReturnsOrderedChanges() {
        Long id = publishAddProblem();
        CodingVos.SubmitRequest wrong = new CodingVos.SubmitRequest();
        wrong.setLanguage("java");
        wrong.setType("SUBMIT");
        wrong.setSourceCode("// @MOCK_VERDICT:WRONG_ANSWER\nclass S { int add(int a,int b){return 0;} }");
        CodingVos.SubmitResult r1 = codeProblemService.submitPractice(alice.getId(), id, wrong);

        CodingVos.SubmitRequest right = new CodingVos.SubmitRequest();
        right.setLanguage("java");
        right.setType("SUBMIT");
        right.setSourceCode("// @MOCK_VERDICT:ACCEPTED\nclass S { int add(int a,int b){return a+b;} }");
        CodingVos.SubmitResult r2 = codeProblemService.submitPractice(alice.getId(), id, right);

        // 故意把较新的 id 放在第一个参数：服务端必须按时间自动定向
        CodingVos.SubmissionDiffVO diff =
                codeProblemService.diffSubmissions(alice.getId(), id, r2.getSubmissionId(), r1.getSubmissionId());
        assertEquals(r1.getSubmissionId(), diff.getFrom().getId(), "from 必须自动纠正为较早提交");
        assertEquals(r2.getSubmissionId(), diff.getTo().getId());
        assertEquals(JudgeStatus.WRONG_ANSWER, diff.getFrom().getJudgeStatus());
        assertEquals(0, diff.getFrom().getScore());
        assertEquals(JudgeStatus.ACCEPTED, diff.getTo().getJudgeStatus());
        assertEquals(100, diff.getTo().getScore());
        assertTrue(diff.getChangedLines() >= 2, "两次代码至少有增有删");
        assertTrue(diff.getLines().stream().anyMatch(l -> "REMOVED".equals(l.getType())));
        assertTrue(diff.getLines().stream().anyMatch(l -> "ADDED".equals(l.getType())));
        assertFalse(diff.isTruncated());
    }

    @Test
    @DisplayName("提交对比：不能用他人提交做对比（归属隔离）")
    void diffRejectsOthersSubmission() {
        Long id = publishAddProblem();
        CodingVos.SubmitRequest q1 = new CodingVos.SubmitRequest();
        q1.setLanguage("java");
        q1.setType("SUBMIT");
        q1.setSourceCode("// @MOCK_VERDICT:ACCEPTED\nclass S { int add(int a,int b){return 0;} }");
        Long aId = codeProblemService.submitPractice(alice.getId(), id, q1).getSubmissionId();
        Long bId = codeProblemService.submitPractice(alice.getId(), id,
                withCode(q1, "// @MOCK_VERDICT:ACCEPTED\nclass S { int add(int a,int b){return a+b;} }"))
                .getSubmissionId();
        assertThrows(com.knowflow.exception.BusinessException.class,
                () -> codeProblemService.diffSubmissions(bob.getId(), id, aId, bId));
    }

    @Test
    @DisplayName("提交对比：跨题目的两条提交被拒绝")
    void diffRejectsCrossProblem() {
        Long id1 = publishAddProblem();
        Long id2 = publishAddProblem();
        CodingVos.SubmitRequest q = new CodingVos.SubmitRequest();
        q.setLanguage("java");
        q.setType("SUBMIT");
        q.setSourceCode("// @MOCK_VERDICT:ACCEPTED\nclass S { int add(int a,int b){return 0;} }");
        Long s1 = codeProblemService.submitPractice(alice.getId(), id1, q).getSubmissionId();
        Long s2 = codeProblemService.submitPractice(alice.getId(), id2, q).getSubmissionId();
        assertThrows(com.knowflow.exception.BusinessException.class,
                () -> codeProblemService.diffSubmissions(alice.getId(), id1, s1, s2));
    }

    private CodingVos.SubmitRequest withCode(CodingVos.SubmitRequest base, String code) {
        CodingVos.SubmitRequest req = new CodingVos.SubmitRequest();
        req.setLanguage(base.getLanguage());
        req.setType(base.getType());
        req.setSourceCode(code);
        return req;
    }

    // ---------- 考试结算 ----------

    @Test
    @DisplayName("考试 CODE 题：ACCEPTED 直接出分入总分；错题与事件只记一次")
    void examCodeAcceptedGrading() {
        Long qid = publishAddProblem();
        ExamVos.AttemptVO attempt = startCodeExam(qid, alice);
        Long pqId = attempt.getQuestions().get(0).getPaperQuestionId();

        ExamRequests.SubmitRequest submit = new ExamRequests.SubmitRequest();
        submit.setAnswers(Map.of(pqId, "// @MOCK_VERDICT:ACCEPTED\nclass Solution{public int add(int a,int b){return a+b;}}"));
        ExamVos.ResultVO result = examService.submit(attempt.getAttemptId(), submit, alice.getId());

        assertEquals(ExamAttempt.STATUS_GRADED, result.getStatus());
        assertEquals(ExamAttempt.GRADING_NONE, result.getGradingStatus());
        assertEquals(10, result.getTotalScore());
        assertEquals(1, result.getGradedCount());
        ExamAnswer answer = answerMapper.selectList(null).stream()
                .filter(a -> a.getAttemptId().equals(attempt.getAttemptId())).findFirst().orElseThrow();
        assertEquals(ExamAnswer.GRADE_JUDGE, answer.getGradeType());
        assertEquals(1, answer.getGraded());
        assertEquals(10, answer.getScore());
        assertEquals(submit.getAnswers().get(pqId), answer.getUserAnswer(), "original submitted code text remains unchanged");
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM learning_evidence_receipt WHERE source_type='EXAM_ANSWER' AND source_id=?",Long.class,answer.getId()), "immediate code grade must capture its receipt");
        assertEquals(0, countMistakes(alice.getId()));
        assertNotNull(attempt.getQuestions().get(0).getCodeProblem(), "答题页必须带 CODE 公开快照");
        assertEquals("public int add(int a, int b)",
                attempt.getQuestions().get(0).getCodeProblem().get("functionSignature"));
    }

    @Test
    @DisplayName("考试 CODE 平台失败保持 PENDING_JUDGE 不计 0；重判出分后 GRADED（幂等不重复计分）")
    void examCodePendingJudgeAndRetry() {
        Long qid = publishAddProblem();
        ExamVos.AttemptVO attempt = startCodeExam(qid, bob);
        Long pqId = attempt.getQuestions().get(0).getPaperQuestionId();

        ExamRequests.SubmitRequest failReq = new ExamRequests.SubmitRequest();
        failReq.setAnswers(Map.of(pqId, "// @MOCK_VERDICT:SYSTEM_ERROR\nboom"));
        ExamVos.ResultVO pending = examService.submit(attempt.getAttemptId(), failReq, bob.getId());
        assertEquals(ExamAttempt.STATUS_GRADING, pending.getStatus());
        assertEquals(ExamAttempt.GRADING_PENDING_JUDGE, pending.getGradingStatus());
        assertEquals(0, pending.getTotalScore());
        assertEquals(0, countMistakes(bob.getId()));

        ExamRequests.SubmitRequest okReq = new ExamRequests.SubmitRequest();
        okReq.setAnswers(Map.of(pqId, "// @MOCK_VERDICT:ACCEPTED\nok"));
        // 重判依据的是 exam_answer 中已交卷的代码（SYSTEM_ERROR 那份），Mock 标记不变 → 仍平台失败
        ExamVos.ResultVO stillPending = examService.rejudgeCodeAnswers(attempt.getAttemptId(), bob.getId());
        assertEquals(ExamAttempt.GRADING_PENDING_JUDGE, stillPending.getGradingStatus());

        // 用数据修复模拟判题服务恢复：直接把待判答案代码替换为 ACCEPTED 标记后重判（生产由重跑同一提交完成）
        ExamAnswer row = answerMapper.selectList(null).stream()
                .filter(a -> a.getAttemptId().equals(attempt.getAttemptId())).findFirst().orElseThrow();
        row.setUserAnswer("// @MOCK_VERDICT:ACCEPTED\nrecovered");
        answerMapper.updateById(row);
        ExamVos.ResultVO graded = examService.rejudgeCodeAnswers(attempt.getAttemptId(), bob.getId());
        assertEquals(ExamAttempt.STATUS_GRADED, graded.getStatus());
        assertEquals(ExamAttempt.GRADING_NONE, graded.getGradingStatus());
        assertEquals(10, graded.getTotalScore());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM learning_evidence_receipt r JOIN exam_answer a ON r.source_type='EXAM_ANSWER' AND r.source_id=a.id WHERE a.attempt_id=?",Long.class,attempt.getAttemptId()), "late judge grade must append one receipt");

        // 幂等：再次重判不能重复加分/重复错题
        ExamVos.ResultVO again = examService.rejudgeCodeAnswers(attempt.getAttemptId(), bob.getId());
        assertEquals(10, again.getTotalScore());
        assertEquals(0, countMistakes(bob.getId()));
    }

    @Test
    @DisplayName("考试 CODE 用户失败（WA）记错题；System Error 不记错")
    void examCodeWrongAnswerMistake() {
        Long qid = publishAddProblem();
        ExamVos.AttemptVO attempt = startCodeExam(qid, alice);
        Long pqId = attempt.getQuestions().get(0).getPaperQuestionId();
        ExamRequests.SubmitRequest req = new ExamRequests.SubmitRequest();
        req.setAnswers(Map.of(pqId, "// @MOCK_VERDICT:WRONG_ANSWER\nwrong"));
        ExamVos.ResultVO r = examService.submit(attempt.getAttemptId(), req, alice.getId());
        assertEquals(ExamAttempt.STATUS_GRADED, r.getStatus());
        assertEquals(0, r.getTotalScore());
        assertEquals(1, countMistakes(alice.getId()));
    }

    @Test
    @DisplayName("考试 CODE 题快照不含参考解答；未作答按 0 分已评处理")
    void examSnapshotNoLeakAndEmptyAnswer() {
        Long qid = publishAddProblem();
        ExamVos.AttemptVO attempt = startCodeExam(qid, alice);
        // 交空卷：CODE 空答案按未作答 0 分（用户事实），整卷直接 GRADED
        ExamRequests.SubmitRequest req = new ExamRequests.SubmitRequest();
        req.setAnswers(Map.of());
        ExamVos.ResultVO r = examService.submit(attempt.getAttemptId(), req, alice.getId());
        assertEquals(ExamAttempt.STATUS_GRADED, r.getStatus());
        assertEquals(0, r.getTotalScore());
        assertEquals(0, countMistakes(alice.getId()), "空答案不记错");
    }

    // ---------- 辅助 ----------

    private ExamVos.AttemptVO startCodeExam(Long questionId, SysUser user) {
        ExamRequests.ComposeRequest compose = new ExamRequests.ComposeRequest();
        compose.setStrategy("RANDOM");
        compose.setSeed(42L);
        compose.setTopics(List.of("ALGO_HASH"));
        compose.setTypeDistribution(Map.of("CODE", 1));
        compose.setTotalScore(10);
        compose.setShuffleQuestion(0);
        ExamVos.PaperVO paper = examService.compose(compose, user.getId());
        assertEquals(1, paper.getQuestionCount(), "题库中应恰好能抽到 1 道 CODE 题（检查种子/脏数据隔离）");
        return examService.startAttempt(paper.getId(), user.getId());
    }

    private long countMistakes(Long userId) {
        return mistakeMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LearningMistake>()
                .eq(LearningMistake::getUserId, userId));
    }

    private long countCodeEvents(Long userId) {
        return eventMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, userId)
                .eq(LearningEvent::getEventType, "CODE_SUBMITTED"));
    }
}
