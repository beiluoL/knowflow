package com.knowflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.common.LearningEventType;
import com.knowflow.controller.CodeQuestionController;
import com.knowflow.dto.QuizSubmitDTO;
import com.knowflow.entity.CodeQuestion;
import com.knowflow.entity.LearningChapter;
import com.knowflow.entity.LearningEvent;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.entity.LearningMistake;
import com.knowflow.entity.LearningPath;
import com.knowflow.entity.LearningUserPath;
import com.knowflow.entity.QuizQuestion;
import com.knowflow.mapper.CodeQuestionMapper;
import com.knowflow.mapper.LearningChapterMapper;
import com.knowflow.mapper.LearningEventMapper;
import com.knowflow.mapper.LearningFlashcardMapper;
import com.knowflow.mapper.LearningMistakeMapper;
import com.knowflow.mapper.LearningPathMapper;
import com.knowflow.mapper.LearningUserPathMapper;
import com.knowflow.mapper.QuizQuestionMapper;
import com.knowflow.service.impl.LearningServiceImpl;
import com.knowflow.service.impl.MistakeServiceImpl;
import com.knowflow.service.impl.QuizServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1 学习事件闭环集成测试（真实 H2，无 mock）。
 * <p>
 * 核心断言：一次真实学习行为 = 一条核心事件（不重复、不产生历史结果型事件）；
 * 所有事件均经 LearningEventService 统一入口写入；SRS / 掌握度链路不回归。
 * </p>
 */
@SpringBootTest
@ActiveProfiles("local")
class LearningEventClosedLoopTest {

    private static final Long UID = 70001L;

