package com.knowflow.knowledge.service.query;

import com.knowflow.knowledge.contract.ProjectDocumentQuery;
import com.knowflow.knowledge.mapper.DocDocumentMapper;
import com.knowflow.security.DocumentAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Keeps document lookup and the existing read policy inside the document owner's boundary. */
@Service
@RequiredArgsConstructor
public class ProjectDocumentQueryService implements ProjectDocumentQuery {
    private final DocDocumentMapper documents;
    private final DocumentAccessPolicy access;

    /** Preserves lookup-before-authorization and propagates the original policy exception unchanged. */
    @Override
    public Content requireReadable(long documentId, long actor) {
        var document = documents.selectById(documentId);
        access.requireRead(document, actor);
        return new Content(document.getTitle(), document.getContent());
    }
}
