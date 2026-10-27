package com.knowflow.learning.actions;
import com.knowflow.entity.*;
import com.knowflow.mapper.DocCategoryMapper;
import com.knowflow.security.DocumentAccessPolicy;
import com.knowflow.service.KbMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;
/** Personal planning selects public, owned or explicitly shared sources, not admin moderation scope. */
@Component @RequiredArgsConstructor
public class PersonalLearningResourceScope {
 private final DocumentAccessPolicy access;private final DocCategoryMapper categories;private final KbMemberService members;
 public boolean category(Long id,Long user){
  if(user==null||!access.canReadCategory(id,user))return false;
  if(access.canReadCategory(id,null))return true;
  Set<Long> seen=new HashSet<>();Long current=id;
  for(int depth=0;current!=null&&depth<32&&seen.add(current);depth++){
   var c=categories.selectById(current);if(c==null||!Integer.valueOf(1).equals(c.getStatus()))return false;
   if(Objects.equals(user,c.getOwnerId()))return true;
   var member=members.getMember(current,user);if(member!=null&&Integer.valueOf(1).equals(member.getStatus())&&member.getRole()!=null&&Set.of("OWNER","EDITOR","READER").contains(member.getRole()))return true;
   if(c.getParentId()==null||c.getParentId()==0)return false;
   var parent=categories.selectById(c.getParentId());if(parent==null||!Objects.equals(c.getOwnerId(),parent.getOwnerId()))return false;
   current=c.getParentId();
  }return false;
 }
 public boolean document(DocDocument doc,Long user){return doc!=null&&Integer.valueOf(1).equals(doc.getStatus())&&category(doc.getCategoryId(),user)&&access.canRead(doc,user);}
}
