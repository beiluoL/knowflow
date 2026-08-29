package com.knowflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.entity.CodeSubmitRecord;
import com.knowflow.entity.DocReadProgress;
import com.knowflow.entity.KgEntity;
import com.knowflow.entity.LearningEvent;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.entity.LearningMistake;
import com.knowflow.entity.QuizAnswerRecord;
import com.knowflow.entity.ResourceKnowledgeMapping;
import com.knowflow.entity.WbRecallSession;
import com.knowflow.entity.WbReviewCard;
import com.knowflow.entity.WbReviewLog;
import com.knowflow.mapper.CodeSubmitRecordMapper;
import com.knowflow.mapper.DocReadProgressMapper;
import com.knowflow.mapper.KgEntityMapper;
import com.knowflow.mapper.KnowledgeMasteryMapper;
import com.knowflow.mapper.LearningFlashcardMapper;
import com.knowflow.mapper.LearningMistakeMapper;
import com.knowflow.mapper.QuizAnswerRecordMapper;
import com.knowflow.mapper.ResourceKnowledgeMappingMapper;
import com.knowflow.mapper.WbRecallSessionMapper;
import com.knowflow.mapper.WbReviewCardMapper;
import com.knowflow.mapper.WbReviewLogMapper;
import com.knowflow.mastery.MasteryConfig;
import com.knowflow.service.impl.KnowledgeMasteryServiceImpl;
import com.knowflow.vo.KnowledgeMasteryDetailVO;
import com.knowflow.vo.MasteryDiagnosticsVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 2-B 知识点掌握度引擎集成测试（真实 H2，无 mock）。
 * <p>覆盖：信号聚合数学、有效信号归一化、状态机、置信度/遗忘风险公式、幂等整行重算、
 * 事件触发、冷启动优雅跳过、诊断可观察性。</p>
 */
@SpringBootTest
@ActiveProfiles("local")
class MasteryEngineIntegrationTest {

    @Autowired private KnowledgeMasteryService masteryService;
    @Autowired private KnowledgeMasteryMapper masteryMapper;
    @Autowired private ResourceKnowledgeMappingMapper mappingMapper;
    @Autowired private KgEntityMapper kgEntityMapper;
    @Autowired private QuizAnswerRecordMapper quizMapper;
    @Autowired private CodeSubmitRecordMapper codeMapper;
    @Autowired private WbRecallSessionMapper recallMapper;
    @Autowired private WbReviewCardMapper reviewCardMapper;
    @Autowired private WbReviewLogMapper reviewLogMapper;
    @Autowired private LearningFlashcardMapper flashcardMapper;
    @Autowired private LearningMistakeMapper mistakeMapper;
    @Autowired private DocReadProgressMapper docProgressMapper;

    private static final long UID = 70001L;
    // 静态自增，跨测试方法不重置（H2 内存库跨方法持久化，避免 ID 冲突）
    private static long seq = 500000L;

    @BeforeEach
    void cleanup() {
        masteryMapper.delete(new LambdaQueryWrapper<>());
        mappingMapper.delete(new LambdaQueryWrapper<>());
        kgEntityMapper.delete(new LambdaQueryWrapper<>());
        quizMapper.delete(new LambdaQueryWrapper<>());
        codeMapper.delete(new LambdaQueryWrapper<>());
        recallMapper.delete(new LambdaQueryWrapper<>());
        reviewCardMapper.delete(new LambdaQueryWrapper<>());
        reviewLogMapper.delete(new LambdaQueryWrapper<>());
        flashcardMapper.delete(new LambdaQueryWrapper<>());
        mistakeMapper.delete(new LambdaQueryWrapper<>());
        docProgressMapper.delete(new LambdaQueryWrapper<>());
    }

    // ---- 工具：实体 + 映射 ----
    private long kg(String type) {
        KgEntity e = new KgEntity();
        long id = 91000L + (++seq);
        e.setId(id);
        e.setName("KP-" + id);
        e.setType(type);
        e.setCategoryId(1L);
        e.setDocId(1L);
        kgEntityMapper.insert(e);
        return id;
    }

