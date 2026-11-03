package com.knowflow.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;

/** 真实 Tika 解析段落、表格和无文字 PDF；不调用 OCR 或远程模型。 */
class CareerDocumentExtractionTest {
    @Test void extractsRealLegacyWordDocThroughTika() throws Exception {
        try (var resource = getClass().getResourceAsStream("/career/legacy-simple.doc")) {
            assertNotNull(resource);
            byte[] bytes = resource.readAllBytes();
            assertArrayEquals(new byte[]{(byte)0xD0,(byte)0xCF,0x11,(byte)0xE0,(byte)0xA1,(byte)0xB1,0x1A,(byte)0xE1},
                java.util.Arrays.copyOf(bytes, 8));
            var extractor = new DocumentTextExtractor();
            try {
                String text = extractor.extractCareerText(new MockMultipartFile("file", "resume.doc", "application/msword", bytes));
                assertTrue(text.contains("This is a simple file created with Word 97-SR2."));
            } finally { extractor.shutdown(); }
        }
    }

    @Test void extractsDocxParagraphAndTable() throws Exception {
        try (var document = new XWPFDocument(); var bytes = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("简历段落 Java developer");
            document.createTable(1, 1).getRow(0).getCell(0).setText("表格项目 evidence");
            document.write(bytes);
            var extractor = new DocumentTextExtractor();
            try {
                String text = extractor.extractCareerText(new MockMultipartFile("file", "resume.docx", "application/octet-stream", bytes.toByteArray()));
                assertTrue(text.contains("简历段落 Java developer"));
                assertTrue(text.contains("表格项目 evidence"));
            } finally { extractor.shutdown(); }
        }
    }

    @Test void imageOnlyPdfProducesNoInventedText() throws Exception {
        try (var document = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(bytes);
            var extractor = new DocumentTextExtractor();
            try {
                assertTrue(extractor.extractCareerText(new MockMultipartFile("file", "scan.pdf", "application/pdf", bytes.toByteArray())).isBlank());
            } finally { extractor.shutdown(); }
        }
    }
}
