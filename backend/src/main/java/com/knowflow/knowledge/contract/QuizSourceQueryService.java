package com.knowflow.knowledge.contract;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.common.SecurityUtils;
import com.knowflow.knowledge.entity.DocDocument;
import com.knowflow.knowledge.mapper.DocCategoryMapper;
import com.knowflow.knowledge.mapper.DocDocumentMapper;
import com.knowflow.security.DocumentAccessPolicy;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** 为管理题库提供经过文档权限校验的有限出题上下文。 */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class QuizSourceQueryService {

    private final DocCategoryMapper categories;
    private final DocDocumentMapper documents;
    private final DocumentAccessPolicy access;

    public record Excerpt(String title, String content) {}

    public record CategorySource(String name, List<Excerpt> excerpts) {}

    /** 读取单个来源，保留历史缺失返回，并校验私人资料边界。 */
    public Excerpt findDocument(long docId) {
        var doc = documents.selectById(docId);
        if (doc == null) {
            return null;
        }
        access.requireRead(doc, SecurityUtils.getCurrentUserId());
        return excerpt(doc, 6000);
    }

    /** 先过滤可见范围，再按原顺序取最多十份已发布文档。 */
    public CategorySource findCategory(long categoryId) {
        var category = categories.selectById(categoryId);
        if (category == null) {
            return null;
        }
        var query = new LambdaQueryWrapper<DocDocument>()
                .select(DocDocument::getTitle, DocDocument::getContent)
                .eq(DocDocument::getCategoryId, categoryId)
                .eq(DocDocument::getStatus, 1)
                .orderByAsc(DocDocument::getSortOrder)
                .last("LIMIT 10");
        var excerpts = documents.selectList(access.scope(query, SecurityUtils.getCurrentUserId()))
                .stream().map(doc -> excerpt(doc, 2000)).toList();
        return new CategorySource(category.getName(), excerpts);
    }

    private Excerpt excerpt(DocDocument doc, int limit) {
        String content = doc.getContent();
        return new Excerpt(doc.getTitle(),
                content != null && content.length() > limit ? content.substring(0, limit) : content);
    }
}
