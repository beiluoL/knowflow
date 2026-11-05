package com.knowflow.knowledge.service.query;

import com.knowflow.knowledge.contract.ProjectDocumentQuery;
import com.knowflow.knowledge.mapper.DocDocumentMapper;
import com.knowflow.security.DocumentAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 将文档查找和现有读取策略保留在文档所属模块边界内。 */
@Service
@RequiredArgsConstructor
public class ProjectDocumentQueryService implements ProjectDocumentQuery {
    private final DocDocumentMapper documents;
    private final DocumentAccessPolicy access;

    /** 保留先查找后授权的顺序，原样传播既有策略异常。 */
    @Override
    public Content requireReadable(long documentId, long actor) {
        var document = documents.selectById(documentId);
        access.requireRead(document, actor);
        return new Content(document.getTitle(), document.getContent());
    }
}
