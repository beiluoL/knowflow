package com.knowflow.interview.career;

import com.knowflow.exception.BusinessException;
import com.knowflow.service.DocumentTextExtractor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 恶意输入必须在写入前被限界；真实解析器不能读取外部实体或解密受保护文件。 */
class ResumeFileSecurityRegressionTest {
    private final DocumentTextExtractor extractor = mock(DocumentTextExtractor.class);
    private final CareerRecordService records = mock(CareerRecordService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final CareerUploadService uploads = new CareerUploadService(extractor, records, jdbc, manager);

    @Test void rejectsMoreThanOneThousandZipEntriesBeforeParsingOrWriting() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (int index = 0; index < 1001; index++) {
                zip.putNextEntry(new ZipEntry("entry-" + index));
                zip.closeEntry();
            }
        }
        assertThrows(BusinessException.class, () -> uploads.upload(1L, "RESUME", file(bytes.toByteArray())));
        verifyNoInteractions(extractor, records, jdbc, manager);
    }

    @Test void rejectsZipExpandedPastTenMiBBeforeParsingOrWriting() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            byte[] block = new byte[8192];
            for (int index = 0; index < 1281; index++) zip.write(block);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.closeEntry();
        }
        assertTrue(bytes.size() < 5 * 1024 * 1024, "压缩上传本身小于5MiB");
        assertThrows(BusinessException.class, () -> uploads.upload(1L, "RESUME", file(bytes.toByteArray())));
        verifyNoInteractions(extractor, records, jdbc, manager);
    }

    @Test void docxExternalEntityCannotReadLocalCanary(@TempDir Path temp) throws Exception {
        String secret = "PRIVATE_CANARY_829176456";
        Path canary = Files.writeString(temp.resolve("private.txt"), secret);
        var original = new ByteArrayOutputStream();
        try (var document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("ordinary resume");
            document.write(original);
        }
        var payload = new ByteArrayOutputStream();
        try (var input = new ZipInputStream(new ByteArrayInputStream(original.toByteArray()));
             var output = new ZipOutputStream(payload)) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                output.putNextEntry(new ZipEntry(entry.getName()));
                if ("word/document.xml".equals(entry.getName())) {
                    String xml = "<?xml version=\"1.0\"?><!DOCTYPE w:document [<!ENTITY private SYSTEM \""
                        + canary.toUri() + "\">]><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                        + "<w:body><w:p><w:r><w:t>&private;</w:t></w:r></w:p></w:body></w:document>";
                    output.write(xml.getBytes(StandardCharsets.UTF_8));
                } else input.transferTo(output);
                output.closeEntry();
            }
        }
        var real = new DocumentTextExtractor();
        try {
            assertFalse(real.extractCareerText(file(payload.toByteArray())).contains(secret));
        } finally { real.shutdown(); }
    }

    @Test void encryptedPdfWithoutPasswordProducesNoInventedText() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var document = new PDDocument()) {
            document.addPage(new PDPage());
            document.protect(new StandardProtectionPolicy("owner-password", "user-password", new AccessPermission()));
            document.save(bytes);
        }
        var real = new DocumentTextExtractor();
        try {
            assertTrue(real.extractCareerText(new MockMultipartFile("file", "private.pdf", "application/pdf", bytes.toByteArray())).isBlank());
        } finally { real.shutdown(); }
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "private.docx", "application/octet-stream", bytes);
    }
}
