package com.knowflow.service;

import com.knowflow.ai.service.EmbeddingService;
import com.knowflow.knowledge.service.CategoryService;
import com.knowflow.knowledge.service.KbMemberService;


import com.knowflow.knowledge.entity.DocChunk;
import com.knowflow.knowledge.entity.DocDocument;
import com.knowflow.knowledge.entity.DocCategory;
import com.knowflow.knowledge.mapper.DocChunkMapper;
import com.knowflow.knowledge.mapper.DocDocumentMapper;
import com.knowflow.knowledge.service.impl.DocChunkServiceImpl;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class DocChunkKeywordFallbackTest {

    @Test
    void searchFallsBackToKeywordChunksWhenEmbeddingIsUnavailable() {
        DocChunkMapper mapper = mock(DocChunkMapper.class);
        DocDocumentMapper docMapper = mock(DocDocumentMapper.class);
        EmbeddingService embedding = mock(EmbeddingService.class);
        KbMemberService members = mock(KbMemberService.class);
        CategoryService categories = mock(CategoryService.class);
        DocDocument published = new DocDocument();
        published.setId(9L);
        published.setCategoryId(4L);
        DocCategory root = category(3L, 0L);
        DocCategory child = category(4L, 3L);
        DocChunk matched = new DocChunk();
        matched.setContent("axios upload file 使用 multipart/form-data");
        when(categories.list(org.mockito.ArgumentMatchers.<Wrapper<DocCategory>>any())).thenReturn(List.of(root, child));
        when(docMapper.selectList(any())).thenReturn(List.of(published));
        when(members.listViewableCategoryIds(7L)).thenReturn(java.util.Set.of(3L));
        when(embedding.embed("axios upload")).thenReturn(List.of());
        when(mapper.selectList(any())).thenReturn(List.of(matched));

        DocChunkServiceImpl service = new DocChunkServiceImpl(mapper, embedding, docMapper, members, categories, policy(), mock(com.knowflow.knowledge.mapper.RagIndexRevisionMapper.class));

        assertEquals(List.of(matched.getContent()), service.searchSimilar("axios upload", 5, 7L));
    }

    @Test
    void searchNeverReadsChunksWhenUserHasNoAccessiblePublishedDocuments() {
        DocChunkMapper chunks = mock(DocChunkMapper.class);
        DocDocumentMapper docs = mock(DocDocumentMapper.class);
        EmbeddingService embedding = mock(EmbeddingService.class);
        KbMemberService members = mock(KbMemberService.class);
        CategoryService categories = mock(CategoryService.class);
        DocDocument privateDoc = new DocDocument();
        privateDoc.setId(9L);
        privateDoc.setCategoryId(3L);
        when(categories.list(org.mockito.ArgumentMatchers.<Wrapper<DocCategory>>any())).thenReturn(List.of(category(3L, 0L)));
        when(members.listViewableCategoryIds(8L)).thenReturn(java.util.Set.of());

        DocChunkServiceImpl service = new DocChunkServiceImpl(chunks, embedding, docs, members, categories, policy(), mock(com.knowflow.knowledge.mapper.RagIndexRevisionMapper.class));

        assertTrue(service.searchSimilar("secret", 5, 8L).isEmpty());
        verifyNoInteractions(chunks, embedding);
    }

    @Test
    void hybridRanksOverlapFirstAndKeepsKeywordOnlyEvidence() {
        DocChunkMapper chunks = mock(DocChunkMapper.class);
        DocDocumentMapper docs = mock(DocDocumentMapper.class);
        EmbeddingService embedding = mock(EmbeddingService.class);
        DocDocument visible = new DocDocument();
        visible.setId(9L);
        when(docs.selectList(any())).thenReturn(List.of(visible));
        when(embedding.embed("java")).thenReturn(List.of(1f, 0f));
        when(embedding.cosineSimilarity(any(), any())).thenReturn(0.9, 0.8);
        DocChunk keywordOnly = chunk(1L, "keyword only", null);
        DocChunk overlap = chunk(2L, "both channels", "1,0");
        DocChunk vectorOnly = chunk(3L, "vector only", "0.8,0.2");
        when(chunks.selectList(any())).thenReturn(List.of(keywordOnly, overlap), List.of(overlap, vectorOnly));
        var service = new DocChunkServiceImpl(chunks, embedding, docs, mock(KbMemberService.class),
                mock(CategoryService.class), policy(), mock(com.knowflow.knowledge.mapper.RagIndexRevisionMapper.class));

        var result = service.searchSimilarTraced("java", 5, 7L, step -> {});
        assertEquals(List.of("both channels", "keyword only", "vector only"), result.chunks);
        assertEquals("hybrid", result.trace.getStrategy());
    }

    private DocChunk chunk(long id, String content, String embedding) {
        DocChunk chunk = new DocChunk();
        chunk.setId(id);
        chunk.setDocId(9L);
        chunk.setContent(content);
        chunk.setEmbedding(embedding);
        return chunk;
    }

    private DocCategory category(Long id, Long parentId) {
        DocCategory category = new DocCategory();
        category.setId(id);
        category.setParentId(parentId);
        return category;
    }
    private com.knowflow.security.DocumentAccessPolicy policy() {
        var policy=mock(com.knowflow.security.DocumentAccessPolicy.class);
        when(policy.scope(any(),any())).thenAnswer(invocation->invocation.getArgument(0));
        return policy;
    }
}
