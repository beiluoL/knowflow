package com.knowflow.exam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.SysUser;
import com.knowflow.exam.ai.ExamAiService;
import com.knowflow.exam.dto.ExamRequests;
import com.knowflow.exam.entity.ExamAnswer;
import com.knowflow.exam.entity.ExamAttempt;
import com.knowflow.exam.entity.ExamPaper;
import com.knowflow.exam.entity.ExamPaperQuestion;
import com.knowflow.exam.mapper.ExamAnswerMapper;
import com.knowflow.exam.mapper.ExamAttemptMapper;
import com.knowflow.exam.mapper.ExamPaperMapper;
import com.knowflow.exam.mapper.ExamPaperQuestionMapper;
import com.knowflow.exam.service.ExamService;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.support.FakeAiConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 3F 任务 B：考试并发与幂等集成测试。
 *
 * <p>使用真实 Spring 上下文 + H2 内存库，CountDownLatch/CyclicBarrier 制造可控并发，
 * 断言并发结束后的「最终数据状态」（exam_attempt / exam_answer / learning_mistake /
 * learning_event 的行数与取值），而不是只断言 HTTP 200。
 *
 * <p>注意：这是 H2(MODE=MySQL) 验证，不等同于生产 MySQL 行为（见报告 NOT_VERIFIED 项）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import({FakeAiConfiguration.class, ExamConcurrencyIntegrationTest.StubExamAiConfig.class})
@TestPropertySource(properties = {
        // 独占内存库：本类上下文（StubExamAiConfig）与 ContentStudio/AI 评卷测试上下文不同，
        // 复用同名库会让 schema.sql 在同一 H2 mem 库上执行第二次，CREATE INDEX 重复导致上下文加载失败。
        "knowflow.datasource.h2.url=jdbc:h2:mem:phase3f-exam-concurrency;DB_CLOSE_DELAY=-1;MODE=MySQL;LOCK_TIMEOUT=10000",
        "server.address=127.0.0.1",
        "knowflow.datasource.allow-runtime-switch=false",
        "knowflow.exam.timeout-scheduler.enabled=false"
})
class ExamConcurrencyIntegrationTest {

    @Autowired ExamService examService;
    @Autowired SysUserMapper userMapper;
    @Autowired ExamPaperMapper paperMapper;
    @Autowired ExamPaperQuestionMapper paperQuestionMapper;
    @Autowired ExamAttemptMapper attemptMapper;
    @Autowired ExamAnswerMapper answerMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    private final List<ExecutorService> pools = new ArrayList<>();

    @AfterEach
    void tearDown() {
        StubExamAiConfig.reset();
        for (ExecutorService p : pools) {
            p.shutdownNow();
        }
        pools.clear();
    }

    private ExecutorService pool(int n) {
        ExecutorService p = Executors.newFixedThreadPool(n);
        pools.add(p);
        return p;
    }

    private SysUser newUser() {
        SysUser u = new SysUser();
        u.setUsername("exam-conc-" + UUID.randomUUID());
        u.setPassword("test-only");
        u.setNickname("Exam Concurrency");
        u.setRole("USER");
        userMapper.insert(u);
        return u;
    }

    /** 创建试卷：2 道单选（答案索引均为 "1"），可选 1 道简答（PENDING_AI）。返回题目快照列表。 */
    private Fixture createFixture(SysUser user, boolean withSubjective) {
        ExamPaper paper = new ExamPaper();
        paper.setName("并发测试卷 " + UUID.randomUUID());
        paper.setDomain("JAVA");
        paper.setSource("TEMPLATE");
        paper.setStrategy("RANDOM");
        paper.setDurationMinutes(60);
        paper.setTotalScore((withSubjective ? 3 : 2) * 5);
        paper.setPassScore(60);
        paper.setMode("EXAM");
        paper.setShuffleQuestion(0);
        paper.setShuffleOption(0);
        paper.setAllowPause(1);
        paper.setAllowChange(1);
        paper.setReviewStatus("APPROVED");
        paper.setOwnerUserId(user.getId());
        paper.setStatus(1);
        paperMapper.insert(paper);

        List<ExamPaperQuestion> pqs = new ArrayList<>();
        pqs.add(insertChoice(paper, 1, "concurrency.q1", "第一题", "选项A", "选项B"));
        pqs.add(insertChoice(paper, 2, "concurrency.q2", "第二题", "选项A", "选项B"));
        if (withSubjective) {
            pqs.add(insertShortAnswer(paper, 3, "concurrency.sa"));
        }
        return new Fixture(paper, pqs);
    }

