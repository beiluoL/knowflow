package com.knowflow.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.common.SecurityUtils;
import com.knowflow.entity.*;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.DocCategoryMapper;
import com.knowflow.service.KbMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

/** Shared document boundary. Membership roles remain owned by KbMemberService. */
@Component @RequiredArgsConstructor
public class DocumentAccessPolicy {
    private final DocCategoryMapper categories;
    private final KbMemberService members;
    private final com.knowflow.mapper.DocDocumentMapper documents;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.knowflow.learning.studypack.StudyPackAccessPolicy studyPacks;
    public boolean canReadKnowledge(KgEntity entity,Long userId) {
        return entity!=null && canReadCategory(entity.getCategoryId(),userId)
                && (entity.getDocId()==null || canRead(documents.selectById(entity.getDocId()),userId));
    }
    public void requireReadKnowledge(KgEntity entity,Long userId) {
        if(entity==null) throw new BusinessException(404,"知识单元不存在");
        if(!canReadKnowledge(entity,userId)) throw new BusinessException(userId==null?401:403,"无权阅读该知识单元");
    }
    public boolean admin(Long userId) { return userId != null && members.canViewAll(userId); }

    public boolean canReadCategory(Long categoryId, Long userId) { return allowed(categoryId,userId,false); }
    public boolean canEditCategory(Long categoryId, Long userId) { return userId != null && allowed(categoryId,userId,true); }
    private boolean allowed(Long categoryId,Long userId,boolean edit) {
        if (admin(userId)) return true;
        if (categoryId == null) return !edit; // Historical uncategorized published documents.
        Set<Long> visited=new HashSet<>(); Long cursor=categoryId;
        while(cursor != null && cursor != 0 && visited.add(cursor)) {
            DocCategory cat=categories.selectById(cursor);
            if(cat==null || !Integer.valueOf(1).equals(cat.getStatus())) return false;
            if(userId != null && userId.equals(cat.getOwnerId())) return true;
            var active=members.listByCategory(cursor);
            if(!active.isEmpty()) return edit ? members.canEditDocs(cursor,userId) : members.canView(cursor,userId);
            Long parent=cat.getParentId();
            if(cat.getOwnerId()!=null) {
                DocCategory ancestor=parent==null||parent==0 ? null : categories.selectById(parent);
                // Imported descendants with the same owner inherit the root's members.
                if(ancestor==null || !Objects.equals(cat.getOwnerId(),ancestor.getOwnerId())) return false;
            }
            if(parent==null || parent==0) return !edit && cat.getOwnerId()==null;
            cursor=parent;
        }
        return false; // Orphans and cycles fail closed.
    }
    public boolean canRead(DocDocument doc,Long userId) {
        return doc!=null && (studyPacks==null || studyPacks.canRead(doc.getId(),userId)) && !Integer.valueOf(1).equals(doc.getDeleted()) && canReadCategory(doc.getCategoryId(),userId)
                && (Integer.valueOf(1).equals(doc.getStatus()) || canEditCategory(doc.getCategoryId(),userId));
    }
    public void requireRead(DocDocument doc,Long userId) {
        if(doc==null || Integer.valueOf(1).equals(doc.getDeleted())) throw new BusinessException(404,"文档不存在");
        if(!canRead(doc,userId)) throw new BusinessException(userId==null ? 401 : 403,"无权阅读该文档");
    }
    public void requireEditCategory(Long categoryId,Long userId) {
        if(userId==null) throw new BusinessException(401,"未登录");
        if(categoryId!=null && categories.selectById(categoryId)==null) throw new BusinessException(404,"知识库不存在");
        if(!canEditCategory(categoryId,userId)) throw new BusinessException(403,"需要知识库 Owner 或 Editor 权限");
    }
    public void requireEdit(DocDocument doc,Long userId) {
        if(doc==null) throw new BusinessException(404,"文档不存在");
        if(studyPacks!=null && !studyPacks.canRead(doc.getId(),userId))throw new BusinessException(403,"无权编辑私人学习资料");
        requireEditCategory(doc.getCategoryId(),userId);
    }
    public LambdaQueryWrapper<DocDocument> scope(LambdaQueryWrapper<DocDocument> query,Long userId) {
        if(studyPacks!=null)studyPacks.scope(query,userId);
        if(admin(userId)) return query;
        var readable=categories.selectList(new LambdaQueryWrapper<DocCategory>()).stream()
                .filter(c->canReadCategory(c.getId(),userId)).map(DocCategory::getId).toList();
        query.and(w->{w.isNull(DocDocument::getCategoryId);if(!readable.isEmpty()) w.or().in(DocDocument::getCategoryId,readable);});
        // Public listings never expose drafts, regardless of caller-controlled query status.
        query.eq(DocDocument::getStatus,1);
        return query;
    }
    public Long actor(){return SecurityUtils.getCurrentUserIdNullable();}
}
