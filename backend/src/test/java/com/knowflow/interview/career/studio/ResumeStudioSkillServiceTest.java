package com.knowflow.interview.career.studio;

import com.knowflow.exception.BusinessException;
import com.knowflow.interview.career.CareerMatcher;
import com.knowflow.interview.career.CareerRecordService;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 专项规则只能引用已确认的真实简历，不把缺失信息判定为不会。 */
class ResumeStudioSkillServiceTest {
    private final CareerRecordService records = mock(CareerRecordService.class);
    private final ResumeStudioSkillService service = new ResumeStudioSkillService(records);

    @Test
    void exposesThreeVersionedRuleOnlyContracts() {
        assertEquals(3, service.skills(1L).size());
        service.skills(1L).forEach(skill -> {
            assertEquals("1.0.0", skill.version());
            assertFalse(skill.modelRequired());
            assertFalse(skill.highRiskProhibitions().isEmpty());
            assertFalse(skill.outputSchema().isEmpty());
        });
        verifyNoInteractions(records);
    }

    @Test
    void absentEvidenceOnlyAsksAndDoesNotWriteAnything() {
        when(records.detail(1L, 8L, 2)).thenReturn(resume("负责订单服务", List.of()));
        var result = service.check(1L, 8L, 2, "java-ai");
        assertEquals(2, result.resumeVersion());
        assertEquals("RULE", result.modelSource());
        assertTrue(result.findings().stream().allMatch(f -> "NEEDS_CONFIRMATION".equals(f.status())));
        assertTrue(result.findings().stream().allMatch(f -> f.evidence().isEmpty()));
        verify(records).detail(1L, 8L, 2);
        verifyNoMoreInteractions(records);
    }

    @Test
    void rejectsClaimWithoutExactSourceQuoteAndDoesNotAssertCompetence() {
        var claims = List.of(new CareerMatcher.Claim("Java RAG", "不存在的引用"));
        when(records.detail(1L, 8L, null)).thenReturn(resume("负责订单服务", claims));
        assertTrue(service.check(1L, 8L, null, "java-ai").findings().stream()
                .allMatch(f -> f.evidence().isEmpty()));
    }

    @Test
    void returnsOnlySourceGroundedEvidence() {
        var claims = List.of(new CareerMatcher.Claim("Java Spring Boot", "使用 Java Spring Boot 开发订单服务"));
        when(records.detail(1L, 8L, 2)).thenReturn(resume("使用 Java Spring Boot 开发订单服务", claims));
        var result = service.check(1L, 8L, 2, "senior-backend");
        assertEquals("EVIDENCE_FOUND", result.findings().get(0).status());
        assertEquals("使用 Java Spring Boot 开发订单服务", result.findings().get(0).evidence().get(0).originalQuote());
    }

    @Test
    void rejectsAnonymousInvalidSkillAndUnconfirmedOrWrongKind() {
        assertEquals(401, assertThrows(BusinessException.class, () -> service.skills(null)).getCode());
        assertEquals(400, assertThrows(BusinessException.class, () -> service.check(1L, 8L, null, "unknown")).getCode());
        when(records.detail(1L, 8L, null)).thenReturn(Map.of("kind", "RESUME", "confirmed", false));
        assertEquals(409, assertThrows(BusinessException.class, () -> service.check(1L, 8L, null, "java-ai")).getCode());
        when(records.detail(1L, 8L, null)).thenReturn(Map.of("kind", "JOB", "confirmed", true));
        assertEquals(400, assertThrows(BusinessException.class, () -> service.check(1L, 8L, null, "java-ai")).getCode());
    }

    @Test
    void preservesOwnershipRejectionFromExistingRecordService() {
        when(records.detail(2L, 8L, 2)).thenThrow(new BusinessException(404, "记录不存在"));
        assertEquals(404, assertThrows(BusinessException.class,
                () -> service.check(2L, 8L, 2, "java-ai")).getCode());
        verify(records).detail(2L, 8L, 2);
        verifyNoMoreInteractions(records);
    }

    private Map<String, Object> resume(String source, List<CareerMatcher.Claim> claims) {
        return Map.of("id", 8L, "kind", "RESUME", "version", 2, "confirmed", true,
                "source", source, "sections", Map.of("projects", claims));
    }
}
