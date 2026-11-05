package com.knowflow.interview.bridge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.knowflow.exception.BusinessException;
import com.knowflow.knowledge.contract.ProjectDocumentQuery;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class InterviewProjectContextServiceTest {
    @Test
    void deduplicatesInRequestOrderAndKeepsTheExistingSnapshotBoundsAndLabels() {
        var documents = mock(ProjectDocumentQuery.class);
        when(documents.requireReadable(4L, 7L))
                .thenReturn(new ProjectDocumentQuery.Content("t".repeat(250), "x".repeat(3000)));
        when(documents.requireReadable(5L, 7L))
                .thenReturn(new ProjectDocumentQuery.Content(null, null));
        var result = new InterviewProjectContextService(documents)
                .resolve(7L, List.of(4L, 5L, 4L), "  user claim  ");
        assertEquals("user claim", result.userDescription());
        assertEquals("USER_REPORTED", result.userDescriptionSource());
        assertEquals(List.of(4L, 5L), result.evidence().stream()
                .map(InterviewProjectContextService.Evidence::documentId).toList());
        var first = result.evidence().get(0);
        assertEquals("document:4", first.sourceId());
        assertEquals(200, first.title().length());
        assertEquals(2400, first.quote().length());
        assertEquals("VERIFIED", first.verification());
        assertEquals("DOCUMENT_CONTENT_ONLY", first.claimScope());
        assertEquals("", result.evidence().get(1).title());
        assertEquals("", result.evidence().get(1).quote());
        var sequence = inOrder(documents);
        sequence.verify(documents).requireReadable(4L, 7L);
        sequence.verify(documents).requireReadable(5L, 7L);
        verifyNoMoreInteractions(documents);
    }

    @Test
    void rejectsAnonymousAndInvalidInputBeforeReadingSourceMaterial() {
        var documents = mock(ProjectDocumentQuery.class);
        var service = new InterviewProjectContextService(documents);
        assertEquals(401, assertThrows(BusinessException.class,
                () -> service.resolve(null, List.of(), "")).getCode());
        for (List<Long> ids : List.of(List.of(0L), Arrays.asList((Long) null),
                List.of(1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L))) {
            assertEquals(400, assertThrows(BusinessException.class,
                    () -> service.resolve(7L, ids, "")).getCode());
        }
        assertEquals(400, assertThrows(BusinessException.class,
                () -> service.resolve(7L, List.of(), "x".repeat(4001))).getCode());
        assertTrue(service.resolve(7L, null, null).evidence().isEmpty());
        verifyNoInteractions(documents);
    }

    @Test
    void sourceRevocationFailsTheSnapshotWithTheOriginalPolicyException() {
        var documents = mock(ProjectDocumentQuery.class);
        var rejection = new BusinessException(403, "无权阅读该文档");
        when(documents.requireReadable(4L, 8L)).thenThrow(rejection);
        assertSame(rejection, assertThrows(BusinessException.class,
                () -> new InterviewProjectContextService(documents).resolve(8L, List.of(4L), "")));
    }
}