    private void map(long kid, String resType, long resId, String status) {
        ResourceKnowledgeMapping m = new ResourceKnowledgeMapping();
        m.setId(92000L + (++seq));
        m.setKnowledgeId(kid);
        m.setResourceType(resType);
        m.setResourceId(resId);
        m.setSource(MasteryConfig.MAP_SOURCE_AUTO);
        m.setStatus(status);
        mappingMapper.insert(m);
    }

    // ============================================================
    // 信号聚合数学
    // ============================================================

    @Test
    @DisplayName("S1 QUIZ 信号：正确率拉普拉斯平滑 (1+1)/(1+0+2)=67 分，状态 LEARNING")
    void quizCorrectSignal() {
        long kid = kg("CONCEPT");
        long qid = 93001L;
        map(kid, MasteryConfig.RES_QUIZ, qid, MasteryConfig.MAP_STATUS_ACCEPTED);
        QuizAnswerRecord r = new QuizAnswerRecord();
        r.setId(93001L); r.setUserId(UID); r.setQuestionId(qid); r.setIsCorrect(1);
        r.setCreateTime(LocalDateTime.now());
        quizMapper.insert(r);

        masteryService.recalc(UID, kid);
        var row = masteryMapper.selectOne(new LambdaQueryWrapper<com.knowflow.entity.KnowledgeMastery>()
                .eq(com.knowflow.entity.KnowledgeMastery::getUserId, UID)
                .eq(com.knowflow.entity.KnowledgeMastery::getKnowledgeId, kid));
        assertNotNull(row);
        assertEquals(67, row.getMasteryScore());
        assertEquals(MasteryConfig.STATUS_LEARNING, row.getLearningStatus());
        assertEquals(9, row.getConfidenceScore()); // 1/(1+10)
    }

    @Test
    @DisplayName("S2 CODING 信号：通过率 (1+1)/(1+2)=67 分")
    void codingPassSignal() {
        long kid = kg("TECHNIQUE");
        long qid = 93002L;
        map(kid, MasteryConfig.RES_CODE_QUESTION, qid, MasteryConfig.MAP_STATUS_ACCEPTED);
        CodeSubmitRecord r = new CodeSubmitRecord();
        r.setId(93002L); r.setUserId(UID); r.setQuestionId(qid); r.setPassed(1); r.setDeleted(0);
        r.setCode("class A {}"); r.setLanguage("java");
        r.setCreateTime(LocalDateTime.now());
        codeMapper.insert(r);

        masteryService.recalc(UID, kid);
        var row = oneRow(kid);
        assertNotNull(row);
        assertEquals(67, row.getMasteryScore());
    }

    @Test
    @DisplayName("S3 DOC 学习信号：阅读进度 100% → 强度 1.0 → 掌握度 100")
    void learningDocSignal() {
        long kid = kg("TERM");
        long docId = 93003L;
        map(kid, MasteryConfig.RES_DOC, docId, MasteryConfig.MAP_STATUS_ACCEPTED);
        DocReadProgress p = new DocReadProgress();
        p.setId(93003L); p.setUserId(UID); p.setDocId(docId); p.setProgress(BigDecimal.ONE);
        p.setLastReadTime(LocalDateTime.now());
        docProgressMapper.insert(p);

        masteryService.recalc(UID, kid);
        assertEquals(100, oneRow(kid).getMasteryScore());
    }

    @Test
    @DisplayName("S4 REVIEW 信号（闪卡代理）：reviewCount=5 → 强度 1.0 → 100")
    void reviewFlashcardSignal() {
        long kid = kg("PRINCIPLE");
        long fid = 93004L;
        map(kid, MasteryConfig.RES_FLASHCARD, fid, MasteryConfig.MAP_STATUS_ACCEPTED);
        LearningFlashcard f = new LearningFlashcard();
        f.setId(fid); f.setUserId(UID); f.setReviewCount(5);
        f.setFront("question"); f.setBack("answer");
        f.setNextReviewTime(LocalDateTime.now().plusDays(3));
        f.setLastReviewTime(LocalDateTime.now());
        flashcardMapper.insert(f);

        masteryService.recalc(UID, kid);
        assertEquals(100, oneRow(kid).getMasteryScore());
    }

