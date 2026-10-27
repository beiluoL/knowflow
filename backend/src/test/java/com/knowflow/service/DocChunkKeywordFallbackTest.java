package com.knowflow.service;

import com.knowflow.entity.DocChunk;
import com.knowflow.entity.DocDocument;
import com.knowflow.entity.DocCategory;
import com.knowflow.mapper.DocChunkMapper;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.service.impl.DocChunkServiceImpl;
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

        DocChunkServiceImpl service = new DocChunkServiceImpl(mapper, embedding, docMapper, members, categories, policy());

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

        DocChunkServiceImpl service = new DocChunkServiceImpl(chunks, embedding, docs, members, categories, policy());

        assertTrue(service.searchSimilar("secret", 5, 8L).isEmpty());
        verifyNoInteractions(chunks, embedding);
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
