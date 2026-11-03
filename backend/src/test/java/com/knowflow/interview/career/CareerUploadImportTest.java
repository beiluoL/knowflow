package com.knowflow.interview.career;

import com.knowflow.exception.BusinessException;
import com.knowflow.service.DocumentTextExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 导入边界及失败保留私人原件的服务合同，不调用 AI。 */
class CareerUploadImportTest {
    private final DocumentTextExtractor extractor = mock(DocumentTextExtractor.class);
    private final CareerRecordService records = mock(CareerRecordService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final CareerUploadService service = new CareerUploadService(extractor, records, jdbc, manager);

    @Test void acceptsRealDocMagicAndRejectsDocxExtensionForSameBytes() throws Exception {
        try (var resource = getClass().getResourceAsStream("/career/legacy-simple.doc")) {
            assertNotNull(resource);
            byte[] bytes = resource.readAllBytes();
            when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
            when(extractor.extractCareerText(any())).thenReturn("This is a simple file created with Word 97-SR2.");
            when(records.create(eq(1L), eq("RESUME"), any())).thenReturn(Map.of("id", 42L));
            var result = service.upload(1L, "RESUME", new MockMultipartFile("file", "resume.doc", "application/msword", bytes));
            assertEquals("EXTRACTED", result.get("importStatus"));
            verify(jdbc).update(anyString(), eq(42L), eq(1L), eq("application/msword"), any(byte[].class));
            assertThrows(BusinessException.class, () -> service.upload(1L, "RESUME",
                new MockMultipartFile("file", "resume.docx", "application/msword", bytes)));
        }
    }

    @Test void rejectsAnonymousAndExtensionSpoofingBeforeWriting() {
        var file = file("resume.exe", "Java resume");
        assertThrows(BusinessException.class, () -> service.upload(null, "RESUME", file));
        assertThrows(BusinessException.class, () -> service.upload(1L, "RESUME", file));
        assertThrows(BusinessException.class, () -> service.upload(1L, "RESUME", file("resume.pdf", "Java resume")));
        verifyNoInteractions(records, jdbc, extractor);
    }

    @Test void rejectsEmptyOversizedAndBinaryText() {
        assertThrows(BusinessException.class, () -> service.upload(1L, "RESUME", file("resume.txt", "")));
        assertThrows(BusinessException.class, () -> service.upload(1L, "RESUME",
            new MockMultipartFile("file", "resume.txt", "text/plain", new byte[5 * 1024 * 1024 + 1])));
        assertThrows(BusinessException.class, () -> service.upload(1L, "RESUME",
            new MockMultipartFile("file", "resume.txt", "text/plain", new byte[]{0,1,2})));
        verifyNoInteractions(records, jdbc, extractor);
    }

    @Test void preservesUnreadablePdfAsPrivatePendingRecord() {
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(extractor.extractCareerText(any())).thenReturn("");
        when(records.create(eq(1L), eq("RESUME"), any())).thenReturn(Map.of("id", 42L));
        var result = service.upload(1L, "RESUME", file("resume.pdf", "%PDF-broken"));
        assertEquals("NEEDS_TEXT", result.get("importStatus"));
        assertTrue(result.get("importWarning").toString().contains("OCR"));
        verify(records).create(eq(1L), eq("RESUME"), argThat(edit ->
            edit.source().equals(CareerUploadService.NEEDS_TEXT_SOURCE) && edit.sections().isEmpty()));
        verify(jdbc).update(anyString(), eq(42L), eq(1L), eq("application/pdf"), any(byte[].class));
        verify(manager).commit(any());
    }

    @Test void warnsAboutReplacementCharactersWithoutInventingContent() {
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(extractor.extractCareerText(any())).thenReturn("Java \uFFFD developer");
        when(records.create(eq(1L), eq("RESUME"), any())).thenReturn(Map.of("id", 42L));
        var result = service.upload(1L, "RESUME", file("resume.txt", "Java developer"));
        assertEquals("REVIEW_REQUIRED", result.get("importStatus"));
        verify(records).create(eq(1L), eq("RESUME"), argThat(edit -> edit.source().contains("\uFFFD")));
    }

    @Test void downloadChecksOwnershipBeforeReadingPrivateBytes() {
        doThrow(new BusinessException(404, "个人资料不存在")).when(records).detail(2L, 42L, null);
        assertThrows(BusinessException.class, () -> service.download(2L, 42L));
        verifyNoInteractions(jdbc);
    }

    @Test void oleSpreadsheetCannotMasqueradeAsDoc() throws Exception {
        try (var fs = new org.apache.poi.poifs.filesystem.POIFSFileSystem();
             var bytes = new java.io.ByteArrayOutputStream()) {
            fs.createDocument(new java.io.ByteArrayInputStream(new byte[]{1, 2}), "Workbook");
            fs.writeFilesystem(bytes);
            assertThrows(BusinessException.class, () -> service.upload(1L, "RESUME",
                new MockMultipartFile("file", "resume.doc", "application/msword", bytes.toByteArray())));
            verifyNoInteractions(records, jdbc, extractor);
        }
    }

    private MockMultipartFile file(String name, String text) {
        return new MockMultipartFile("file", name, "application/octet-stream", text.getBytes(StandardCharsets.UTF_8));
    }
}