    @Test
    @DisplayName("S5 MISTAKE 信号：已掌握 1/1 → 强度 1.0 → 100")
    void mistakeSignal() {
        long kid = kg("CONCEPT");
        long mid = 93005L;
        map(kid, MasteryConfig.RES_MISTAKE, mid, MasteryConfig.MAP_STATUS_ACCEPTED);
        LearningMistake m = new LearningMistake();
        m.setId(mid); m.setUserId(UID); m.setQuestion("what is X?"); m.setMastered(1);
        m.setLastReviewTime(LocalDateTime.now());
        mistakeMapper.insert(m);

        masteryService.recalc(UID, kid);
        assertEquals(100, oneRow(kid).getMasteryScore());
    }

    @Test
    @DisplayName("S6 RECALL 信号：回合均分 90 → 强度 0.9 → 90")
    void recallSignal() {
        long kid = kg("CONCEPT");
        long sid = 93006L;
        map(kid, MasteryConfig.RES_RECALL_SESSION, sid, MasteryConfig.MAP_STATUS_ACCEPTED);
        WbRecallSession s = new WbRecallSession();
        s.setId(sid); s.setUserId(UID); s.setStatus("COMPLETED");
        s.setSourceText("recall source text");
        s.setRound1Score(90); s.setRound2Score(80); s.setRound3Score(100);
        s.setCompletedTime(LocalDateTime.now());
        recallMapper.insert(s);

        masteryService.recalc(UID, kid);
        assertEquals(90, oneRow(kid).getMasteryScore());
    }

    // ============================================================
    // 有效信号归一化（缺失信号 ≠ 0）
    // ============================================================

    @Test
    @DisplayName("S7 有效信号归一化：单 QUIZ 全对（拉普拉斯 2/3≈67），不被其余 5 个缺失信号稀释（若稀释则≈16）")
    void activeSignalNormalization() {
        long kid = kg("CONCEPT");
        long qid = 93007L;
        map(kid, MasteryConfig.RES_QUIZ, qid, MasteryConfig.MAP_STATUS_ACCEPTED);
        QuizAnswerRecord r = new QuizAnswerRecord();
        r.setId(93007L); r.setUserId(UID); r.setQuestionId(qid); r.setIsCorrect(1);
        r.setCreateTime(LocalDateTime.now());
        quizMapper.insert(r);

        masteryService.recalc(UID, kid);
        // QUIZ 权重 0.25，单信号：mastery = 0.25*(2/3) / 0.25 *100 = 67（而非被 6 个权重稀释到 16）
        assertEquals(67, oneRow(kid).getMasteryScore());
    }

    @Test
    @DisplayName("S8 多信号加权：QUIZ 全对 + CODING 全对 + DOC 全读 → 加权 ≈ 72（拉普拉斯：单样本信号不归 1）")
    void multiSignalWeighted() {
        long kid = kg("TECHNIQUE");
        long qid = 93008L, cid = 93009L, did = 93010L;
        map(kid, MasteryConfig.RES_QUIZ, qid, MasteryConfig.MAP_STATUS_ACCEPTED);
        map(kid, MasteryConfig.RES_CODE_QUESTION, cid, MasteryConfig.MAP_STATUS_ACCEPTED);
        map(kid, MasteryConfig.RES_DOC, did, MasteryConfig.MAP_STATUS_ACCEPTED);
        QuizAnswerRecord qr = new QuizAnswerRecord();
        qr.setId(qid); qr.setUserId(UID); qr.setQuestionId(qid); qr.setIsCorrect(1);
        qr.setCreateTime(LocalDateTime.now()); quizMapper.insert(qr);
        CodeSubmitRecord cr = new CodeSubmitRecord();
        cr.setId(cid); cr.setUserId(UID); cr.setQuestionId(cid); cr.setPassed(1); cr.setDeleted(0);
        cr.setCode("class A {}"); cr.setLanguage("java");
        cr.setCreateTime(LocalDateTime.now()); codeMapper.insert(cr);
        DocReadProgress dp = new DocReadProgress();
        dp.setId(did); dp.setUserId(UID); dp.setDocId(did); dp.setProgress(BigDecimal.ONE);
        dp.setLastReadTime(LocalDateTime.now()); docProgressMapper.insert(dp);

        masteryService.recalc(UID, kid);
        // QUIZ/CONCEPT 单样本拉普拉斯 0.667，DOC=1.0；归一化 = (0.25*0.667+0.25*0.667+0.10*1.0)/0.60*100 = 72
        assertEquals(72, oneRow(kid).getMasteryScore());
    }

