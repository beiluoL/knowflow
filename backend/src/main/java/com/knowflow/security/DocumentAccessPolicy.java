package com.knowflow.security;

import com.knowflow.knowledge.entity.DocCategory;
import com.knowflow.knowledge.entity.DocDocument;
import com.knowflow.knowledge.entity.KgEntity;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.common.SecurityUtils;
import com.knowflow.exception.BusinessException;
import com.knowflow.knowledge.mapper.DocCategoryMapper;
import com.knowflow.knowledge.service.KbMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

/** 共享文档访问边界，成员角色仍由 KbMemberService 管理。 */
@Component @RequiredArgsConstructor
public class DocumentAccessPolicy {
    private final DocCategoryMapper categories;
    private final KbMemberService members;
    private final com.knowflow.knowledge.mapper.DocDocumentMapper documents;
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
        if (categoryId == null) return !edit; // 历史上未分类的已发布文档。
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
                // 同一所有者的导入后代节点继承根节点成员。
                if(ancestor==null || !Objects.equals(cat.getOwnerId(),ancestor.getOwnerId())) return false;
            }
            if(parent==null || parent==0) return !edit && cat.getOwnerId()==null;
            cursor=parent;
        }
        return false; // 孤立节点和循环引用均拒绝访问。
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
        // 公开列表不得暴露草稿，即使调用方自行指定查询状态。
        query.eq(DocDocument::getStatus,1);
        return query;
    }
    public Long actor(){return SecurityUtils.getCurrentUserIdNullable();}
}
