package com.knowflow.coding.service;

import com.knowflow.common.SecurityUtils;
import com.knowflow.coding.dto.CodeSubmitDTO;
import com.knowflow.coding.entity.CodeQuestion;
import com.knowflow.coding.entity.CodeSubmitRecord;
import com.knowflow.coding.mapper.CodeQuestionMapper;
import com.knowflow.coding.mapper.CodeSubmitRecordMapper;
import com.knowflow.learning.contract.LearningEventRecorder;
import com.knowflow.learning.contract.LearningEventType;
import com.knowflow.security.DocumentAccessPolicy;
import com.knowflow.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证编程题服务的可见性、私有台账及提交事件行为。
 */
class CodeQuestionServiceTest {
    private final CodeQuestionMapper questions = mock(CodeQuestionMapper.class);
    private final CodeSubmitRecordMapper records = mock(CodeSubmitRecordMapper.class);
    private final LearningEventRecorder events = mock(LearningEventRecorder.class);
    private final DocumentAccessPolicy access = mock(DocumentAccessPolicy.class);
    private final CodeQuestionService service = new CodeQuestionService(questions, records, events, access);

    private CodeQuestion published() {
        CodeQuestion question = new CodeQuestion();
        question.setId(8L);
        question.setStatus(1);
        when(questions.selectById(8L)).thenReturn(question);
        return question;
    }

    @Test void missingAndUnpublishedRetainLegacy404() {
        assertEquals(404, service.detail(8L).getCode());
        assertEquals(404, service.submit(8L, new CodeSubmitDTO()).getCode());
        published().setStatus(0);
        assertEquals(404, service.detail(8L).getCode());
        assertEquals(404, service.submit(8L, new CodeSubmitDTO()).getCode());
        verifyNoInteractions(access, records, events);
    }

    @Test void ownerAllowedButAnyForeignLedgerDenies() {
        published();
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            when(questions.selectPrivateResourceKeys(8L)).thenReturn(List.of("user/2/code"));
            assertEquals(200, service.detail(8L).getCode());
            when(questions.selectPrivateResourceKeys(8L)).thenReturn(List.of("user/2/code", "user/3/code"));
            assertThrows(BusinessException.class, () -> service.detail(8L));
            assertThrows(BusinessException.class, () -> service.submit(8L, new CodeSubmitDTO()));
            assertThrows(BusinessException.class, () -> service.submissions(8L));
            verifyNoInteractions(records, events);
        }
    }

    @Test void adminBypassesLedger() {
        published();
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            when(access.admin(2L)).thenReturn(true);
            assertEquals(200, service.detail(8L).getCode());
            verify(questions, never()).selectPrivateResourceKeys(anyLong());
        }
    }

    @Test void submissionWritesCountersAndOneEventAndRetainsCaughtFailure() {
        published();
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            when(questions.selectPrivateResourceKeys(8L)).thenReturn(List.of());
            CodeSubmitDTO payload = new CodeSubmitDTO();
            payload.setCode("return 1");
            payload.setTotal(1);
            payload.setPassCount(1);
            var result = service.submit(8L, payload);
            assertTrue(result.getData().getPassed());
            assertEquals(1, result.getData().getSubmitCount());
            verify(events).record(eq(2L), eq(LearningEventType.CODE_SUBMITTED), eq("CODE_QUESTION"), eq(8L), anyMap());
            doThrow(new IllegalStateException("write failed")).when(records).insert(any(CodeSubmitRecord.class));
            assertEquals(200, service.submit(8L, payload).getCode());
            verifyNoMoreInteractions(events);
        }
    }
}
