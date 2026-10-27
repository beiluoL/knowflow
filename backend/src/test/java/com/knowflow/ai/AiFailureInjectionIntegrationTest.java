package com.knowflow.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.LearningMistake;
import com.knowflow.entity.QuizAnswerRecord;
import com.knowflow.entity.QuizQuestion;
import com.knowflow.entity.SysUser;
import com.knowflow.exam.ai.ExamAiService;
import com.knowflow.exam.dto.ExamRequests;
import com.knowflow.mapper.LearningMistakeMapper;
import com.knowflow.mapper.QuizAnswerRecordMapper;
import com.knowflow.mapper.QuizQuestionMapper;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.service.LearningAnalyticsService;
import com.knowflow.service.QuizService;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import com.knowflow.vo.LearningAnalyticsVO;
import com.knowflow.vo.QuizExplainVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3H 故障注入集成测试（FakeAiConfiguration 脚本化替身 + 真实 H2 + 真实 Spring 链路）。
 *
 * <p>覆盖任务书场景：未配置 Key、429/5xx/超时分类透传、非法 JSON、缺字段/错误题型、
 * 重复题目、用户侧 AI 状态、AI 出卷降级题库组卷、AI 讲解降级基础解析、AI 评分失败不造假分、
 * 错题不重复写入、一次作答一条事件、未评分主观题不计正确率。</p>
 *
 * <p>边界：Fake 是 Service 层替身，不模拟真实 HTTP 状态码（401/429/500 报文）；
 * HTTP 客户端状态码映射由 AiCapabilityExecutorTest（kind 重试语义）与代码静态路径共同保证，
 * 真实供应商报文验证为 NOT_VERIFIED。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
@TestPropertySource(properties = {
        // 故障分类由FakeAiConfiguration注入；关闭链路另由AiDisabledChainIntegrationTest验证。
        "knowflow.ai.capability.enabled=true",
        "knowflow.datasource.h2.url=jdbc:h2:mem:phase3h-ai-fault;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "server.address=127.0.0.1",
        "knowflow.datasource.allow-runtime-switch=false",
        "knowflow.exam.timeout-scheduler.enabled=false"
})
class AiFailureInjectionIntegrationTest {

