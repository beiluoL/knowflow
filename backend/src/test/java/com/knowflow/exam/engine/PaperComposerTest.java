package com.knowflow.exam.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.QuizQuestion;
import com.knowflow.exam.dto.ExamRequests;
import com.knowflow.mapper.DocCategoryMapper;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.mapper.LearningMistakeMapper;
import com.knowflow.mapper.QuizAnswerRecordMapper;
import com.knowflow.mapper.QuizQuestionMapper;
import com.knowflow.security.DocumentAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 确定性组卷引擎单元测试：种子复现、题量不足拒绝、语义去重、分值守恒、错题策略不伪造。
 */
class PaperComposerTest {

    private QuizQuestionMapper questionMapper;
    private PaperComposer composer;
    private QuizAnswerRecordMapper recordMapper;

    private static QuizQuestion q(long id, String type, String title, String content,
                                 String tags, int difficulty, String answer) {
        QuizQuestion q = new QuizQuestion();
        q.setId(id);
        q.setTitle(title);
        q.setContent(content);
        q.setQuestionType(type);
        q.setTags(tags);
        q.setDifficulty(difficulty);
        q.setStatus(1);
        q.setAnswer(answer);
        q.setOptions(type.equals(QuizQuestion.TYPE_FILL_BLANK) || type.equals(QuizQuestion.TYPE_SHORT_ANSWER)
                || type.equals(QuizQuestion.TYPE_TRUE_FALSE) ? null : "[\"A\",\"B\",\"C\",\"D\"]");
        return q;
    }

    private static QuizQuestion q(long id, String type, String title, String tags, int difficulty, String answer) {
        return q(id, type, title, "题干内容-" + id, tags, difficulty, answer);
    }

    @BeforeEach
    void setUp() {
        questionMapper = mock(QuizQuestionMapper.class);
        LearningMistakeMapper mistakeMapper = mock(LearningMistakeMapper.class);
        recordMapper = mock(QuizAnswerRecordMapper.class);
        DocumentAccessPolicy access = mock(DocumentAccessPolicy.class);
        DocCategoryMapper categoryMapper = mock(DocCategoryMapper.class);
        DocDocumentMapper docMapper = mock(DocDocumentMapper.class);

        when(access.admin(anyLong())).thenReturn(true);
        when(access.actor()).thenReturn(1L);
        when(recordMapper.selectList(any())).thenReturn(List.of());
        when(mistakeMapper.selectList(any())).thenReturn(List.of());

        composer = new PaperComposer(questionMapper, mistakeMapper, recordMapper, access,
                categoryMapper, docMapper,
                mock(com.knowflow.coding.mapper.CodeProblemMapper.class),
                mock(com.knowflow.coding.mapper.CodeTestCaseMapper.class),
                new ObjectMapper(),
                new SmartPracticeProperties(), mock(QuestionHistoryLookup.class));
    }

    private void givenBank(List<QuizQuestion> bank) {
        when(questionMapper.selectList(any())).thenReturn(bank);
    }

    private ExamRequests.ComposeRequest req(String strategy, long seed, int singles, int totalScore) {
        ExamRequests.ComposeRequest r = new ExamRequests.ComposeRequest();
        r.setStrategy(strategy);
        r.setSeed(seed);
        Map<String, Integer> dist = new HashMap<>();
        if (singles > 0) {
            dist.put(QuizQuestion.TYPE_SINGLE_CHOICE, singles);
        }
        r.setTypeDistribution(dist);
        r.setTotalScore(totalScore);
        r.setDurationMinutes(20);
        r.setShuffleQuestion(1);
        return r;
    }