    @Autowired private QuizService quizService;
    @Autowired private LearningService learningService;
    @Autowired private WorkbenchService workbenchService;
    @Autowired private MistakeService mistakeService;
    @Autowired private CodeQuestionController codeQuestionController;
    @Autowired private LearningEventMapper eventMapper;
    @Autowired private QuizQuestionMapper quizQuestionMapper;
    @Autowired private LearningFlashcardMapper flashcardMapper;
    @Autowired private CodeQuestionMapper codeQuestionMapper;
    @Autowired private LearningPathMapper pathMapper;
    @Autowired private LearningChapterMapper chapterMapper;
    @Autowired private LearningUserPathMapper userPathMapper;
    @Autowired private LearningMistakeMapper mistakeMapper;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(UID, null, List.of()));
    }

    private long countEvents(Long userId, String eventType) {
        return eventMapper.selectCount(new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, userId)
                .eq(LearningEvent::getEventType, eventType));
    }

    private long countEventsByResource(Long userId, String eventType, String resourceType, Long resourceId) {
        return eventMapper.selectCount(new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, userId)
                .eq(LearningEvent::getEventType, eventType)
                .eq(LearningEvent::getResourceType, resourceType)
                .eq(LearningEvent::getResourceId, resourceId));
    }

    private QuizQuestion newQuestion(String answer) {
        QuizQuestion q = new QuizQuestion();
        q.setTitle("测试题-" + System.nanoTime());
        q.setContent("题干内容-" + System.nanoTime());
        q.setQuestionType(QuizQuestion.TYPE_SINGLE_CHOICE);
        q.setOptions("[\"A\",\"B\",\"C\"]");
        q.setAnswer(answer);
        q.setDifficulty(1);
        q.setStatus(1);
        quizQuestionMapper.insert(q);
        return q;
    }

    // ============================================================
    // Test 1 & 2：答题正确 / 错误 → 恰好 1 条 QUESTION_ANSWERED
    // ============================================================

    @Test
    @DisplayName("Test1: 答题正确 → 恰好 1 条 QUESTION_ANSWERED(correct=true)，无 QUESTION_CORRECT")
    void quizCorrectEmitsSingleCoreEvent() {
        QuizQuestion q = newQuestion("B");
        QuizSubmitDTO dto = new QuizSubmitDTO();
        QuizSubmitDTO.Item item = new QuizSubmitDTO.Item();
        item.setQuestionId(q.getId());
        item.setUserAnswer("B");
        dto.setAnswers(List.of(item));

        quizService.submit(dto, UID);

        assertEquals(1, countEventsByResource(UID, LearningEventType.QUESTION_ANSWERED.name(), "QUIZ", q.getId()));
        assertEquals(0, countEvents(UID, LearningEventType.QUESTION_CORRECT.name()));
        // 事件结果落在 metadata.correct=true
        LearningEvent ev = eventMapper.selectOne(new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, UID)
                .eq(LearningEvent::getEventType, LearningEventType.QUESTION_ANSWERED.name())
                .eq(LearningEvent::getResourceId, q.getId()));
        assertNotNull(ev);
        assertTrue(ev.getMetadata().contains("\"correct\":true"));
    }

    @Test
    @DisplayName("Test2: 答题错误 → 恰好 1 条 QUESTION_ANSWERED(correct=false)，无 QUESTION_WRONG")
    void quizWrongEmitsSingleCoreEvent() {
        QuizQuestion q = newQuestion("B");
        QuizSubmitDTO dto = new QuizSubmitDTO();
        QuizSubmitDTO.Item item = new QuizSubmitDTO.Item();
        item.setQuestionId(q.getId());
        item.setUserAnswer("A");
        dto.setAnswers(List.of(item));

        quizService.submit(dto, UID);

        assertEquals(1, countEventsByResource(UID, LearningEventType.QUESTION_ANSWERED.name(), "QUIZ", q.getId()));
        assertEquals(0, countEvents(UID, LearningEventType.QUESTION_WRONG.name()));
        LearningEvent ev = eventMapper.selectOne(new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, UID)
                .eq(LearningEvent::getEventType, LearningEventType.QUESTION_ANSWERED.name())
                .eq(LearningEvent::getResourceId, q.getId()));
        assertNotNull(ev);
        assertTrue(ev.getMetadata().contains("\"correct\":false"));
    }

    // ============================================================
    // Test 3：闪卡复习 → 恰好 1 条 FLASHCARD_REVIEWED + SRS 更新
    // ============================================================

    @Test
    @DisplayName("Test3: 闪卡复习 → 1 条 FLASHCARD_REVIEWED 且 SRS 状态更新")
    void flashcardReviewEmitsSingleEventAndUpdatesSrs() {
        LearningFlashcard card = new LearningFlashcard();
        card.setUserId(UID);
        card.setFront("前端问题");
        card.setBack("后端答案");
        card.setDifficulty(1);
        flashcardMapper.insert(card);

        learningService.reviewFlashcard(card.getId(), UID, 5);

        assertEquals(1, countEventsByResource(UID, LearningEventType.FLASHCARD_REVIEWED.name(), "FLASHCARD", card.getId()));
        LearningFlashcard updated = flashcardMapper.selectById(card.getId());
        assertNotNull(updated);
        assertEquals(1, updated.getReviewCount());
        assertEquals(1, updated.getReviewInterval());
        assertNotNull(updated.getNextReviewTime());
        // Phase 0 统一 SRS 后 ease_factor 被持久化（默认 250，q=5 后 260）
        assertNotNull(updated.getEaseFactor());
        assertTrue(updated.getEaseFactor() >= 250);
    }

    @Test
    @DisplayName("Test3b: 闪卡复习携带真实 knowledgeId → 事件 metadata 绑定该知识点（打通 Evidence RECALL 归属）")
    void flashcardReviewWithKnowledgeIdBindsMetadata() {
        LearningFlashcard card = new LearningFlashcard();
        card.setUserId(UID);
        card.setFront("带知识点的闪卡");
        card.setBack("答案");
        card.setDifficulty(1);
        flashcardMapper.insert(card);

        long knowledgeId = 888001L;
        long chapterId = 888002L;
        card.setChapterId(chapterId);
        flashcardMapper.updateById(card);

        learningService.reviewFlashcard(card.getId(), UID, 5, knowledgeId);

        LearningEvent ev = eventMapper.selectOne(new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, UID)
                .eq(LearningEvent::getEventType, LearningEventType.FLASHCARD_REVIEWED.name())
                .eq(LearningEvent::getResourceId, card.getId()));
        assertNotNull(ev);
        // 原有三键不回归
        assertTrue(ev.getMetadata().contains("\"quality\":5"), ev.getMetadata());
        assertTrue(ev.getMetadata().contains("\"lapsed\":"));
        // 新增知识点绑定
        assertTrue(ev.getMetadata().contains("\"knowledgeId\":" + knowledgeId), ev.getMetadata());
        assertTrue(ev.getMetadata().contains("\"chapterId\":" + chapterId), ev.getMetadata());
        // SM-2 链路与三参版本一致
        assertEquals(1, countEventsByResource(UID, LearningEventType.FLASHCARD_REVIEWED.name(), "FLASHCARD", card.getId()));
        assertEquals(1, flashcardMapper.selectById(card.getId()).getReviewCount());
    }

    @Test
    @DisplayName("Test3c: 闪卡复习未携带 knowledgeId → metadata 不出现 knowledgeId（禁止伪造绑定）")
    void flashcardReviewWithoutKnowledgeIdOmitsKey() {
        LearningFlashcard card = new LearningFlashcard();
        card.setUserId(UID);
        card.setFront("无知识点闪卡");
        card.setBack("答案");
        card.setDifficulty(1);
        flashcardMapper.insert(card);

        learningService.reviewFlashcard(card.getId(), UID, 3);

        LearningEvent ev = eventMapper.selectOne(new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, UID)
                .eq(LearningEvent::getEventType, LearningEventType.FLASHCARD_REVIEWED.name())
                .eq(LearningEvent::getResourceId, card.getId()));
        assertNotNull(ev);
        assertFalse(ev.getMetadata().contains("knowledgeId"), ev.getMetadata());
        assertFalse(ev.getMetadata().contains("chapterId"), ev.getMetadata());
    }

    @Test
    @DisplayName("Test3d: knowledgeId 传 0 / 负数 → 视为未知，不写入 metadata")
    void invalidKnowledgeIdIsIgnored() {
        LearningFlashcard card = new LearningFlashcard();
        card.setUserId(UID);
        card.setFront("非法知识点闪卡");
        card.setBack("答案");
        card.setDifficulty(1);
        flashcardMapper.insert(card);

        learningService.reviewFlashcard(card.getId(), UID, 3, 0L);

        LearningEvent ev = eventMapper.selectOne(new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, UID)
                .eq(LearningEvent::getEventType, LearningEventType.FLASHCARD_REVIEWED.name())
                .eq(LearningEvent::getResourceId, card.getId()));
        assertNotNull(ev);
        assertFalse(ev.getMetadata().contains("knowledgeId"), ev.getMetadata());
    }

    // ============================================================
    // Test 4：代码提交 → 恰好 1 条 CODE_SUBMITTED（结果进 metadata）
    // ============================================================

    @Test
    @DisplayName("Test4: 代码提交 → 1 条 CODE_SUBMITTED(passed=true)，无 CODE_PASSED/CODE_FAILED")
    void codeSubmitEmitsSingleCoreEvent() {
        CodeQuestion q = new CodeQuestion();
        q.setTitle("两数之和");
        q.setDescription("给定两个数，返回它们的和");
        q.setDifficulty(1);
        q.setLanguage("javascript");
        q.setStatus(1);
        q.setSortOrder(1);
        codeQuestionMapper.insert(q);

        CodeQuestionController.SubmitPayload payload = new CodeQuestionController.SubmitPayload();
        payload.setCode("const add=(a,b)=>a+b");
        payload.setLanguage("javascript");
        payload.setTotal(3);
        payload.setPassCount(3);

        codeQuestionController.submit(q.getId(), payload);

        assertEquals(1, countEventsByResource(UID, LearningEventType.CODE_SUBMITTED.name(), "CODE_QUESTION", q.getId()));
        assertEquals(0, countEvents(UID, LearningEventType.CODE_PASSED.name()));
        assertEquals(0, countEvents(UID, LearningEventType.CODE_FAILED.name()));
        LearningEvent ev = eventMapper.selectOne(new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, UID)
                .eq(LearningEvent::getEventType, LearningEventType.CODE_SUBMITTED.name())
                .eq(LearningEvent::getResourceId, q.getId()));
        assertNotNull(ev);
        assertTrue(ev.getMetadata().contains("\"passed\":true"));
        assertTrue(ev.getMetadata().contains("\"passCount\":3"));
    }

    // ============================================================
    // Test 5：章节完成 → 恰好 1 条 CHAPTER_COMPLETE
    // ============================================================

    @Test
    @DisplayName("Test5: 章节完成（非末章）→ 恰好 1 条 CHAPTER_COMPLETE，无 PATH_COMPLETED")
    void chapterCompleteEmitsSingleCoreEvent() {
        LearningPath path = new LearningPath();
        path.setTitle("Java 基础");
        path.setStatus(1);
        path.setChapterCount(2);
        pathMapper.insert(path);

        LearningChapter c1 = new LearningChapter();
        c1.setPathId(path.getId());
        c1.setTitle("第一章");
        c1.setSortOrder(1);
        chapterMapper.insert(c1);
        LearningChapter c2 = new LearningChapter();
        c2.setPathId(path.getId());
        c2.setTitle("第二章");
        c2.setSortOrder(2);
        chapterMapper.insert(c2);

        LearningUserPath up = new LearningUserPath();
        up.setUserId(UID);
        up.setPathId(path.getId());
        up.setProgress(new BigDecimal("0"));
        up.setCompletedChapters(0);
        up.setEnrollTime(LocalDateTime.now());
        userPathMapper.insert(up);

        learningService.completeChapter(c1.getId(), UID);

        assertEquals(1, countEventsByResource(UID, LearningEventType.CHAPTER_COMPLETE.name(), "CHAPTER", c1.getId()));
        assertEquals(0, countEvents(UID, LearningEventType.PATH_COMPLETED.name()));
    }

    // ============================================================
    // Test 6：重复性 —— 同一业务操作只产生一条核心事件（含错题复习）
    // ============================================================

    @Test
    @DisplayName("Test6: 错题标记已掌握 → 恰好 1 条 MISTAKE_REVIEW；无历史结果型事件产生")
    void mistakeReviewEmitsSingleEvent() {
        LearningMistake mistake = new LearningMistake();
        mistake.setUserId(UID);
        mistake.setQuestion("1+1=?");
        mistake.setWrongAnswer("3");
        mistake.setCorrectAnswer("2");
        mistake.setCategory("测验");
        mistake.setSource("quiz");
        mistakeMapper.insert(mistake);

        mistakeService.markMastered(mistake.getId(), UID);

        assertEquals(1, countEventsByResource(UID, LearningEventType.MISTAKE_REVIEW.name(), "MISTAKE", mistake.getId()));
        // 全局断言：本测试会话从未产生历史结果型事件
        assertEquals(0, countEvents(UID, LearningEventType.QUESTION_CORRECT.name()));
        assertEquals(0, countEvents(UID, LearningEventType.QUESTION_WRONG.name()));
        assertEquals(0, countEvents(UID, LearningEventType.CODE_PASSED.name()));
        assertEquals(0, countEvents(UID, LearningEventType.CODE_FAILED.name()));
    }

    @Test
    @DisplayName("Test6b: 一次答题提交两个答案 → 每个资源各 1 条，无重复")
    void multiAnswerNoDuplicate() {
        QuizQuestion q1 = newQuestion("A");
        QuizQuestion q2 = newQuestion("B");
        QuizSubmitDTO dto = new QuizSubmitDTO();
        QuizSubmitDTO.Item i1 = new QuizSubmitDTO.Item();
        i1.setQuestionId(q1.getId());
        i1.setUserAnswer("A");
        QuizSubmitDTO.Item i2 = new QuizSubmitDTO.Item();
        i2.setQuestionId(q2.getId());
        i2.setUserAnswer("B");
        dto.setAnswers(List.of(i1, i2));

        quizService.submit(dto, UID);

        assertEquals(1, countEventsByResource(UID, LearningEventType.QUESTION_ANSWERED.name(), "QUIZ", q1.getId()));
        assertEquals(1, countEventsByResource(UID, LearningEventType.QUESTION_ANSWERED.name(), "QUIZ", q2.getId()));
    }
}
