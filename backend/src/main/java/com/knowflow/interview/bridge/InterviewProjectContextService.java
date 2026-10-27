package com.knowflow.interview.bridge;

import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.security.DocumentAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

/** 只冻结有阅读权限的文档片段，不读取文件系统、不声称验证过项目代码。 */
@Service
@RequiredArgsConstructor
public class InterviewProjectContextService {
    private final DocDocumentMapper documents;
    private final DocumentAccessPolicy access;
    public record Evidence(String sourceId, Long documentId, String title, String quote,
                           String verification, String claimScope) {}
    public record Snapshot(String userDescription, String userDescriptionSource, List<Evidence> evidence) {}
    public Snapshot resolve(Long actor, List<Long> documentIds, String userDescription) {
        if (actor == null) throw new BusinessException(401, "未登录");
        if (documentIds != null && documentIds.size() > 8) throw new BusinessException(400, "最多选择八篇项目文档");
        if (userDescription != null && userDescription.length() > 4000) throw new BusinessException(400, "项目描述过长");
        List<Evidence> evidence = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(documentIds == null ? List.<Long>of() : documentIds)) {
            if (id == null || id <= 0) throw new BusinessException(400, "文档 ID 无效");
            var doc = documents.selectById(id);
            access.requireRead(doc, actor);
            String content = doc.getContent() == null ? "" : doc.getContent();
            evidence.add(new Evidence("document:" + id, id, bounded(doc.getTitle(), 200), bounded(content, 2400),
                    "VERIFIED", "DOCUMENT_CONTENT_ONLY"));
        }
        return new Snapshot(userDescription == null ? "" : userDescription.trim(), "USER_REPORTED", List.copyOf(evidence));
    }
    private static String bounded(String value, int max) { return value == null ? "" : value.substring(0, Math.min(max,value.length())); }
}
