package com.knowflow.service;

import com.knowflow.common.PageResult;
import com.knowflow.dto.LearningNoteDTO;
import com.knowflow.entity.LearningNote;
import com.knowflow.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** 笔记目录、标签与关系管理。正文变更一律委托NoteService，不能绕过revision。 */
@Service @RequiredArgsConstructor
public class NoteOrganizationService {
 private final JdbcTemplate jdbc;
 private final NoteService notes;
 private final DocService docs;
 private final ObjectMapper json;
 public record Folder(long id,String name,Long parentId,long revision,long count){}
 public record Tag(long id,String name,long count){}
 public record Stats(long total,long recent,long favorites,long drafts,long trash){}
 public record Relation(long id,String type,long targetId,String title,boolean accessible,String href){}
 public record Relations(List<Relation> outgoing,List<Relation> backlinks){}
 public PageResult<LearningNote> page(Long user,int page,int size,String keyword,String view,Long folderId,List<Long> tagIds,String sort){
  if(page<1||size<1||size>100)throw new BusinessException(400,"分页参数不正确");
  if(!Set.of("ALL","RECENT","FAVORITES","DRAFT","TRASH").contains(view))throw new BusinessException(400,"笔记分类不正确");
  String order=switch(sort){case "UPDATED"->"n.update_time DESC,n.id DESC";case "CREATED"->"n.create_time DESC,n.id DESC";case "TITLE"->"n.title ASC,n.id DESC";default->throw new BusinessException(400,"排序参数不正确");};
  var args=new ArrayList<Object>();args.add(user);args.add(view.equals("TRASH")?1:0);var where=new StringBuilder(" FROM learning_note n WHERE n.user_id=? AND n.deleted=?");
  if(view.equals("RECENT")){where.append(" AND n.update_time>=?");args.add(LocalDateTime.now().minusDays(7));}
  if(view.equals("FAVORITES"))where.append(" AND n.favorite=TRUE");if(view.equals("DRAFT"))where.append(" AND n.status='DRAFT'");
  if(folderId!=null){if(folderId==0)where.append(" AND n.folder_id IS NULL");else{requireFolder(folderId,user);where.append(" AND n.folder_id=?");args.add(folderId);}}
  if(keyword!=null&&!keyword.isBlank()){where.append(" AND (LOWER(n.title) LIKE ? OR LOWER(n.content) LIKE ? OR LOWER(n.tags) LIKE ?)");String k="%"+keyword.trim().toLowerCase(Locale.ROOT)+"%";args.add(k);args.add(k);args.add(k);}
  if(tagIds!=null){if(tagIds.size()>50)throw new BusinessException(400,"筛选标签过多");for(Long tag:tagIds){where.append(" AND EXISTS(SELECT 1 FROM learning_note_tag_ref tr WHERE tr.note_id=n.id AND tr.user_id=n.user_id AND tr.tag_id=?)");args.add(tag);}}
  long total=jdbc.queryForObject("SELECT COUNT(*)"+where,Long.class,args.toArray());args.add(size);args.add((long)(page-1)*size);
  var rows=jdbc.query("SELECT n.*"+where+" ORDER BY "+order+" LIMIT ? OFFSET ?",new BeanPropertyRowMapper<>(LearningNote.class),args.toArray());
  var result=new PageResult<LearningNote>();result.setRecords(rows);result.setTotal(total);result.setPageNum(page);result.setPageSize(size);result.setPages((total+size-1)/size);return result;
 }
 public Stats stats(Long user){return new Stats(count(user,"deleted=0"),jdbc.queryForObject("SELECT COUNT(*) FROM learning_note WHERE user_id=? AND deleted=0 AND update_time>=?",Long.class,user,LocalDateTime.now().minusDays(7)),count(user,"deleted=0 AND favorite=TRUE"),count(user,"deleted=0 AND status='DRAFT'"),count(user,"deleted=1"));}
 private long count(Long user,String fixed){return jdbc.queryForObject("SELECT COUNT(*) FROM learning_note WHERE user_id=? AND "+fixed,Long.class,user);}
 public List<Folder> folders(Long user){return jdbc.query("SELECT f.*,(SELECT COUNT(*) FROM learning_note n WHERE n.user_id=f.user_id AND n.folder_id=f.id AND n.deleted=0) AS note_count FROM learning_note_folder f WHERE f.user_id=? ORDER BY f.name,f.id",(rs,i)->new Folder(rs.getLong("id"),rs.getString("name"),(Long)rs.getObject("parent_id"),rs.getLong("revision"),rs.getLong("note_count")),user);}
 private Folder requireFolder(Long id,Long user){return folders(user).stream().filter(f->f.id()==id).findFirst().orElseThrow(()->new BusinessException(404,"文件夹不存在"));}
 private String name(String name){if(name==null||name.isBlank()||name.trim().length()>100||name.matches(".*[\\r\\n\\\\/].*"))throw new BusinessException(400,"名称须为1至100字，不能包含路径分隔符");return name.trim();}
 private void lockFolders(Long user){jdbc.queryForList("SELECT id FROM learning_note_folder WHERE user_id=? ORDER BY id FOR UPDATE",user);}
 @Transactional public Folder folder(Long user,Long id,String label,Long parent,Long revision){
  String valid=name(label);lockFolders(user);
  if(id!=null){var existing=requireFolder(id,user);if(revision==null||existing.revision()!=revision)throw new BusinessException(409,"文件夹已在其他位置修改");}
  Long cursor=parent;Set<Long> seen=new HashSet<>();while(cursor!=null){if(Objects.equals(cursor,id)||!seen.add(cursor))throw new BusinessException(400,"文件夹不能移动到自身或子目录");cursor=requireFolder(cursor,user).parentId();}
  if(id==null){var holder=new org.springframework.jdbc.support.GeneratedKeyHolder();jdbc.update(c->{var p=c.prepareStatement("INSERT INTO learning_note_folder(user_id,name,parent_id,revision) VALUES(?,?,?,1)",java.sql.Statement.RETURN_GENERATED_KEYS);p.setLong(1,user);p.setString(2,valid);p.setObject(3,parent);return p;},holder);id=holder.getKey().longValue();}
  else if(jdbc.update("UPDATE learning_note_folder SET name=?,parent_id=?,revision=revision+1 WHERE id=? AND user_id=? AND revision=?",valid,parent,id,user,revision)!=1)throw new BusinessException(409,"文件夹已在其他位置修改");
  return requireFolder(id,user);
 }
 @Transactional public void deleteFolder(Long user,Long id,Long revision){lockFolders(user);var f=requireFolder(id,user);if(revision==null||f.revision()!=revision)throw new BusinessException(409,"文件夹已修改");long children=jdbc.queryForObject("SELECT COUNT(*) FROM learning_note_folder WHERE parent_id=? AND user_id=?",Long.class,id,user);long contained=jdbc.queryForObject("SELECT COUNT(*) FROM learning_note WHERE folder_id=? AND user_id=?",Long.class,id,user);if(children+contained>0)throw new BusinessException(409,"文件夹非空，请先移动其中笔记（含回收站）和子目录");jdbc.update("DELETE FROM learning_note_folder WHERE id=? AND user_id=? AND revision=?",id,user,revision);}
 public List<Tag> tags(Long user){return jdbc.query("SELECT t.id,t.name,COUNT(n.id) AS note_count FROM learning_note_tag t LEFT JOIN learning_note_tag_ref r ON r.tag_id=t.id AND r.user_id=t.user_id LEFT JOIN learning_note n ON n.id=r.note_id AND n.user_id=t.user_id AND n.deleted=0 WHERE t.user_id=? GROUP BY t.id,t.name ORDER BY t.name",(rs,i)->new Tag(rs.getLong(1),rs.getString(2),rs.getLong(3)),user);}
 private Tag requireTag(Long id,Long user){return tags(user).stream().filter(t->t.id()==id).findFirst().orElseThrow(()->new BusinessException(404,"标签不存在"));}
 @Transactional public Tag createTag(Long user,String label){String valid=name(label);long id=jdbc.execute((ConnectionCallback<Long>)c->NoteTagIndex.ensure(c,user,valid));return requireTag(id,user);}
 private LearningNoteDTO payload(LearningNote n){var d=new LearningNoteDTO();d.setTitle(n.getTitle());d.setContent(n.getContent());d.setTags(n.getTags());d.setSourceTitle(n.getSourceTitle());d.setStatus(n.getStatus());d.setRevision(n.getRevision());d.setFolderId(n.getFolderId());d.setFavorite(n.getFavorite());return d;}
 @Transactional public void editTag(Long user,Long id,String label,boolean remove){
  Tag old=requireTag(id,user);jdbc.queryForList("SELECT id FROM learning_note_tag WHERE id=? AND user_id=? FOR UPDATE",id,user);String valid=remove?null:name(label);
  var noteIds=jdbc.queryForList("SELECT n.id FROM learning_note n JOIN learning_note_tag_ref r ON n.id=r.note_id AND n.user_id=r.user_id WHERE r.tag_id=? AND r.user_id=? AND n.deleted=0 ORDER BY n.id",Long.class,id,user);
  if(!remove){long duplicates=jdbc.queryForObject("SELECT COUNT(*) FROM learning_note_tag WHERE user_id=? AND name_key=? AND id<>?",Long.class,user,valid.toLowerCase(Locale.ROOT),id);if(duplicates>0)throw new BusinessException(409,"同名标签已存在");jdbc.update("UPDATE learning_note_tag SET name=?,name_key=? WHERE id=? AND user_id=?",valid,valid.toLowerCase(Locale.ROOT),id,user);}
  for(Long noteId:noteIds){var n=notes.detail(noteId,user,false);var d=payload(n);var changed=new ArrayList<String>();for(String tag:NoteTagIndex.parse(n.getTags())){if(tag.equalsIgnoreCase(old.name())){if(!remove)changed.add(valid);}else changed.add(tag);}try{d.setTags(json.writeValueAsString(changed));}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}notes.update(noteId,user,d);}
  if(remove){jdbc.update("DELETE FROM learning_note_tag_ref WHERE user_id=? AND tag_id=?",user,id);jdbc.update("DELETE FROM learning_note_tag WHERE user_id=? AND id=?",user,id);}
 }
 public Relations relations(Long user,Long noteId){
  var note=notes.detail(noteId,user,false);var outgoing=new ArrayList<Relation>();
  if(note.getSourceDocId()!=null)outgoing.add(document(0,note.getSourceDocId(),user));
  jdbc.query("SELECT id,target_type,target_id FROM learning_note_relation WHERE user_id=? AND note_id=? ORDER BY id",(org.springframework.jdbc.core.RowCallbackHandler)rs->{long id=rs.getLong(1),target=rs.getLong(3);String type=rs.getString(2);if(type.equals("DOC"))outgoing.add(document(id,target,user));else{var n=notes.detail(target,user,true);outgoing.add(new Relation(id,"NOTE",target,n.getDeleted()==0?n.getTitle():"回收站中的笔记",n.getDeleted()==0,"/notes?note="+target));}},user,noteId);
  var backlinks=jdbc.query("SELECT r.id,n.id,n.title FROM learning_note_relation r JOIN learning_note n ON n.id=r.note_id AND n.user_id=r.user_id WHERE r.user_id=? AND r.target_type='NOTE' AND r.target_id=? AND n.deleted=0",(rs,i)->new Relation(rs.getLong(1),"NOTE",rs.getLong(2),rs.getString(3),true,"/notes?note="+rs.getLong(2)),user,noteId);
  return new Relations(outgoing,backlinks);
 }
 private Relation document(long relationId,long docId,Long user){try{var doc=docs.getDocDetail(docId,user);return new Relation(relationId,"DOC",docId,doc.getTitle(),true,"/doc/"+docId);}catch(BusinessException e){if(e.getCode()!=null&&(e.getCode()==403||e.getCode()==404))return new Relation(relationId,"DOC",docId,"文档不可访问",false,"");throw e;}}
 @Transactional public Relations addRelation(Long user,Long noteId,String type,Long target){notes.detail(noteId,user,false);if(target==null||target<1||type==null||!Set.of("DOC","NOTE").contains(type))throw new BusinessException(400,"关联目标不正确");if(type.equals("NOTE")){if(target.equals(noteId))throw new BusinessException(400,"不能关联自身");notes.detail(target,user,false);}else docs.getDocDetail(target,user);
  long count=jdbc.queryForObject("SELECT COUNT(*) FROM learning_note_relation WHERE user_id=? AND note_id=? AND target_type=? AND target_id=?",Long.class,user,noteId,type,target);
  if(count==0)try{jdbc.update("INSERT INTO learning_note_relation(user_id,note_id,target_type,target_id) VALUES(?,?,?,?)",user,noteId,type,target);}catch(org.springframework.dao.DuplicateKeyException e){/* 并发重复建立同一关系是幂等操作。 */}
  return relations(user,noteId);
 }
 @Transactional public void removeRelation(Long user,Long noteId,Long relation){notes.detail(noteId,user,false);jdbc.update("DELETE FROM learning_note_relation WHERE id=? AND user_id=? AND note_id=?",relation,user,noteId);}
}
