package com.knowflow.service;

import com.knowflow.knowledge.service.DocChunkService;
import com.knowflow.knowledge.service.DocService;
import com.knowflow.knowledge.service.KnowledgeImportService;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.config.UploadConfigProperties;
import com.knowflow.knowledge.dto.KnowledgeImportOptionsDTO;
import com.knowflow.knowledge.entity.DocCategory;
import com.knowflow.knowledge.entity.DocDocument;
import com.knowflow.knowledge.entity.DocFavorite;
import com.knowflow.knowledge.mapper.DocCategoryMapper;
import com.knowflow.knowledge.mapper.DocDocumentMapper;
import com.knowflow.knowledge.mapper.DocFavoriteMapper;
import com.knowflow.support.FakeAiConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * 验证导入索引失败后的记录保护、回滚及重试。
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
@TestPropertySource(properties = "knowflow.datasource.h2.url=jdbc:h2:mem:import_index_fault;DB_CLOSE_DELAY=-1;MODE=MySQL")
class KnowledgeImportIndexFailureTest {
    @Autowired private KnowledgeImportService importer;
    @Autowired private DocCategoryMapper categories;
    @Autowired private DocDocumentMapper documents;
    @MockBean private DocChunkService chunks;
    @Autowired private UploadConfigProperties uploadConfig;
    @Autowired private DocService docService;
    @Autowired private DocFavoriteMapper favorites;

    @Test
    void failedIndexLeavesDocumentAndIncrementalRetrySkipsIt() {
        KnowledgeImportOptionsDTO options = createTarget();
        MultipartFile[] files = {new MockMultipartFile("files", "fault.md", "text/markdown",
                "# Index fault\n\nIsolated fixture.".getBytes(StandardCharsets.UTF_8))};
        doThrow(new IllegalStateException("injected index failure"))
                .when(chunks).indexDocument(anyLong(), anyString());

        var first = importer.importDirectoryWithProgress(files, options, 2L, UUID.randomUUID().toString(), null);
        assertEquals(1, first.getSuccessCount());
        assertEquals(0, first.getFailedCount());

        var retry = importer.importDirectoryWithProgress(files, options, 2L, UUID.randomUUID().toString(), null);
        assertEquals(1, retry.getSkippedCount());
        assertEquals(0, retry.getSuccessCount());
        assertEquals(1L, documentCount(options));
        verify(chunks).indexDocument(anyLong(), anyString());
    }

    @Test
    void unreadableMarkdownDoesNotWriteDocumentAndRetryCanImport() throws IOException {
        KnowledgeImportOptionsDTO options = createTarget();
        MockMultipartFile readable = markdown("# Retry");
        MockMultipartFile unreadable = spy(markdown("# Retry"));
        doThrow(new IOException("injected read failure")).when(unreadable).getBytes();

        var failed = importer.importDirectoryWithProgress(new MultipartFile[]{unreadable},
                options, 2L, UUID.randomUUID().toString(), null);
        assertEquals(1, failed.getFailedCount());
        assertEquals(0L, documentCount(options));
        assertEquals(0, categories.selectById(options.getTargetCategoryId()).getDocCount());

        var retry = importer.importDirectoryWithProgress(new MultipartFile[]{readable},
                options, 2L, UUID.randomUUID().toString(), null);
        assertEquals(1, retry.getSuccessCount());
        assertEquals(1L, documentCount(options));
    }

    @Test
    void failedImageWritePreservesOriginalLinkAndRetrySkipsDocument() throws IOException {
        KnowledgeImportOptionsDTO options = createTarget();
        MockMultipartFile image = spy(new MockMultipartFile("files", "fault.png",
                "image/png", new byte[]{1}));
        doThrow(new IOException("injected disk failure")).when(image).transferTo(any(File.class));
        MultipartFile[] files = {markdown("# Image\n\n![fixture](fault.png)"), image};
        String batchId = UUID.randomUUID().toString();

        var result = importer.importDirectoryWithProgress(files, options, 2L, batchId, null);
        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getImageCount());
        assertEquals(1, categories.selectById(options.getTargetCategoryId()).getDocCount());
        DocDocument document = documents.selectOne(new LambdaQueryWrapper<DocDocument>()
                .eq(DocDocument::getCategoryId, options.getTargetCategoryId()));
        assertTrue(document.getContent().contains("![fixture](fault.png)"));
        try (var paths = Files.list(Path.of(uploadConfig.getDir(), "imports", batchId))) {
            assertEquals(0L, paths.count());
        }

        var retry = importer.importDirectoryWithProgress(files, options, 2L, UUID.randomUUID().toString(), null);
        assertEquals(1, retry.getSkippedCount());
        assertEquals(1L, documentCount(options));
        verify(image).transferTo(any(File.class));
    }

    @Test
    void failedChunkDeletionRollsBackFavoriteRemovalAndAllowsRetry() {
        KnowledgeImportOptionsDTO options = createTarget();
        importer.importDirectoryWithProgress(new MultipartFile[]{markdown("# Delete retry")},
                options, 2L, UUID.randomUUID().toString(), null);
        DocDocument document = documents.selectOne(new LambdaQueryWrapper<DocDocument>()
                .eq(DocDocument::getCategoryId, options.getTargetCategoryId()));
        DocFavorite favorite = new DocFavorite();
        favorite.setDocId(document.getId());
        favorite.setUserId(2L);
        favorites.insert(favorite);
        doThrow(new IllegalStateException("injected chunk delete failure"))
                .doNothing().when(chunks).deleteChunks(document.getId());

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(2L, "unused", java.util.List.of()));
        SecurityContextHolder.setContext(context);
        try {
            assertThrows(IllegalStateException.class, () -> docService.removeDoc(document.getId()));
            assertEquals(1L, documentCount(options));
            assertEquals(1, categories.selectById(options.getTargetCategoryId()).getDocCount());
            assertEquals(document.getId(), favorites.selectById(favorite.getId()).getDocId());

            docService.removeDoc(document.getId());
            assertEquals(0L, documentCount(options));
            assertEquals(0, categories.selectById(options.getTargetCategoryId()).getDocCount());
            assertNull(favorites.selectById(favorite.getId()));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private KnowledgeImportOptionsDTO createTarget() {
        DocCategory category = new DocCategory();
        category.setName("import-fault-" + UUID.randomUUID());
        category.setOwnerId(2L);
        category.setParentId(0L);
        category.setStatus(1);
        categories.insert(category);
        KnowledgeImportOptionsDTO options = new KnowledgeImportOptionsDTO();
        options.setTargetCategoryId(category.getId());
        options.setCreateSubCategories(false);
        return options;
    }

    private MockMultipartFile markdown(String content) {
        return new MockMultipartFile("files", "fault.md", "text/markdown",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private long documentCount(KnowledgeImportOptionsDTO options) {
        return documents.selectCount(new LambdaQueryWrapper<DocDocument>()
                .eq(DocDocument::getCategoryId, options.getTargetCategoryId()));
    }
}