    // ============================================================
    // 状态机
    // ============================================================

    @Test
    @DisplayName("S9 WEAK：QUIZ 全错 → 拉普拉斯正确率 (0+1)/(0+1+2)=33 → 掌握度 33 < 60 → WEAK")
    void weakStatus() {
        long kid = kg("CONCEPT");
        long qid = 93011L;
        map(kid, MasteryConfig.RES_QUIZ, qid, MasteryConfig.MAP_STATUS_ACCEPTED);
        QuizAnswerRecord r = new QuizAnswerRecord();
        r.setId(qid); r.setUserId(UID); r.setQuestionId(qid); r.setIsCorrect(0);
        r.setCreateTime(LocalDateTime.now()); quizMapper.insert(r);

        masteryService.recalc(UID, kid);
        var row = oneRow(kid);
        assertEquals(33, row.getMasteryScore()); // 拉普拉斯 (0+1)/(0+1+2)=33
        assertEquals(MasteryConfig.STATUS_WEAK, row.getLearningStatus());
    }

    @Test
    @DisplayName("S10 MASTERED：10 次全对 → 掌握度 100 且置信度 50 且风险低 → MASTERED")
    void masteredStatus() {
        long kid = kg("CONCEPT");
        long qid = 93012L;
        map(kid, MasteryConfig.RES_QUIZ, qid, MasteryConfig.MAP_STATUS_ACCEPTED);
        for (int i = 0; i < 10; i++) {
            QuizAnswerRecord r = new QuizAnswerRecord();
            r.setId(930120L + i); r.setUserId(UID); r.setQuestionId(qid); r.setIsCorrect(1);
            r.setCreateTime(LocalDateTime.now()); quizMapper.insert(r);
        }
        masteryService.recalc(UID, kid);
        var row = oneRow(kid);
        assertEquals(92, row.getMasteryScore()); // 拉普拉斯 (10+1)/(10+2)=11/12≈92
        assertEquals(50, row.getConfidenceScore()); // 10/(10+10)
        assertEquals(MasteryConfig.STATUS_MASTERED, row.getLearningStatus());
    }

    @Test
    @DisplayName("S11 REVIEW_REQUIRED：复习卡远 overdue（最近复习 40 天前）→ 遗忘风险高")
    void reviewRequiredStatus() {
        long kid = kg("PRINCIPLE");
        long cid = 93013L;
        map(kid, MasteryConfig.RES_REVIEW_CARD, cid, MasteryConfig.MAP_STATUS_ACCEPTED);
        WbReviewCard c = new WbReviewCard();
        c.setId(cid); c.setUserId(UID); c.setRepetitions(5); c.setLapseCount(0);
        c.setIntervalDay(10); c.setReviewCount(5);
        c.setFront("question"); c.setBack("answer");
        c.setLastReviewTime(LocalDateTime.now().minusDays(40));
        c.setNextReviewTime(LocalDateTime.now().minusDays(10));
        reviewCardMapper.insert(c);

        masteryService.recalc(UID, kid);
        var row = oneRow(kid);
        assertTrue(row.getForgettingRisk() >= MasteryConfig.REVIEW_REQUIRED_RISK_THRESHOLD,
                "遗忘风险应 >= " + MasteryConfig.REVIEW_REQUIRED_RISK_THRESHOLD + "，实际 " + row.getForgettingRisk());
        assertEquals(MasteryConfig.STATUS_REVIEW_REQUIRED, row.getLearningStatus());
    }

    // ============================================================
    // 置信度公式 / 幂等 / 无信号删除
    // ============================================================