    private ExamPaperQuestion insertChoice(ExamPaper paper, int ordinal, String topic,
                                           String title, String optA, String optB) {
        ExamPaperQuestion pq = new ExamPaperQuestion();
        pq.setPaperId(paper.getId());
        pq.setOrdinal(ordinal);
        pq.setScore(5);
        pq.setQuestionType("SINGLE_CHOICE");
        pq.setTopic(topic);
        pq.setScoringRule("ALL_OR_NOTHING");
        pq.setSnapshot(snapshot(title, title + " 题干", List.of(optA, optB, "选项C", "选项D"), "1"));
        paperQuestionMapper.insert(pq);
        return pq;
    }

    private ExamPaperQuestion insertShortAnswer(ExamPaper paper, int ordinal, String topic) {
        ExamPaperQuestion pq = new ExamPaperQuestion();
        pq.setPaperId(paper.getId());
        pq.setOrdinal(ordinal);
        pq.setScore(5);
        pq.setQuestionType("SHORT_ANSWER");
        pq.setTopic(topic);
        pq.setScoringRule("ALL_OR_NOTHING");
        pq.setSnapshot(snapshot("简答题", "请说明线程池核心参数的含义。", List.of(),
                "核心线程数、最大线程数、队列、拒绝策略等。"));
        paperQuestionMapper.insert(pq);
        return pq;
    }

    private String snapshot(String title, String content, List<String> options, String answer) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", title);
            m.put("content", content);
            m.put("options", options);
            m.put("answer", answer);
            m.put("explanation", "测试解析");
            m.put("difficulty", 2);
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private record Fixture(ExamPaper paper, List<ExamPaperQuestion> questions) {
    }

