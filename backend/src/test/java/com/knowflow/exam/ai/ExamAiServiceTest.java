package com.knowflow.exam.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.exam.ai.ExamAiService.AiGrade;
import com.knowflow.exam.ai.ExamAiService.GradeInput;
import com.knowflow.service.AiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AI 评卷容错测试：未配置 / 非法 JSON / 缺题 / 调用失败都必须保持可识别失败（上层据此维持 PENDING，绝不记 0 分）；
 * 合法返回时总分由服务端按固定权重重算。
 */
class ExamAiServiceTest {

    private AiService ai;
    private ExamAiService service;
    private final List<GradeInput> inputs = List.of(
            new GradeInput(1L, "解释 HashMap 树化", "请说明树化条件", "SHORT_ANSWER", "8 且容量 64", ""),
            new GradeInput(2L, "G1 调优", "如何排查频繁 Full GC", "SHORT_ANSWER", "看日志/jstat", "")
    );

    @BeforeEach
    void setUp() {
        ai = mock(AiService.class);
        service = new ExamAiService(ai, new ObjectMapper());
    }

    @Test
    void 模型未配置时所有题保持失败标记而不抛异常() {
        when(ai.isConfigured()).thenReturn(false);
        List<AiGrade> r = service.gradeSubjective(inputs, 1L, null);
        assertThat(r).hasSize(2);
        assertThat(r).allMatch(g -> !g.valid() && "AI_NOT_CONFIGURED".equals(g.rawError()));
    }

    @Test
    void 非法JSON时全部标记INVALID_JSON() {
        when(ai.isConfigured()).thenReturn(true);
        when(ai.complete(any(), any(), any(), anyLong())).thenReturn("我认为这道题答得还行，但不是JSON");
        List<AiGrade> r = service.gradeSubjective(inputs, 1L, null);
        assertThat(r).hasSize(2);
        assertThat(r).allMatch(g -> "INVALID_JSON".equals(g.rawError()));
    }

    @Test
    void 合法五维评分由服务端按权重重算总分并容忍markdown围栏() {
        when(ai.isConfigured()).thenReturn(true);
        // 80*.35 + 60*.25 + 40*.20 + 100*.10 + 20*.10 = 28+15+8+10+2 = 63
        String fenced = """
                ```json
                {"grades":[{"paperQuestionId":1,"total":70,"confidence":88,
                "dimensions":{"technicalAccuracy":80,"principle":60,"completeness":40,"engineering":100,"expression":20},
                "hitPoints":["提到树化阈值"],"missingPoints":["未提容量64"],"errors":[],
                "suggestions":["补充容量条件"],"evidence":["链表长度达到8"]}]}
                ```
                """;
        when(ai.complete(any(), any(), any(), anyLong())).thenReturn(fenced);
        List<AiGrade> r = service.gradeSubjective(inputs, 1L, null);
        assertThat(r).hasSize(2);
        AiGrade g1 = r.stream().filter(x -> x.paperQuestionId() == 1L).findFirst().orElseThrow();
        AiGrade g2 = r.stream().filter(x -> x.paperQuestionId() == 2L).findFirst().orElseThrow();
        assertThat(g1.valid()).isTrue();
        assertThat(g1.total100()).isEqualTo(63);
        assertThat(g1.modelTotal()).isEqualTo(70);
        assertThat(g1.confidence()).isEqualTo(88);
        assertThat(g1.hitPoints()).containsExactly("提到树化阈值");
        // 第 2 题模型漏评 → 保持失败标记，不能伪造分数
        assertThat(g2.valid()).isFalse();
        assertThat(g2.rawError()).isEqualTo("MISSING_IN_RESPONSE");
    }

    @Test
    void 模型调用抛异常时全部标记AI_CALL_FAILED() {
        when(ai.isConfigured()).thenReturn(true);
        when(ai.complete(any(), any(), any(), anyLong()))
                .thenThrow(new RuntimeException("timeout 60s"));
        List<AiGrade> r = service.gradeSubjective(inputs, 1L, null);
        assertThat(r).hasSize(2);
        assertThat(r).allMatch(g -> g.rawError() != null && g.rawError().startsWith("AI_CALL_FAILED"));
    }

    @Test
    void 出卷逐题Schema校验_非法题被标记但不影响其他题() {
        when(ai.isConfigured()).thenReturn(true);
        String raw = """
                {"questions":[
                  {"title":"合法单选","content":"下列说法正确的是？","questionType":"SINGLE_CHOICE",
                   "options":["a","b","c","d"],"answer":"1","explanation":"b 正确","difficulty":2,"topic":"HASHMAP"},
                  {"title":"缺选项单选","content":"题","questionType":"SINGLE_CHOICE",
                   "options":["a","b"],"answer":"0","explanation":"x","difficulty":2,"topic":"HASHMAP"},
                  {"title":"判断题","content":"判断","questionType":"TRUE_FALSE",
                   "answer":"maybe","explanation":"必须布尔","difficulty":1,"topic":"JVM"}
                ]}
                """;
        when(ai.complete(any(), any(), any(), anyLong())).thenReturn(raw);
        String[] err = new String[1];
        var qs = service.generateQuestions("HASHMAP", 3, null, null, 1L, null, err);
        assertThat(err[0]).isNull();
        assertThat(qs).hasSize(3);
        assertThat(qs.get(0).valid()).isTrue();
        assertThat(qs.get(1).invalidReason()).contains("4 个选项");
        assertThat(qs.get(2).invalidReason()).contains("true/false");
    }
}