    @Test
    @DisplayName("S12 置信度公式 N/(N+K)：3 次样本 → 3/(3+10)=23%")
    void confidenceFormula() {
        long kid = kg("CONCEPT");
        long qid = 93014L;
        map(kid, MasteryConfig.RES_QUIZ, qid, MasteryConfig.MAP_STATUS_ACCEPTED);
        for (int i = 0; i < 3; i++) {
            QuizAnswerRecord r = new QuizAnswerRecord();
            r.setId(930140L + i); r.setUserId(UID); r.setQuestionId(qid); r.setIsCorrect(1);
            r.setCreateTime(LocalDateTime.now()); quizMapper.insert(r);
        }
        masteryService.recalc(UID, kid);
        assertEquals(23, oneRow(kid).getConfidenceScore());
    }

    @Test
    @DisplayName("S13 幂等：连续重算两次仍只有 1 行（整行重算，不累计）")
    void idempotentRecompute() {
        long kid = kg("CONCEPT");
        long qid = 93015L;
        map(kid, MasteryConfig.RES_QUIZ, qid, MasteryConfig.MAP_STATUS_ACCEPTED);
        QuizAnswerRecord r = new QuizAnswerRecord();
        r.setId(qid); r.setUserId(UID); r.setQuestionId(qid); r.setIsCorrect(1);
        r.setCreateTime(LocalDateTime.now()); quizMapper.insert(r);

        masteryService.recalc(UID, kid);
        masteryService.recalc(UID, kid);
        masteryService.recalc(UID, kid);
        long count = masteryMapper.selectCount(new LambdaQueryWrapper<com.knowflow.entity.KnowledgeMastery>()
                .eq(com.knowflow.entity.KnowledgeMastery::getUserId, UID)
                .eq(com.knowflow.entity.KnowledgeMastery::getKnowledgeId, kid));
        assertEquals(1, count);
    }

    @Test
    @DisplayName("S14 无信号删除行：有映射但无资源记录 → 不落库（hasSignals=false）")
    void noSignalsDeletesRow() {
        long kid = kg("CONCEPT");
        long qid = 93016L;
        map(kid, MasteryConfig.RES_QUIZ, qid, MasteryConfig.MAP_STATUS_ACCEPTED);
        // 故意不插入 quiz_answer_record
        masteryService.recalc(UID, kid);
        assertNull(oneRow(kid));
    }

    // ============================================================
    // 事件触发 / 冷启动 / 诊断
    // ============================================================

    @Test
    @DisplayName("S15 事件触发：LearningEvent(QUIZ) 经 processEvent 自动重算落库")
    void processEventTriggersRecalc() {
        long kid = kg("CONCEPT");
        long qid = 93017L;
        map(kid, MasteryConfig.RES_QUIZ, qid, MasteryConfig.MAP_STATUS_ACCEPTED);
        QuizAnswerRecord r = new QuizAnswerRecord();
        r.setId(qid); r.setUserId(UID); r.setQuestionId(qid); r.setIsCorrect(1);
        r.setCreateTime(LocalDateTime.now()); quizMapper.insert(r);

        LearningEvent ev = new LearningEvent();
        ev.setId(99001L); ev.setUserId(UID); ev.setEventType("QUIZ_SUBMIT");
        ev.setResourceType(MasteryConfig.RES_QUIZ); ev.setResourceId(qid);
        masteryService.processEvent(ev);
        assertNotNull(oneRow(kid));
    }

    @Test
    @DisplayName("S16 冷启动：事件对应资源无已接受映射 → 引擎优雅跳过，不抛异常、不落库")
    void coldStartNoMapping() {
        // 无 kg_entity、无 mapping
        LearningEvent ev = new LearningEvent();
        ev.setId(99002L); ev.setUserId(UID); ev.setEventType("QUIZ_SUBMIT");
        ev.setResourceType(MasteryConfig.RES_QUIZ); ev.setResourceId(93018L);
        assertDoesNotThrow(() -> masteryService.processEvent(ev));
        assertEquals(0L, masteryMapper.selectCount(new LambdaQueryWrapper<com.knowflow.entity.KnowledgeMastery>()
                .eq(com.knowflow.entity.KnowledgeMastery::getUserId, UID)));
    }

