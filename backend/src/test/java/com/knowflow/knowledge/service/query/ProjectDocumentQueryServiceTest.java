package com.knowflow.knowledge.service.query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.knowflow.exception.BusinessException;
import com.knowflow.knowledge.entity.DocDocument;
import com.knowflow.knowledge.mapper.DocDocumentMapper;
import com.knowflow.security.DocumentAccessPolicy;
import org.junit.jupiter.api.Test;

/**
 * 验证项目文档查询在授权后保留空值并传播原始异常。
 */
class ProjectDocumentQueryServiceTest {
    @Test
    void returnsUnmodifiedNullableFieldsOnlyAfterReadAuthorization() {
        var documents = mock(DocDocumentMapper.class);
        var access = mock(DocumentAccessPolicy.class);
        var document = new DocDocument();
        document.setContent("x".repeat(3000));
        when(documents.selectById(4L)).thenReturn(document);
        var content = new ProjectDocumentQueryService(documents, access).requireReadable(4L, 7L);
        assertNull(content.title());
        assertEquals(document.getContent(), content.content());
        var sequence = inOrder(documents, access);
        sequence.verify(documents).selectById(4L);
        sequence.verify(access).requireRead(document, 7L);
        verifyNoMoreInteractions(documents, access);
    }

    @Test
    void propagatesTheOriginalMissingAndForbiddenExceptions() {
        for (int code : new int[] {404, 403}) {
            var documents = mock(DocDocumentMapper.class);
            var access = mock(DocumentAccessPolicy.class);
            DocDocument document = code == 404 ? null : new DocDocument();
            when(documents.selectById(4L)).thenReturn(document);
            var rejection = new BusinessException(code, "original policy failure");
            doThrow(rejection).when(access).requireRead(document, 8L);
            assertSame(rejection, assertThrows(BusinessException.class,
                    () -> new ProjectDocumentQueryService(documents, access).requireReadable(4L, 8L)));
            verify(access).requireRead(document, 8L);
            verifyNoMoreInteractions(access);
        }
    }
}