    private List<QuizQuestion> singleBank(int n) {
        List<QuizQuestion> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            list.add(q(i, QuizQuestion.TYPE_SINGLE_CHOICE, "单选题 " + i + " 号", "HASHMAP", (i % 3) + 1, "0"));
        }
        return list;
    }

    @Test
    void 相同种子与题库版本生成完全相同的题目序列() {
        givenBank(singleBank(8));
        var a = composer.compose(1L, req(PaperComposer.STRATEGY_RANDOM, 42L, 4, 100));
        var b = composer.compose(1L, req(PaperComposer.STRATEGY_RANDOM, 42L, 4, 100));
        assertThat(a.sufficient).isTrue();
        assertThat(ids(a)).isEqualTo(ids(b));
    }

    @Test
    void 不同种子通常生成不同试卷() {
        givenBank(singleBank(8));
        var a = composer.compose(1L, req(PaperComposer.STRATEGY_RANDOM, 1L, 4, 100));
        var b = composer.compose(1L, req(PaperComposer.STRATEGY_RANDOM, 999983L, 4, 100));
        assertThat(ids(a)).isNotEqualTo(ids(b));
    }

    @Test
    void 题量不足时拒绝组卷并给出缺口而不是重复凑数() {
        givenBank(singleBank(5));
        var r = composer.compose(1L, req(PaperComposer.STRATEGY_RANDOM, 7L, 8, 100));
        assertThat(r.sufficient).isFalse();
        assertThat(r.message).contains("不足");
        assertThat(r.selected).hasSize(5);
    }

    @Test
    void 同一语义母题的多个变体不会重复进入一张卷() {
        List<QuizQuestion> bank = new ArrayList<>();
        // 3 道语义同身份（标题与题干归一化后一致）+ 2 道不同
        String variantStem = "并发环境下 put 是否会产生问题，请判断。";
        bank.add(q(1, QuizQuestion.TYPE_SINGLE_CHOICE, "HashMap 线程安全?", variantStem, "HASHMAP", 1, "1"));
        bank.add(q(2, QuizQuestion.TYPE_SINGLE_CHOICE, "hashmap线程安全？", variantStem, "HASHMAP", 1, "1"));
        bank.add(q(3, QuizQuestion.TYPE_SINGLE_CHOICE, "HashMap 线程安全 ?", variantStem, "HASHMAP", 2, "1"));
        bank.add(q(4, QuizQuestion.TYPE_SINGLE_CHOICE, "树化阈值是多少", "链表达到多少会树化？", "HASHMAP", 2, "1"));
        bank.add(q(5, QuizQuestion.TYPE_SINGLE_CHOICE, "负载因子默认值", "默认负载因子是多少？", "HASHMAP", 1, "0"));
        givenBank(bank);

        var r = composer.compose(1L, req(PaperComposer.STRATEGY_RANDOM, 1L, 5, 100));
        assertThat(r.sufficient).isFalse();
        assertThat(r.selected).hasSize(3);
        // 语义变体中最多入选一道
        long variantPicked = r.selected.stream()
                .filter(s -> SemanticIdentity.normalize(s.question.getTitle()).equals("hashmap线程安全"))
                .count();
        assertThat(variantPicked).isEqualTo(1);
    }

    @Test
    void 分值总和严格等于试卷总分且均为正分() {
        givenBank(singleBank(6));
        var r = composer.compose(1L, req(PaperComposer.STRATEGY_GRADIENT, 5L, 6, 100));
        assertThat(r.sufficient).isTrue();
        int sum = r.selected.stream().mapToInt(s -> s.score).sum();
        assertThat(sum).isEqualTo(100);
        assertThat(r.selected).allSatisfy(s -> assertThat(s.score).isPositive());
    }

    @Test
    void 错题强化在没有真实错题时直接拒绝而非伪造() {
        givenBank(singleBank(6));
        var r = composer.compose(1L, req(PaperComposer.STRATEGY_MISTAKE, 5L, 3, 100));
        assertThat(r.sufficient).isFalse();
        assertThat(r.message).contains("错题");
        assertThat(r.selected).isEmpty();
    }

    @Test
    void 可用性预览逐题型返回题库数量() {
        List<QuizQuestion> bank = singleBank(4);
        bank.add(q(10, QuizQuestion.TYPE_TRUE_FALSE, "判断题 X", "JVM", 1, "true"));
        givenBank(bank);
        Map<String, Integer> want = Map.of(
                QuizQuestion.TYPE_SINGLE_CHOICE, 4,
                QuizQuestion.TYPE_TRUE_FALSE, 2);
        var preview = composer.preview(1L, List.of(), want, null, PaperComposer.STRATEGY_RANDOM);
        assertThat(preview.available.get(QuizQuestion.TYPE_SINGLE_CHOICE)).isEqualTo(4);
        assertThat(preview.available.get(QuizQuestion.TYPE_TRUE_FALSE)).isEqualTo(1);
        assertThat(preview.sufficient).isFalse();
        assertThat(preview.bankVersion).isNotBlank();
    }

    private List<Long> ids(PaperComposer.SelectionResult r) {
        return r.selected.stream().map(s -> s.question.getId()).toList();
    }
    @Test
    void explicitCanonicalVersionsCannotRepeatAndPreviewCountsUniqueQuestions() {
        var a=q(901,QuizQuestion.TYPE_SINGLE_CHOICE,"旧版本标题","HASHMAP",1,"0");
        var b=q(902,QuizQuestion.TYPE_SINGLE_CHOICE,"完全不同的新版本标题","HASHMAP",1,"0");
        var c=q(903,QuizQuestion.TYPE_SINGLE_CHOICE,"另一题","HASHMAP",1,"0");
        a.setCanonicalKey("hashmap-contract"); b.setCanonicalKey("hashmap-contract"); c.setCanonicalKey("another");
        givenBank(List.of(a,b,c));
        for(String strategy:List.of(PaperComposer.STRATEGY_RANDOM,PaperComposer.STRATEGY_REVIEW)) {
            var selected=composer.compose(1L,req(strategy,42L,3,100));
            assertThat(selected.selected).hasSize(2); assertThat(selected.sufficient).isFalse();
        }
        var preview=composer.preview(1L,List.of(),Map.of("SINGLE_CHOICE",3),List.of(),"RANDOM");
        assertThat(preview.available.get("SINGLE_CHOICE")).isEqualTo(2); assertThat(preview.sufficient).isFalse();
    }

    @Test
    void explicitDifferentIdentitiesAreNotMergedOnlyBecauseTitlesMatch() {
        var a=q(904,QuizQuestion.TYPE_SINGLE_CHOICE,"同名题","完全相同题干","HASHMAP",1,"0");
        var b=q(905,QuizQuestion.TYPE_SINGLE_CHOICE,"同名题","完全相同题干","HASHMAP",1,"0");
        a.setCanonicalKey("different-boundary-1"); b.setCanonicalKey("different-boundary-2");
        givenBank(List.of(a,b));
        assertThat(composer.compose(1L,req("RANDOM",42L,2,100)).selected).hasSize(2);
    }

    @Test
    void unansweredOnlyExcludesEarlierCanonicalVersionForCurrentUser() {
        var a=q(920,QuizQuestion.TYPE_SINGLE_CHOICE,"旧版","HASHMAP",1,"0");
        var b=q(921,QuizQuestion.TYPE_SINGLE_CHOICE,"新版","HASHMAP",1,"0");
        var c=q(922,QuizQuestion.TYPE_SINGLE_CHOICE,"未做","HASHMAP",1,"0");
        a.setCanonicalKey("seen"); b.setCanonicalKey("seen"); c.setCanonicalKey("unseen");
        var record=new com.knowflow.entity.QuizAnswerRecord(); record.setQuestionId(a.getId()); record.setUserId(1L);
        when(recordMapper.selectList(any())).thenReturn(List.of(record));
        givenBank(List.of(a,b,c)); var request=req("RANDOM",42L,2,100); request.setIncludeAnswered(false);
        var result=composer.compose(1L,request);
        assertThat(result.selected).hasSize(1); assertThat(result.selected.get(0).question.getId()).isEqualTo(922L);
        assertThat(result.sufficient).isFalse();
    }
    @Test
    void bankVersionChangesWhenExplicitIdentityChangesWithoutTimestamp() {
        var a=q(930,QuizQuestion.TYPE_SINGLE_CHOICE,"题","HASHMAP",1,"0"); a.setCanonicalKey("v1");
        String before=composer.bankFingerprint(List.of(a)); a.setCanonicalKey("v2");
        assertThat(composer.bankFingerprint(List.of(a))).isNotEqualTo(before);
    }

}