    @Test
    @DisplayName("S17 诊断可观察性：统计 ACCEPTED/PENDING 映射与未映射资源")
    void diagnostics() {
        long kid = kg("CONCEPT");
        map(kid, MasteryConfig.RES_QUIZ, 93019L, MasteryConfig.MAP_STATUS_ACCEPTED);
        map(kid, MasteryConfig.RES_DOC, 93020L, MasteryConfig.MAP_STATUS_PENDING);
        MasteryDiagnosticsVO d = masteryService.diagnostics(UID);
        assertEquals(2, d.getTotalMappings());
        assertEquals(1, d.getAcceptedMappings());
        assertEquals(1, d.getPendingMappings());
        assertNotNull(d.getNote());
    }

    @Test
    @DisplayName("S18 详情视图：getDetail 返回解释、信号明细与原始计数器")
    void detailView() {
        long kid = kg("CONCEPT");
        long qid = 93021L;
        map(kid, MasteryConfig.RES_QUIZ, qid, MasteryConfig.MAP_STATUS_ACCEPTED);
        QuizAnswerRecord r = new QuizAnswerRecord();
        r.setId(qid); r.setUserId(UID); r.setQuestionId(qid); r.setIsCorrect(1);
        r.setCreateTime(LocalDateTime.now()); quizMapper.insert(r);
        masteryService.recalc(UID, kid);

        KnowledgeMasteryDetailVO vo = masteryService.getDetail(UID, kid);
        assertNotNull(vo);
        assertEquals(kid, vo.getKnowledgeId());
        assertNotNull(vo.getExplanation());
        assertFalse(vo.getSignals().isEmpty());
        assertTrue(vo.getSignals().stream().anyMatch(s -> "QUIZ".equals(s.getType())));
        assertEquals(1, vo.getAttemptCount());
    }

    // ============================================================
    // 列表查询
    // ============================================================

    @Test
    @DisplayName("S19 列表：listWeak / listReviewRequired 按状态过滤")
    void listByStatus() {
        // WEAK
        long weakKid = kg("CONCEPT");
        long wq = 93022L;
        map(weakKid, MasteryConfig.RES_QUIZ, wq, MasteryConfig.MAP_STATUS_ACCEPTED);
        QuizAnswerRecord wr = new QuizAnswerRecord();
        wr.setId(wq); wr.setUserId(UID); wr.setQuestionId(wq); wr.setIsCorrect(0);
        wr.setCreateTime(LocalDateTime.now()); quizMapper.insert(wr);
        masteryService.recalc(UID, weakKid);

        // REVIEW_REQUIRED
        long revKid = kg("PRINCIPLE");
        long rc = 93023L;
        map(revKid, MasteryConfig.RES_REVIEW_CARD, rc, MasteryConfig.MAP_STATUS_ACCEPTED);
        WbReviewCard card = new WbReviewCard();
        card.setId(rc); card.setUserId(UID); card.setRepetitions(5); card.setLapseCount(0);
        card.setIntervalDay(10); card.setReviewCount(5);
        card.setFront("question"); card.setBack("answer");
        card.setLastReviewTime(LocalDateTime.now().minusDays(40));
        card.setNextReviewTime(LocalDateTime.now().minusDays(10));
        reviewCardMapper.insert(card);
        masteryService.recalc(UID, revKid);

        List<?> weak = masteryService.listWeak(UID);
        List<?> review = masteryService.listReviewRequired(UID);
        assertEquals(1, weak.size());
        assertEquals(1, review.size());
    }

    // ---- 工具 ----
    private com.knowflow.entity.KnowledgeMastery oneRow(long kid) {
        return masteryMapper.selectOne(new LambdaQueryWrapper<com.knowflow.entity.KnowledgeMastery>()
                .eq(com.knowflow.entity.KnowledgeMastery::getUserId, UID)
                .eq(com.knowflow.entity.KnowledgeMastery::getKnowledgeId, kid));
    }
}