    private static final String TOPIC = "AI.JAVA.BASE";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtUtils jwt;
    @Autowired SysUserMapper userMapper;
    @Autowired QuizQuestionMapper questionMapper;
    @Autowired QuizAnswerRecordMapper answerRecordMapper;
    @Autowired LearningMistakeMapper mistakeMapper;
    @Autowired com.knowflow.exam.mapper.ExamPaperQuestionMapper paperQuestionMapper;
    @Autowired QuizService quizService;
    @Autowired ExamAiService examAi;
    @Autowired LearningAnalyticsService analyticsService;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        FakeAiConfiguration.reset();
    }

    private SysUser newUser(String tag) {
        SysUser u = new SysUser();
        u.setUsername("p3h-" + tag + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        u.setPassword("test-only");
        u.setNickname("P3H " + tag);
        u.setRole("USER");
        userMapper.insert(u);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u.getId(), null, List.of()));
        return u;
    }

    private String bearer(SysUser u) {
        return "Bearer " + jwt.generateToken(u.getId(), u.getUsername(), u.getRole());
    }

    private void seedBank() {
        // 4 单选 / 2 多选 / 2 判断 / 1 填空 / 1 简答，满足 AI 降级卷 5 题分布 2:1:1:1:1
        for (int i = 0; i < 4; i++) {
            insert(TOPIC, QuizQuestion.TYPE_SINGLE_CHOICE, 1,
                    "AI 降级单选 " + i + " " + UUID.randomUUID(),
                    "单选题干 " + i, "[\"A\",\"B\",\"C\"]", "A");
        }
        for (int i = 0; i < 2; i++) {
            insert(TOPIC, QuizQuestion.TYPE_MULTIPLE_CHOICE, 1,
                    "AI 降级多选 " + i + " " + UUID.randomUUID(),
                    "多选题干 " + i, "[\"A\",\"B\",\"C\"]", "AB");
        }
        for (int i = 0; i < 2; i++) {
            insert(TOPIC, QuizQuestion.TYPE_TRUE_FALSE, 1,
                    "AI 降级判断 " + i + " " + UUID.randomUUID(),
                    "判断题干 " + i, null, "true");
        }
        insert(TOPIC, QuizQuestion.TYPE_FILL_BLANK, 1,
                "AI 降级填空 " + UUID.randomUUID(), "Java 定义整型关键字是？", null, "int");
        insert(TOPIC, QuizQuestion.TYPE_SHORT_ANSWER, 1,
                "AI 降级简答 " + UUID.randomUUID(), "简述什么是多态。", null, "同一接口不同实现");
    }

    private QuizQuestion insert(String topic, String type, int difficulty, String title,
                                String content, String options, String answer) {
        QuizQuestion q = new QuizQuestion();
        q.setTitle(title);
        q.setContent(content);
        q.setQuestionType(type);
        q.setOptions(options);
        q.setAnswer(answer);
        q.setExplanation(title.contains("无解析") ? null : "标准解析：" + answer);
        q.setDifficulty(difficulty);
        q.setTopicCode(topic);
        q.setStatus(1);
        questionMapper.insert(q);
        return q;
    }

    // ============ 场景1/11：能力状态（未配置 / 正常） ============

    @Test
    @DisplayName("能力状态：未配置 Key → UNCONFIGURED（不泄露 Key/堆栈，且不触发付费探测）")
    void statusUnconfigured() throws Exception {
        SysUser u = newUser("unconf");
        FakeAiConfiguration.configured = false;
        mvc.perform(get("/api/ai-capability/status").header("Authorization", bearer(u)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("UNCONFIGURED"))
                .andExpect(jsonPath("$.data.configured").value(false))
                .andExpect(jsonPath("$.data.useBasicMode").value(true));
    }

    @Test
    @DisplayName("能力状态：已配置且无近期失败 → AVAILABLE")
    void statusAvailable() throws Exception {
        SysUser u = newUser("ok");
        mvc.perform(get("/api/ai-capability/status").header("Authorization", bearer(u)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
    }

    // ============ 场景 P0：规则组卷不依赖 AI ============

    @Test
    @DisplayName("规则推荐组卷：AI 未配置时仍 200，返回 rule-engine-v2 与选题原因")
    void smartRecommendWorksWithoutAi() throws Exception {
        seedBank();
        SysUser u = newUser("recommend");
        FakeAiConfiguration.configured = false;
        int callsBefore = FakeAiConfiguration.completeCalls;
        mvc.perform(get("/api/quiz/smart-recommend")
                        .header("Authorization", bearer(u))
                        .param("topics", TOPIC).param("count", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ruleVersion").value("rule-engine-v2"))
                .andExpect(jsonPath("$.data.sufficient").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(5));
        assertEquals(callsBefore, FakeAiConfiguration.completeCalls,
                "规则组卷链路绝不允许调用模型");
    }

    // ============ 场景3/5/7/8/9/10：AI 出卷降级 ============

    @Test
    @DisplayName("AI 出卷：供应商 5xx 故障 → 自动切换题库组卷并显式标注 aiFallback")
    void aiComposeFallsBackOnProviderError() throws Exception {
        seedBank();
        SysUser u = newUser("compose-5xx");
        FakeAiConfiguration.nextFailureKind =
                com.knowflow.ai.capability.AiFailureKind.PROVIDER_ERROR;
        ExamRequests.AiComposeRequest req = new ExamRequests.AiComposeRequest();
        req.setName("故障降级卷");
        req.setTopic(TOPIC);
        req.setCount(5);
        req.setDifficulties(List.of(1));
        mvc.perform(post("/api/exam/papers/ai")
                        .header("Authorization", bearer(u))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiFallback").value(true))
                .andExpect(jsonPath("$.data.fallbackReason").value(
                        org.hamcrest.Matchers.containsString("AI_PROVIDER_ERROR")))
                .andExpect(jsonPath("$.data.id").exists())
                .andDo(result -> {
                    com.fasterxml.jackson.databind.JsonNode root =
                            objectMapper.readTree(result.getResponse().getContentAsString());
                    long paperId = root.path("data").path("id").asLong();
                    Long questionRows = paperQuestionMapper.selectCount(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                                    com.knowflow.exam.entity.ExamPaperQuestion>()
                                    .eq(com.knowflow.exam.entity.ExamPaperQuestion::getPaperId, paperId));
                    assertEquals(5, questionRows, "降级卷必须保持原数量要求，不允许偷减题量");
                });
    }

    @Test
    @DisplayName("AI 出卷：429 限流（可重试失败耗尽）→ 同样降级题库组卷")
    void aiComposeFallsBackOnRateLimited() throws Exception {
        seedBank();
        SysUser u = newUser("compose-429");
        FakeAiConfiguration.nextFailureKind =
                com.knowflow.ai.capability.AiFailureKind.RATE_LIMITED;
        ExamRequests.AiComposeRequest req = new ExamRequests.AiComposeRequest();
        req.setName("限流降级卷");
        req.setTopic(TOPIC);
        req.setCount(5);
        req.setDifficulties(List.of(1));
        mvc.perform(post("/api/exam/papers/ai")
                        .header("Authorization", bearer(u))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.data.aiFallback").value(true))
                .andExpect(jsonPath("$.data.fallbackReason").value(
                        org.hamcrest.Matchers.containsString("AI_RATE_LIMITED")));
    }

    @Test
    @DisplayName("AI 出卷：非法 JSON → 校验失败降级，不把脏数据当候选题")
    void aiComposeInvalidJsonFallsBack() throws Exception {
        seedBank();
        SysUser u = newUser("compose-badjson");
        FakeAiConfiguration.script("这不是JSON，也没有题目结构");
        ExamRequests.AiComposeRequest req = new ExamRequests.AiComposeRequest();
        req.setName("非法JSON降级卷");
        req.setTopic(TOPIC);
        req.setCount(5);
        req.setDifficulties(List.of(1));
        mvc.perform(post("/api/exam/papers/ai")
                        .header("Authorization", bearer(u))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.data.aiFallback").value(true));
    }

    @Test
    @DisplayName("AI 出卷：返回未知题型 ESSAY_999 → 逐题校验拒绝并降级")
    void aiComposeWrongTypeFallsBack() throws Exception {
        seedBank();
        SysUser u = newUser("compose-badtype");
        FakeAiConfiguration.script("""
                {"questions":[{"title":"未知题型题","content":"内容","questionType":"ESSAY_999",
                "options":[],"answer":"x","explanation":"无","difficulty":1,"topic":"%s"}]}
                """.formatted(TOPIC));
        ExamRequests.AiComposeRequest req = new ExamRequests.AiComposeRequest();
        req.setName("错误题型降级卷");
        req.setTopic(TOPIC);
        req.setCount(5);
        req.setDifficulties(List.of(1));
        mvc.perform(post("/api/exam/papers/ai")
                        .header("Authorization", bearer(u))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.data.aiFallback").value(true))
                .andExpect(jsonPath("$.data.fallbackReason").value(
                        org.hamcrest.Matchers.containsString("VALIDATION_SHORTAGE")));
    }

    @Test
    @DisplayName("AI 出卷：卷内语义重复（同指纹 2 题只要 1）→ 去重后不足并降级")
    void aiComposeDuplicateFallsBack() throws Exception {
        seedBank();
        SysUser u = newUser("compose-dup");
        FakeAiConfiguration.script("""
                {"questions":[
                {"title":"完全相同的题目标题","content":"相同内容","questionType":"SINGLE_CHOICE",
                 "options":["A","B"],"answer":"A","explanation":"e","difficulty":1,"topic":"%s"},
                {"title":"完全相同的题目标题","content":"相同内容","questionType":"SINGLE_CHOICE",
                 "options":["A","B"],"answer":"A","explanation":"e","difficulty":1,"topic":"%s"}]}
                """.formatted(TOPIC, TOPIC));
        ExamRequests.AiComposeRequest req = new ExamRequests.AiComposeRequest();
        req.setName("重复题降级卷");
        req.setTopic(TOPIC);
        req.setCount(2);
        req.setDifficulties(List.of(1));
        mvc.perform(post("/api/exam/papers/ai")
                        .header("Authorization", bearer(u))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.data.aiFallback").value(true));
    }

    @Test
    @DisplayName("AI 出卷：fallbackToBank=0 时维持旧契约（直接 422，不偷偷组卷）")
    void aiComposeNoFallbackFlagReturnsError() throws Exception {
        seedBank();
        SysUser u = newUser("compose-nofb");
        FakeAiConfiguration.nextFailureKind =
                com.knowflow.ai.capability.AiFailureKind.PROVIDER_ERROR;
        ExamRequests.AiComposeRequest req = new ExamRequests.AiComposeRequest();
        req.setName("不降级");
        req.setTopic(TOPIC);
        req.setCount(5);
        req.setFallbackToBank(0);
        mvc.perform(post("/api/exam/papers/ai")
                        .header("Authorization", bearer(u))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.code").value(422));
    }

    // ============ 4.2：AI 讲解降级 ============

    @Test
    @DisplayName("AI 讲解：429 失败 → BASIC 模式 + failureKind=AI_RATE_LIMITED + 标准解析可用")
    void explainFallsBackToBasicOnRateLimit() {
        SysUser u = newUser("explain-429");
        QuizQuestion q = insert(TOPIC, QuizQuestion.TYPE_SINGLE_CHOICE, 1,
                "讲解降级题 " + UUID.randomUUID(), "1+1=?", "[\"1\",\"2\"]", "2");
        FakeAiConfiguration.nextFailureKind =
                com.knowflow.ai.capability.AiFailureKind.RATE_LIMITED;
        QuizExplainVO vo = quizService.explainQuestion(q.getId(), "1", u.getId());
        assertEquals("BASIC", vo.getMode());
        assertEquals("AI_RATE_LIMITED", vo.getFailureKind());
        assertEquals("2", vo.getAnswer());
        assertTrue(vo.isHasExplanation());
        assertNotNull(vo.getExplanation());
    }

    @Test
    @DisplayName("AI 讲解：未配置 Key → BASIC 模式且零模型调用")
    void explainBasicWhenUnconfigured() {
        SysUser u = newUser("explain-unconf");
        QuizQuestion q = insert(TOPIC, QuizQuestion.TYPE_SINGLE_CHOICE, 1,
                "无AI讲解题 " + UUID.randomUUID(), "2+2=?", "[\"3\",\"4\"]", "4");
        FakeAiConfiguration.configured = false;
        int callsBefore = FakeAiConfiguration.completeCalls;
        QuizExplainVO vo = quizService.explainQuestion(q.getId(), "3", u.getId());
        assertEquals("BASIC", vo.getMode());
        assertEquals(callsBefore, FakeAiConfiguration.completeCalls);
    }

    @Test
    @DisplayName("AI 讲解降级：题库无解析时显式暂无解析，绝不编造")
    void explainBasicWithoutExplanationDoesNotFabricate() {
        SysUser u = newUser("explain-noexp");
        QuizQuestion q = new QuizQuestion();
        q.setTitle("无解析题 " + UUID.randomUUID());
        q.setContent("没有解析的题");
        q.setQuestionType(QuizQuestion.TYPE_SINGLE_CHOICE);
        q.setOptions("[\"A\",\"B\"]");
        q.setAnswer("B");
        q.setDifficulty(1);
        q.setTopicCode(TOPIC);
        q.setStatus(1);
        questionMapper.insert(q);

        FakeAiConfiguration.nextFailureKind =
                com.knowflow.ai.capability.AiFailureKind.TIMEOUT;
        QuizExplainVO vo = quizService.explainQuestion(q.getId(), "A", u.getId());
        assertEquals("BASIC", vo.getMode());
        assertFalse(vo.isHasExplanation());
        assertNull(vo.getExplanation());
        assertNull(vo.getContent(), "AI 失败且无标准解析时 content 必须为 null，前端显式暂无解析");
    }

    // ============ 4.3：AI 评分失败不造假分 ============

    @Test
    @DisplayName("AI 主观评分：供应商限流时每题 rawError=AI_RATE_LIMITED，total100=0 且 valid=false（调用方不得写分）")
    void aiGradeFailureMarksNoFakeScore() {
        newUser("grade-fail");
        FakeAiConfiguration.nextFailureKind =
                com.knowflow.ai.capability.AiFailureKind.RATE_LIMITED;
        ExamAiService.GradeInput input = new ExamAiService.GradeInput(
                9001L, "简答题", "简述 JVM 内存结构", "SHORT_ANSWER",
                "堆/栈/方法区", "我觉得是内存");
        List<ExamAiService.AiGrade> grades = examAi.gradeSubjective(List.of(input), 90001L, null);
        assertEquals(1, grades.size());
        ExamAiService.AiGrade g = grades.get(0);
        assertFalse(g.valid());
        assertEquals("AI_RATE_LIMITED", g.rawError());
        assertEquals(0, g.total100(), "AI 失败时绝不能写入任何分数（包括默认 0 分）");
    }

    @Test
    @DisplayName("AI 主观评分：未配置 → AI_NOT_CONFIGURED 且零模型调用")
    void aiGradeUnconfiguredNoCall() {
        newUser("grade-unconf");
        FakeAiConfiguration.configured = false;
        int callsBefore = FakeAiConfiguration.completeCalls;
        ExamAiService.GradeInput input = new ExamAiService.GradeInput(
                9002L, "简答题", "题目", "SHORT_ANSWER", "参考答案", "学生答案");
        List<ExamAiService.AiGrade> grades = examAi.gradeSubjective(List.of(input), 90002L, null);
        assertFalse(grades.get(0).valid());
        assertEquals("AI_NOT_CONFIGURED", grades.get(0).rawError());
        assertEquals(callsBefore, FakeAiConfiguration.completeCalls);
    }

    // ============ 错题幂等 + 事件不重复 + 分析口径 ============

    private com.knowflow.dto.QuizSubmitDTO.Item answer(long qid, String ans) {
        com.knowflow.dto.QuizSubmitDTO.Item item = new com.knowflow.dto.QuizSubmitDTO.Item();
        item.setQuestionId(qid);
        item.setUserAnswer(ans);
        return item;
    }

    @Test
    @DisplayName("同一题连续两次答错：错题只写 1 行（reviewCount 累加），不产生重复错题")
    void mistakeNotDuplicatedOnRepeatedWrong() {
        SysUser u = newUser("mistake-idem");
        QuizQuestion q = insert(TOPIC, QuizQuestion.TYPE_SINGLE_CHOICE, 1,
                "幂等错题 " + UUID.randomUUID(), "3+3=?", "[\"5\",\"6\"]", "6");
        com.knowflow.dto.QuizSubmitDTO dto = new com.knowflow.dto.QuizSubmitDTO();
        dto.setAnswers(List.of(answer(q.getId(), "5")));
        quizService.submit(dto, u.getId());
        quizService.submit(dto, u.getId());

        List<LearningMistake> rows = mistakeMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LearningMistake>()
                        .eq(LearningMistake::getUserId, u.getId())
                        .eq(LearningMistake::getQuestionId, q.getId()));
        assertEquals(1, rows.size(), "错题必须幂等合并，不允许重复写入");
        assertEquals(1, rows.get(0).getReviewCount(), "第二次答错应累加复习次数");
        assertEquals(0, rows.get(0).getMastered());
    }

    @Test
    @DisplayName("一次作答只产生一条 QUESTION_ANSWERED 事件（同一资源维度）")
    void singleAnswerSingleEvent() {
        SysUser u = newUser("event-idem");
        QuizQuestion q = insert(TOPIC, QuizQuestion.TYPE_SINGLE_CHOICE, 1,
                "事件幂等题 " + UUID.randomUUID(), "1+2=?", "[\"2\",\"3\"]", "3");
        com.knowflow.dto.QuizSubmitDTO dto = new com.knowflow.dto.QuizSubmitDTO();
        dto.setAnswers(List.of(answer(q.getId(), "3")));
        quizService.submit(dto, u.getId());

        Long count = answerRecordMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<QuizAnswerRecord>()
                        .eq(QuizAnswerRecord::getUserId, u.getId())
                        .eq(QuizAnswerRecord::getQuestionId, q.getId()));
        // 事件经 LearningEventClosedLoopTest 已断言，此处复核作答记录也只一条
        assertEquals(1, count);
    }

    @Test
    @DisplayName("学习分析口径：is_correct=NULL 的未评分记录进入 ungradedCount，不计正确率分母")
    void analyticsExcludesUngradedFromCorrectRate() {
        SysUser u = newUser("analytics");
        QuizQuestion wrong = insert(TOPIC, QuizQuestion.TYPE_SINGLE_CHOICE, 1,
                "分析错题 " + UUID.randomUUID(), "1+1=?", "[\"1\",\"2\"]", "2");
        QuizQuestion right = insert(TOPIC, QuizQuestion.TYPE_SINGLE_CHOICE, 1,
                "分析对题 " + UUID.randomUUID(), "2+2=?", "[\"3\",\"4\"]", "4");
        QuizQuestion pending = insert(TOPIC, QuizQuestion.TYPE_SHORT_ANSWER, 1,
                "待评分题 " + UUID.randomUUID(), "简述什么是闭包。", null, "函数与引用环境");

        com.knowflow.dto.QuizSubmitDTO wrongDto = new com.knowflow.dto.QuizSubmitDTO();
        wrongDto.setAnswers(List.of(answer(wrong.getId(), "1")));
        quizService.submit(wrongDto, u.getId());
        com.knowflow.dto.QuizSubmitDTO rightDto = new com.knowflow.dto.QuizSubmitDTO();
        rightDto.setAnswers(List.of(answer(right.getId(), "4")));
        quizService.submit(rightDto, u.getId());

        // 模拟一条未完成 AI/人工评分的主观题记录（is_correct=NULL）。
        // 不能走 MyBatis insert：该列在 schema 中有 DEFAULT 0，null 字段被省略后会落默认值，
        // 无法模拟“未评分”语义。
        jdbc.update("INSERT INTO quiz_answer_record("
                        + "user_id, question_id, user_answer, is_correct, create_time, update_time, deleted) "
                        + "VALUES (?, ?, ?, NULL, NOW(), NOW(), 0)",
                u.getId(), pending.getId(), "闭包就是函数");

        LearningAnalyticsVO vo = analyticsService.overview(u.getId(), 30);
        assertEquals(3, vo.getQuiz().getTotalAnswered());
        assertEquals(2, vo.getQuiz().getGradedCount());
        assertEquals(1, vo.getQuiz().getCorrectCount());
        assertEquals(1, vo.getQuiz().getUngradedCount(),
                "未评分主观题必须单列，绝不能计入正确率");
        assertEquals(50.0, vo.getQuiz().getCorrectRate(), 0.01);
    }

    @Test
    @DisplayName("AI 出题校验：缺字段（无 answer）的候选题被拒绝并走降级")
    void aiComposeMissingFieldFallsBack() throws Exception {
        seedBank();
        SysUser u = newUser("compose-missing");
        FakeAiConfiguration.script("""
                {"questions":[{"title":"缺答案题","content":"内容","questionType":"SINGLE_CHOICE",
                "options":["A","B"],"explanation":"e","difficulty":1,"topic":"%s"}]}
                """.formatted(TOPIC));
        ExamRequests.AiComposeRequest req = new ExamRequests.AiComposeRequest();
        req.setName("缺字段降级卷");
        req.setTopic(TOPIC);
        req.setCount(5);
        req.setDifficulties(List.of(1));
        mvc.perform(post("/api/exam/papers/ai")
                        .header("Authorization", bearer(u))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.data.aiFallback").value(true));
    }
}
