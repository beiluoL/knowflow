package com.knowflow.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.knowflow.ai.capability.AiCapabilityExecutor;
import com.knowflow.entity.LearningMistake;
import com.knowflow.entity.QuizQuestion;
import com.knowflow.entity.SysUser;
import com.knowflow.exam.dto.ExamRequests;
import com.knowflow.mapper.LearningMistakeMapper;
import com.knowflow.mapper.QuizQuestionMapper;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.service.LearningAnalyticsService;
import com.knowflow.service.QuizService;
import com.knowflow.utils.JwtUtils;
import com.knowflow.vo.LearningAnalyticsVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3H 链路 B：AI 系统级完全关闭（knowflow.ai.capability.enabled=false）时的真实业务链路。
 *
 * <p>必须全部通过且不发起任何模型调用：
 * 题库 → 规则组卷/推荐 → 在线答题 → 客观判分 → 错题 → 学习分析。</p>
 *
 * <p>本上下文不导入 FakeAiConfiguration：使用真实 AiServiceImpl。local profile 无 API Key，
 * 且系统开关关闭——任何 AI 路径在执行器入口即 SYSTEM_DISPOSED 快速失败。
 * “零真实付费 HTTP 调用”由代码路径静态保证 + tokensUsedToday==0 行为断言；
 * 真实供应商网络抓包为 NOT_VERIFIED（本地无 Key）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "knowflow.datasource.h2.url=jdbc:h2:mem:phase3h-ai-disabled;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "server.address=127.0.0.1",
        "knowflow.datasource.allow-runtime-switch=false",
        "knowflow.exam.timeout-scheduler.enabled=false",
        "knowflow.ai.capability.enabled=false"
})
class AiDisabledChainIntegrationTest {

    private static final String TOPIC = "AI.JAVA.OFFLINE";

    @Autowired MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Autowired JwtUtils jwt;
    @Autowired SysUserMapper userMapper;
    @Autowired QuizQuestionMapper questionMapper;
    @Autowired LearningMistakeMapper mistakeMapper;
    @Autowired QuizService quizService;
    @Autowired LearningAnalyticsService analyticsService;
    @Autowired AiCapabilityExecutor capabilityExecutor;
    @Autowired com.knowflow.coding.service.CodeProblemService codeProblemService;
    @Autowired com.knowflow.coding.judge.CodeJudgeGateway judgeGateway;

    private SysUser user;
    private String token;

