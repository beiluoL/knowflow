package com.knowflow.interview.career.tailoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.interview.career.CareerMatcher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** AI 事实护栏：编造数字或原文没有的技能必须被丢弃。 */
class TailoringFactGuardTest {

    private final CareerTailoringService service = new CareerTailoringService(
            null, new ObjectMapper(), null, null);

    private final List<CareerMatcher.Claim> required =
            List.of(new CareerMatcher.Claim("Java", "必须 Java"), new CareerMatcher.Claim("Redis", "必须 Redis"));

    @Test
    void keepsFactPreservingRewrite() {
        String raw = """
                [{"originalQuote":"负责订单服务重构","proposedText":"负责订单服务重构与接口优化",
                  "reason":"表达更清晰","requirement":"Java"}]
                """;
        var changes = service.validateAi(raw, "负责订单服务重构", required, List.of());
        assertEquals(1, changes.size());
        assertEquals("REWRITE", changes.get(0).get("type"));
    }

    @Test
    void rejectsInventedMetricsAndUnknownSkills() {
        String raw = """
                [
                 {"originalQuote":"负责订单服务重构","proposedText":"负责订单服务重构，性能提升 40%",
                  "reason":"补充成果","requirement":"Java"},
                 {"originalQuote":"负责订单服务重构","proposedText":"使用 Redis 重写订单服务",
                  "reason":"贴合岗位","requirement":"Redis"},
                 {"originalQuote":"不存在的经历","proposedText":"不存在的经历","reason":"x","requirement":"Java"}
                ]
                """;
        var changes = service.validateAi(raw, "负责订单服务重构", required, List.of());
        assertTrue(changes.isEmpty(), "编造数字、编造技能、无原文引用的建议都必须丢弃");
    }

    @Test
    void digitGuardAllowsSameNumbersButNotNewOnes() {
        assertTrue(service.digitsPreserved("QPS 从 100 提升到 200", "QPS 由 100 提升至 200"));
        assertFalse(service.digitsPreserved("负责 2 个服务", "负责 3 个服务"));
        assertFalse(service.digitsPreserved("负责服务重构", "负责服务重构，性能提升 40%"));
    }

    @Test
    void rejectsUnparseableModelOutput() {
        assertTrue(service.validateAi("not-json", "原文", required, List.of()).isEmpty());
        assertTrue(service.validateAi("{}", "原文", required, List.of()).isEmpty());
        assertTrue(service.validateAi(null, "原文", required, List.of()).isEmpty());
    }
}