    private ExamRequests.SubmitRequest submitReq(Fixture f, String first, String second, String subjective) {
        Map<Long, String> answers = new LinkedHashMap<>();
        answers.put(f.questions().get(0).getId(), first);
        answers.put(f.questions().get(1).getId(), second);
        if (f.questions().size() > 2 && subjective != null) {
            answers.put(f.questions().get(2).getId(), subjective);
        }
        Map<Long, Integer> timeCosts = new LinkedHashMap<>();
        for (ExamPaperQuestion pq : f.questions()) {
            timeCosts.put(pq.getId(), 10);
        }
        ExamRequests.SubmitRequest req = new ExamRequests.SubmitRequest();
        req.setAnswers(answers);
        req.setTimeCosts(timeCosts);
        req.setMarked(List.of());
        req.setClientVersion("concurrency-test");
        return req;
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private long answerRows(Long attemptId) {
        return count("SELECT COUNT(*) FROM exam_answer WHERE attempt_id=? AND deleted=0", attemptId);
    }

    private long duplicateAnswerRows(Long attemptId) {
        return count("SELECT COUNT(*) FROM (SELECT paper_question_id FROM exam_answer WHERE attempt_id=? "
                + "AND deleted=0 GROUP BY paper_question_id HAVING COUNT(*) > 1)", attemptId);
    }

    private long inProgressFor(SysUser u, ExamPaper paper) {
        return count("SELECT COUNT(*) FROM exam_attempt WHERE user_id=? AND paper_id=? AND status='IN_PROGRESS' AND deleted=0",
                u.getId(), paper.getId());
    }

    private long mistakes(SysUser u) {
        return count("SELECT COUNT(*) FROM learning_mistake WHERE user_id=? AND source='EXAM' AND deleted=0", u.getId());
    }

    private long answeredEvents(SysUser u) {
        return count("SELECT COUNT(*) FROM learning_event WHERE user_id=? AND event_type='QUESTION_ANSWERED' AND deleted=0",
                u.getId());
    }

    /** 场景 1：同用户多设备同时开考，只能产生一个 IN_PROGRESS（返回同一 attempt）。 */
    @Test
    void concurrentStartAttemptProducesSingleInProgress() throws Exception {
        SysUser u = newUser();
        Fixture f = createFixture(u, false);
        int n = 8;
        CyclicBarrier barrier = new CyclicBarrier(n);
        ExecutorService pool = pool(n);
        List<Future<Long>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return examService.startAttempt(f.paper().getId(), u.getId()).getAttemptId();
            }));
        }
        Long first = null;
        for (Future<Long> fut : futures) {
            Long id = fut.get(15, TimeUnit.SECONDS);
            if (first == null) {
                first = id;
            }
            assertEquals(first, id, "并发开考必须复用同一个 attempt");
        }
        assertEquals(1, inProgressFor(u, f.paper()));
        assertEquals(1, count("SELECT COUNT(*) FROM exam_attempt WHERE user_id=? AND paper_id=? AND deleted=0",
                u.getId(), f.paper().getId()));
    }

    /** 场景 2：同一 attempt 并发交卷，只判分一次：答案行唯一、错题/事件不重复。 */
    @Test
    void concurrentSubmitGradesExactlyOnce() throws Exception {
        SysUser u = newUser();
        Fixture f = createFixture(u, true);
        Long attemptId = examService.startAttempt(f.paper().getId(), u.getId()).getAttemptId();
        // q1 正确("1")，q2 错误("0")，主观题已作答（交卷后 PENDING_AI）
        ExamRequests.SubmitRequest req = submitReq(f, "1", "0", "核心线程数、最大线程数、工作队列与拒绝策略。");

        int n = 8;
        CyclicBarrier barrier = new CyclicBarrier(n);
        ExecutorService pool = pool(n);
        AtomicInteger errors = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                    examService.submit(attemptId, req, u.getId());
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            }));
        }
        for (Future<?> fut : futures) {
            fut.get(20, TimeUnit.SECONDS);
        }
        assertEquals(0, errors.get(), "并发交卷不应抛出业务异常（失败者回放已有结果）");

        ExamAttempt attempt = attemptMapper.selectById(attemptId);
        assertEquals(3, answerRows(attemptId), "每题恰好一行答案");
        assertEquals(0, duplicateAnswerRows(attemptId), "不允许任何题出现重复答案行");
        assertEquals(ExamAttempt.STATUS_GRADING, attempt.getStatus());
        assertEquals(ExamAttempt.GRADING_PENDING_AI, attempt.getGradingStatus());

        List<ExamAnswer> answers = answerMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExamAnswer>()
                                .eq(ExamAnswer::getAttemptId, attemptId));
        long gradedObjective = answers.stream().filter(a -> a.getGraded() != null && a.getGraded() == 1).count();
        long pendingSubjective = answers.stream()
                .filter(a -> "SHORT_ANSWER".equals(paperQuestionMapper.selectById(a.getPaperQuestionId()).getQuestionType()))
                .filter(a -> a.getGraded() == 0 && a.getScore() == null).count();
        assertEquals(2, gradedObjective, "客观题全部已评");
        assertEquals(1, pendingSubjective, "主观题保持 PENDING 且分数为 NULL（不记 0）");

        assertEquals(1, mistakes(u), "唯一答错的客观题只入一次错题本");
        assertEquals(2, answeredEvents(u), "只有已评分客观题写 QUESTION_ANSWERED，且各一次");
    }

    /** 场景 3：交卷后保存草稿被拒绝（条件 UPDATE，0 行 → 409），不会回写交卷状态。 */
    @Test
    void draftAfterSubmitIsRejectedAndCannotOverwriteGradedState() {
        SysUser u = newUser();
        Fixture f = createFixture(u, false);
        Long attemptId = examService.startAttempt(f.paper().getId(), u.getId()).getAttemptId();
        examService.submit(attemptId, submitReq(f, "1", "0", null), u.getId());

        ExamRequests.SaveDraftRequest draft = new ExamRequests.SaveDraftRequest();
        draft.setAnswers(Map.of(f.questions().get(0).getId(), "0"));
        draft.setMarked(List.of());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> examService.saveDraft(attemptId, draft, u.getId()));
        assertEquals(409, ex.getCode());
        ExamAttempt attempt = attemptMapper.selectById(attemptId);
        assertEquals(ExamAttempt.STATUS_GRADED, attempt.getStatus(), "草稿不得把交卷状态改回 IN_PROGRESS");
    }

    /** 场景 4：交卷与草稿保存并发，结束后答案集唯一、状态终结，草稿不得覆盖交卷。 */
    @Test
    void concurrentDraftAndSubmitLeavesConsistentState() throws Exception {
        SysUser u = newUser();
        Fixture f = createFixture(u, false);
        Long attemptId = examService.startAttempt(f.paper().getId(), u.getId()).getAttemptId();

        ExamRequests.SaveDraftRequest draft = new ExamRequests.SaveDraftRequest();
        draft.setAnswers(Map.of(f.questions().get(0).getId(), "1", f.questions().get(1).getId(), "1"));
        draft.setMarked(List.of());

        int n = 6;
        CyclicBarrier barrier = new CyclicBarrier(n);
        ExecutorService pool = pool(n);
        AtomicInteger draft409 = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final boolean submitter = i == 0;
            futures.add(pool.submit(() -> {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                    if (submitter) {
                        examService.submit(attemptId, submitReq(f, "1", "1", null), u.getId());
                    } else {
                        try {
                            examService.saveDraft(attemptId, draft, u.getId());
                        } catch (BusinessException e) {
                            if (e.getCode() == 409) {
                                draft409.incrementAndGet();
                            } else {
                                unexpected.incrementAndGet();
                            }
                        }
                    }
                } catch (Exception e) {
                    if (!submitter && e instanceof BusinessException be && be.getCode() == 409) {
                        draft409.incrementAndGet();
                    } else {
                        unexpected.incrementAndGet();
                    }
                }
            }));
        }
        for (Future<?> fut : futures) {
            fut.get(20, TimeUnit.SECONDS);
        }
        // 注意：不能要求一定有草稿拿到 409——五个草稿可能全部在交卷状态翻转前完成（200 也是合法调度）。
        // 「交卷后到达的草稿必须 409」这一确定性保证由场景 3 串行覆盖；
        // 并发场景只断言与线程调度无关的最终状态不变量。
        assertEquals(0, unexpected.get(), "除草稿 409 外不应有其他异常");
        assertEquals(2, answerRows(attemptId));
        assertEquals(0, duplicateAnswerRows(attemptId));
        assertEquals(ExamAttempt.STATUS_GRADED, attemptMapper.selectById(attemptId).getStatus());
    }

    /** 场景 5：超时自动交卷以截止前草稿判分；重复扫描幂等。 */
    @Test
    void expiredAttemptIsAutoSubmittedFromDraftOnce() {
        SysUser u = newUser();
        Fixture f = createFixture(u, false);
        Long attemptId = examService.startAttempt(f.paper().getId(), u.getId()).getAttemptId();

        ExamRequests.SaveDraftRequest draft = new ExamRequests.SaveDraftRequest();
        draft.setAnswers(Map.of(f.questions().get(0).getId(), "1", f.questions().get(1).getId(), "1"));
        draft.setMarked(List.of());
        examService.saveDraft(attemptId, draft, u.getId());

        jdbc.update("UPDATE exam_attempt SET server_deadline=? WHERE id=?", LocalDateTime.now().minusMinutes(1), attemptId);

        int first = examService.autoSubmitExpiredAttempts(50);
        assertEquals(1, first);
        int second = examService.autoSubmitExpiredAttempts(50);
        assertEquals(0, second, "已自动交卷的尝试不得重复处理");

        ExamAttempt attempt = attemptMapper.selectById(attemptId);
        assertEquals(ExamAttempt.STATUS_GRADED, attempt.getStatus());
        assertEquals("AUTO_TIMEOUT", attempt.getClientVersion());
        assertEquals(2, answerRows(attemptId));
        assertEquals(0, mistakes(u), "草稿答案全部正确，不应产生错题");
        assertEquals(2, answeredEvents(u));
    }

    /** 场景 6：超时自动交卷与用户手动交卷并发，仍然只判分一次。 */
    @Test
    void autoSubmitRacesManualSubmitGradesOnce() throws Exception {
        SysUser u = newUser();
        Fixture f = createFixture(u, false);
        Long attemptId = examService.startAttempt(f.paper().getId(), u.getId()).getAttemptId();
        ExamRequests.SaveDraftRequest draft = new ExamRequests.SaveDraftRequest();
        draft.setAnswers(Map.of(f.questions().get(0).getId(), "1", f.questions().get(1).getId(), "1"));
        draft.setMarked(List.of());
        examService.saveDraft(attemptId, draft, u.getId());
        jdbc.update("UPDATE exam_attempt SET server_deadline=? WHERE id=?", LocalDateTime.now().minusSeconds(30), attemptId);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = pool(2);
        AtomicInteger errors = new AtomicInteger();
        Future<?> auto = pool.submit(() -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                examService.autoSubmitExpiredAttempts(50);
            } catch (Exception e) {
                errors.incrementAndGet();
            }
        });
        Future<?> manual = pool.submit(() -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                // 超时后的手动交卷只采信草稿（两题均正确）
                examService.submit(attemptId, submitReq(f, "0", "0", null), u.getId());
            } catch (Exception e) {
                errors.incrementAndGet();
            }
        });
        auto.get(20, TimeUnit.SECONDS);
        manual.get(20, TimeUnit.SECONDS);
        assertEquals(0, errors.get());
        assertEquals(2, answerRows(attemptId));
        assertEquals(0, duplicateAnswerRows(attemptId));
        ExamAttempt attempt = attemptMapper.selectById(attemptId);
        assertEquals(ExamAttempt.STATUS_GRADED, attempt.getStatus());
        assertEquals(0, mistakes(u), "超时后请求体答案不采信，草稿全对 → 无错题");
    }

    /** 场景 7：并发 AI 补评只有一个请求真正执行；另一个收到 409，答案/错题/事件均不重复。 */
    @Test
    void concurrentAiGradingRunsExactlyOnce() throws Exception {
        SysUser u = newUser();
        Fixture f = createFixture(u, true);
        Long attemptId = examService.startAttempt(f.paper().getId(), u.getId()).getAttemptId();
        examService.submit(attemptId,
                submitReq(f, "1", "0", "核心线程数、最大线程数、工作队列与拒绝策略。"), u.getId());
        assertEquals(ExamAttempt.GRADING_PENDING_AI, attemptMapper.selectById(attemptId).getGradingStatus());

        StubExamAiConfig.entered = new CountDownLatch(1);
        StubExamAiConfig.release = new CountDownLatch(1);
        StubExamAiConfig.totalForAll = 80;

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = pool(2);
        AtomicInteger conflict = new AtomicInteger();
        AtomicInteger otherError = new AtomicInteger();
        Future<?> a = pool.submit(() -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                examService.runAiGrading(attemptId, u.getId());
            } catch (Exception e) {
                if (e instanceof BusinessException be && be.getCode() == 409) {
                    conflict.incrementAndGet();
                } else {
                    otherError.incrementAndGet();
                }
            }
        });
        Future<?> b = pool.submit(() -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                examService.runAiGrading(attemptId, u.getId());
            } catch (Exception e) {
                if (e instanceof BusinessException be && be.getCode() == 409) {
                    conflict.incrementAndGet();
                } else {
                    otherError.incrementAndGet();
                }
            }
        });
        assertTrue(StubExamAiConfig.entered.await(5, TimeUnit.SECONDS), "首个补评应已进入模型调用");
        StubExamAiConfig.release.countDown();
        a.get(20, TimeUnit.SECONDS);
        b.get(20, TimeUnit.SECONDS);

        assertEquals(1, conflict.get(), "并发补评必须恰好一方被 409 拒绝");
        assertEquals(0, otherError.get());
        assertEquals(1, StubExamAiConfig.calls.get(), "模型只允许被调用一次");

        ExamAttempt attempt = attemptMapper.selectById(attemptId);
        assertEquals(ExamAttempt.STATUS_GRADED, attempt.getStatus());
        assertEquals(ExamAttempt.GRADING_AI_GRADED, attempt.getGradingStatus());
        assertEquals(3, answerRows(attemptId));
        long aiGraded = count("SELECT COUNT(*) FROM exam_answer WHERE attempt_id=? AND graded=1 AND grade_type='AI' AND deleted=0",
                attemptId);
        assertEquals(1, aiGraded, "主观题只被 AI 评分一次");
        assertEquals(1, mistakes(u), "主观题 80 分判对不入错题；仅保留唯一客观错题");
        assertEquals(3, answeredEvents(u), "主观题评分事件只写一次");
    }

    /** 场景 8：AI 全部失败保持 PENDING（不记 0、不入错题），随后重试成功，最终只评分一次。 */
    @Test
    void aiFailureKeepsPendingThenRetrySucceeds() {
        SysUser u = newUser();
        Fixture f = createFixture(u, true);
        Long attemptId = examService.startAttempt(f.paper().getId(), u.getId()).getAttemptId();
        examService.submit(attemptId,
                submitReq(f, "1", "0", "核心线程数、最大线程数、工作队列与拒绝策略。"), u.getId());

        StubExamAiConfig.failAll = true;
        var failed = examService.runAiGrading(attemptId, u.getId());
        assertEquals(ExamAttempt.STATUS_GRADING_FAILED, failed.getStatus());
        assertEquals(ExamAttempt.GRADING_AI_FAILED, failed.getGradingStatus());
        assertEquals(0, count("SELECT COUNT(*) FROM exam_answer WHERE attempt_id=? AND graded=1 AND grade_type='AI' AND deleted=0",
                attemptId));
        assertEquals(1, count("SELECT COUNT(*) FROM exam_answer WHERE attempt_id=? AND graded=0 AND score IS NULL AND deleted=0",
                attemptId), "失败题保持未评分且分数 NULL");
        assertEquals(1, mistakes(u), "AI 失败不新增错题");
        assertEquals(2, answeredEvents(u), "AI 失败不写评分事件");

        StubExamAiConfig.failAll = false;
        StubExamAiConfig.totalForAll = 30; // 30 分 → 判错，应入错题一次
        var ok = examService.runAiGrading(attemptId, u.getId());
        assertEquals(ExamAttempt.STATUS_GRADED, ok.getStatus());
        assertEquals(ExamAttempt.GRADING_AI_GRADED, ok.getGradingStatus());
        assertEquals(1, count("SELECT COUNT(*) FROM exam_answer WHERE attempt_id=? AND graded=1 AND grade_type='AI' AND deleted=0",
                attemptId));
        assertEquals(2, mistakes(u), "重试成功且判错后，主观题错题只入一次");
        assertEquals(3, answeredEvents(u));
        assertEquals(3, answerRows(attemptId), "重试不得插入重复答案行");
    }

    /** 场景 9：其他用户不能触发本人 attempt 的 AI 补评（归属校验）。 */
    @Test
    void otherUserCannotTriggerAiGrading() {
        SysUser owner = newUser();
        SysUser other = newUser();
        Fixture f = createFixture(owner, true);
        Long attemptId = examService.startAttempt(f.paper().getId(), owner.getId()).getAttemptId();
        examService.submit(attemptId,
                submitReq(f, "1", "0", "核心线程数、最大线程数、工作队列与拒绝策略。"), owner.getId());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> examService.runAiGrading(attemptId, other.getId()));
        assertEquals(403, ex.getCode());
        assertEquals(ExamAttempt.GRADING_PENDING_AI, attemptMapper.selectById(attemptId).getGradingStatus(),
                "越权请求不得改变评分状态");
    }

    /**
     * 可控 AI 评卷 Stub：进入模型调用时发信号并在闸门处等待，用于制造并发窗口；
     * 支持全部失败（rawError）与固定分数脚本。不是真实模型 E2E。
     */
    @TestConfiguration
    static class StubExamAiConfig {

        static volatile CountDownLatch entered;
        static volatile CountDownLatch release;
        static volatile AtomicInteger calls = new AtomicInteger(0);
        static volatile int totalForAll = 80;
        static volatile boolean failAll = false;

        static void reset() {
            entered = null;
            release = null;
            calls = new AtomicInteger(0);
            totalForAll = 80;
            failAll = false;
        }

        @Bean
        @Primary
        ExamAiService stubExamAiService(com.knowflow.service.AiService aiService, ObjectMapper objectMapper) {
            return new ExamAiService(aiService, objectMapper) {
                @Override
                public boolean isConfigured() {
                    return true;
                }

                @Override
                public List<AiGrade> gradeSubjective(List<GradeInput> inputs, Long userId, String model) {
                    calls.incrementAndGet();
                    if (entered != null) {
                        entered.countDown();
                    }
                    if (release != null) {
                        try {
                            release.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    List<AiGrade> out = new ArrayList<>();
                    for (GradeInput in : inputs) {
                        if (failAll) {
                            out.add(new AiGrade(in.paperQuestionId(), 0, 0, Map.of(),
                                    List.of(), List.of(), List.of("STUB_FAILURE"), List.of(), List.of(),
                                    -1, "STUB_AI_FAILED"));
                        } else {
                            int t = totalForAll;
                            Map<String, Integer> dims = Map.of(
                                    "technicalAccuracy", t, "principle", t, "completeness", t,
                                    "engineering", t, "expression", t);
                            out.add(new AiGrade(in.paperQuestionId(), t, 80, dims,
                                    List.of("stub 命中点"), List.of(), List.of(),
                                    List.of("stub 建议"), List.of("考生原话片段"), t, null));
                        }
                    }
                    return out;
                }
            };
        }
    }
    @Test
    void staleTabCannotReplaceNewerDraft() {
        SysUser u=newUser();Fixture f=createFixture(u,false);
        long id=examService.startAttempt(f.paper().getId(),u.getId()).getAttemptId();
        var first=new ExamRequests.SaveDraftRequest();first.setAnswers(Map.of(f.questions().get(0).getId(),"1"));first.setMarked(List.of());
        examService.saveDraft(id,first,u.getId());
        var stale=new ExamRequests.SaveDraftRequest();stale.setAnswers(Map.of(f.questions().get(0).getId(),"0"));stale.setMarked(List.of());
        assertEquals(409,assertThrows(BusinessException.class,()->examService.saveDraft(id,stale,u.getId())).getCode());
        assertEquals("1",examService.getAttempt(id,u.getId()).getDraft().get(f.questions().get(0).getId()));
    }
    @Test
    void staleSubmitVersionCannotOverwriteSavedAnswersButFinalReplayRemainsIdempotent() {
        SysUser u=newUser();Fixture f=createFixture(u,false);long id=examService.startAttempt(f.paper().getId(),u.getId()).getAttemptId();
        var draft=new ExamRequests.SaveDraftRequest();draft.setExpectedRevision(0L);draft.setAnswers(Map.of(f.questions().get(0).getId(),"1"));examService.saveDraft(id,draft,u.getId());
        var stale=submitReq(f,"0","0",null);stale.setExpectedRevision(0L);
        assertEquals(409,assertThrows(BusinessException.class,()->examService.submit(id,stale,u.getId())).getCode());assertEquals(0,answerRows(id));
        stale.setExpectedRevision(1L);var result=examService.submit(id,stale,u.getId());assertEquals(0,result.getTotalScore());
        stale.setExpectedRevision(0L);assertEquals(result.getTotalScore(),examService.submit(id,stale,u.getId()).getTotalScore());assertEquals(2,answerRows(id));
    }
}