    @BeforeEach
    void setUp() {
        user = new SysUser();
        user.setUsername("p3h-off-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        user.setPassword("test-only");
        user.setNickname("P3H Offline");
        user.setRole("USER");
        userMapper.insert(user);
        token = "Bearer " + jwt.generateToken(user.getId(), user.getUsername(), user.getRole());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getId(), null, List.of()));
    }

    private void seedBank() {
        for (int i = 0; i < 4; i++) {
            insert(QuizQuestion.TYPE_SINGLE_CHOICE, "B");
        }
        for (int i = 0; i < 2; i++) {
            insert(QuizQuestion.TYPE_MULTIPLE_CHOICE, "AB");
        }
        for (int i = 0; i < 2; i++) {
            insert(QuizQuestion.TYPE_TRUE_FALSE, "true");
        }
        insert(QuizQuestion.TYPE_FILL_BLANK, "int");
        insert(QuizQuestion.TYPE_SHORT_ANSWER, "同一接口不同实现");
    }

    private QuizQuestion insert(String type, String answer) {
        QuizQuestion q = new QuizQuestion();
        q.setTitle("离线题 " + type + " " + UUID.randomUUID());
        q.setContent("题干");
        q.setQuestionType(type);
        q.setOptions(type.equals(QuizQuestion.TYPE_FILL_BLANK)
                || type.equals(QuizQuestion.TYPE_SHORT_ANSWER) ? null : "[\"A\",\"B\",\"C\"]");
        q.setAnswer(answer);
        q.setExplanation("标准解析 " + answer);
        q.setDifficulty(1);
        q.setTopicCode(TOPIC);
        q.setStatus(1);
        questionMapper.insert(q);
        return q;
    }

    @Test
    @DisplayName("能力状态：系统开关关闭 → DISABLED（前端进入基础模式）")
    void statusDisabled() throws Exception {
        mvc.perform(get("/api/ai-capability/status").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.useBasicMode").value(true));
    }

    @Test
    @DisplayName("链路B-1：规则推荐组卷在 AI 全关时正常，rule-engine-v2 且题量满足")
    void smartRecommendOffline() throws Exception {
        seedBank();
        mvc.perform(get("/api/quiz/smart-recommend")
                        .header("Authorization", token)
                        .param("topics", TOPIC).param("count", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ruleVersion").value("rule-engine-v2"))
                .andExpect(jsonPath("$.data.sufficient").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(5));
        assertEquals(0, capabilityExecutor.tokensUsedToday(),
                "链路 B 全程不允许消耗任何模型 token");
    }

    @Test
    @DisplayName("链路B-2：确定性规则组卷端点在 AI 全关时正常成卷")
    void ruleComposeOffline() throws Exception {
        seedBank();
        ExamRequests.ComposeRequest req = new ExamRequests.ComposeRequest();
        req.setName("离线规则卷");
        req.setDomain("JAVA");
        req.setTopics(List.of(TOPIC));
        req.setStrategy("RANDOM");
        req.setQuestionCount(5);
        req.setDifficulties(List.of(1));
        req.setMode("PRACTICE");
        mvc.perform(post("/api/exam/papers/compose")
                        .header("Authorization", token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists());
    }

    @Test
    @DisplayName("链路B-3：题目讲解在 AI 全关时返回 BASIC + 标准解析")
    void explainBasicOffline() throws Exception {
        QuizQuestion q = insert(QuizQuestion.TYPE_SINGLE_CHOICE, "B");
        mvc.perform(get("/api/quiz/explain")
                        .header("Authorization", token)
                        .param("questionId", String.valueOf(q.getId()))
                        .param("userAnswer", "A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("BASIC"))
                .andExpect(jsonPath("$.data.answer").value("B"))
                .andExpect(jsonPath("$.data.hasExplanation").value(true));
    }

    @Test
    @DisplayName("链路B 全链路：答题 → 客观判分 → 错题归集 → 学习分析，零 AI 依赖")
    void fullOfflineLearningChain() {
        QuizQuestion q = insert(QuizQuestion.TYPE_SINGLE_CHOICE, "B");

        // 在线答题（错误答案）→ 客观题确定性判分
        com.knowflow.dto.QuizSubmitDTO dto = new com.knowflow.dto.QuizSubmitDTO();
        com.knowflow.dto.QuizSubmitDTO.Item item = new com.knowflow.dto.QuizSubmitDTO.Item();
        item.setQuestionId(q.getId());
        item.setUserAnswer("A");
        dto.setAnswers(List.of(item));
        var submitResult = quizService.submit(dto, user.getId());
        // 客观题必须有确定性判分结果（不依赖 AI）
        assertTrue(submitResult != null, "提交判分结果不能为 null");

        // 错题归集
        List<LearningMistake> mistakes = mistakeMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LearningMistake>()
                        .eq(LearningMistake::getUserId, user.getId())
                        .eq(LearningMistake::getQuestionId, q.getId()));
        assertEquals(1, mistakes.size());
        assertEquals("QUIZ", mistakes.get(0).getSource());

        // 学习分析：该作答已判分、判错，正确率口径正确
        LearningAnalyticsVO vo = analyticsService.overview(user.getId(), 30);
        assertEquals(1, vo.getQuiz().getTotalAnswered());
        assertEquals(1, vo.getQuiz().getGradedCount());
        assertEquals(0, vo.getQuiz().getCorrectCount());
        assertEquals(0.0, vo.getQuiz().getCorrectRate(), 0.01);
        assertEquals(0, vo.getQuiz().getUngradedCount());
        assertTrue(vo.getMistake().getTotal() >= 1);
        assertTrue(vo.getMistake().getPending() >= 1, "新错题必须进入未掌握队列");
        // 既有 SRS 口径：归集时 lastReviewTime=now，当天不算到期（次日进入 dueToday）
        // 与 MistakeService.getDueTodayCount 完全一致，此处锁定该口径防止未来漂移
        assertEquals(0, vo.getMistake().getDueToday());

        // 零 token 消耗：AI 全关没有任何付费调用发生
        assertEquals(0, capabilityExecutor.tokensUsedToday());
    }

    @Test
    @DisplayName("场景15 安全门禁：Judge 未部署（disabled）时提交落 JUDGE_UNAVAILABLE，"
            + "不计分不记错不产生事件，且绝不会自动回退到宿主机执行器")
    void judgeDisabledNeverFallsBackToHost() {
        // 门禁自证：默认 profile 下网关必须是 disabled 且 executionEnabled=false
        assertEquals("DISABLED", judgeGateway.mode());
        assertFalse(judgeGateway.executionEnabled(),
                "不安全的宿主机执行器绝不能因 AI/Judge 故障被自动启用");

        // admin 建题并发布
        SysUser admin = new SysUser();
        admin.setUsername("p3h-adm-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        admin.setPassword("test-only");
        admin.setRole("ADMIN");
        admin.setNickname("P3H Admin");
        userMapper.insert(admin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin.getId(), null, List.of()));

        com.knowflow.coding.vo.CodingVos.SaveProblemRequest pr =
                new com.knowflow.coding.vo.CodingVos.SaveProblemRequest();
        pr.setTitle("门禁题 " + UUID.randomUUID());
        pr.setContent("# 实现 add");
        pr.setDifficulty(1);
        pr.setTopicCode("ALGO_BASE");
        pr.setSupportedLanguages("java");
        pr.setFunctionSignature("public int add(int a, int b)");
        pr.setStarterCode("class Solution { public int add(int a,int b){return 0;} }");
        pr.setConstraints("1..100");
        pr.setExamples("[]");
        pr.setTimeLimitMs(1000);
        pr.setMemoryLimitMb(256);
        pr.setReferenceSolution("class Solution { public int add(int a,int b){return a+b;} }");
        pr.setStatus(0);
        com.knowflow.coding.vo.CodingVos.SaveCaseRequest c =
                new com.knowflow.coding.vo.CodingVos.SaveCaseRequest();
        c.setInput("[1,2]");
        c.setExpectedOutput("3");
        c.setVisibility("PUBLIC");
        c.setWeight(1);
        c.setOrderNo(0);
        pr.setCases(List.of(c));
        Long pid = codeProblemService.saveProblem(pr, admin.getId());
        codeProblemService.review(pid, "APPROVED", "ok");

        // 普通用户提交
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getId(), null, List.of()));
        com.knowflow.coding.vo.CodingVos.SubmitRequest sr =
                new com.knowflow.coding.vo.CodingVos.SubmitRequest();
        sr.setLanguage("java");
        sr.setType("SUBMIT");
        sr.setSourceCode("class Solution { public int add(int a,int b){return a-b;} }");
        com.knowflow.coding.vo.CodingVos.SubmitResult r =
                codeProblemService.submitPractice(user.getId(), pid, sr);

        assertEquals(com.knowflow.coding.judge.JudgeStatus.JUDGE_UNAVAILABLE, r.getJudgeStatus());
        assertNull(r.getScore(), "Judge 不可用时不得给分（包括 0 分）");

        long mistakes = mistakeMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LearningMistake>()
                        .eq(LearningMistake::getUserId, user.getId())
                        .eq(LearningMistake::getQuestionId, pid));
        assertEquals(0, mistakes, "Judge 不可用不是用户错误，不得记入错题本");

        // 分析口径：平台失败被单列剔除，首次通过率分母不含该题
        LearningAnalyticsVO vo = analyticsService.overview(user.getId(), 30);
        assertEquals(1, vo.getCode().getExcludedPlatformFailure());
        assertEquals(0, vo.getCode().getAttemptedQuestions());
        assertEquals(0, vo.getCode().getTotalSubmissions());
    }
}
