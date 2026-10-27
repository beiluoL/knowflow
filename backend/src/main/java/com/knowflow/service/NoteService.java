package com.knowflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.common.PageResult;
import com.knowflow.dto.LearningNoteDTO;
import com.knowflow.entity.*;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/** 普通/章节 Markdown 笔记唯一服务；康奈尔保持独立业务模型。 */
@Service @RequiredArgsConstructor
public class NoteService {
 private final LearningNoteMapper notes;
 private final NoteVersionMapper versions;
 private final LearningService learning;
 private final com.fasterxml.jackson.databind.ObjectMapper json;
 private final org.springframework.jdbc.core.JdbcTemplate jdbc;
 private final DocService docs;
 public LearningNote detail(Long id,Long user,boolean trash) {
  LearningNote n=notes.ownedIncludingDeleted(id,user);
  if(n==null || (!trash && n.getDeleted()!=0))throw new BusinessException(404,"笔记不存在");
  return n;
 }
 public PageResult<LearningNote> page(Long user,int page,int size,String keyword,boolean trash) {
  if(page<1||size<1||size>100)throw new BusinessException(400,"分页参数不正确");
  // 显式 SQL 查询回收站，避免 @TableLogic 自动补 deleted=0。
  var result=new PageResult<LearningNote>();
  result.setPageNum(page);result.setPageSize(size);
  var all=notesPage(user,page,size,keyword,trash);
  result.setTotal(all.total());result.setRecords(all.records());result.setPages((all.total()+size-1)/size);return result;
 }
 private record Slice(long total,List<LearningNote> records) {}
 private Slice notesPage(Long user,int page,int size,String keyword,boolean trash) {
  return new Slice(notes.countPage(user,trash?1:0,keyword),notes.selectPageRows(user,trash?1:0,keyword,(long)(page-1)*size,size));
 }
 public List<LearningNote> chapterList(Long user,Long chapter,Long path) {
  return notes.selectList(new LambdaQueryWrapper<LearningNote>().eq(LearningNote::getUserId,user).eq(chapter!=null,LearningNote::getChapterId,chapter).eq(path!=null,LearningNote::getPathId,path).orderByDesc(LearningNote::getUpdateTime));
 }
 private void validate(LearningNoteDTO dto,Long user) {
  if(dto.getTitle()==null||dto.getTitle().isBlank()||dto.getTitle().length()>200)throw new BusinessException(400,"标题不能为空且不超过200字");
  if(dto.getStatus()!=null&&!Set.of("SAVED","DRAFT").contains(dto.getStatus()))throw new BusinessException(400,"笔记状态不正确");
  if((dto.getChapterId()!=null&&dto.getChapterId()<=0)||(dto.getPathId()!=null&&dto.getPathId()<=0))throw new BusinessException(400,"关联ID不正确");
  if(dto.getPathId()!=null)learning.getPathDetail(dto.getPathId(),user);
  if(dto.getChapterId()!=null){var c=learning.getChapterDetail(dto.getChapterId(),user);if(dto.getPathId()!=null&&!dto.getPathId().equals(c.getPathId()))throw new BusinessException(400,"章节与路径不一致");}
 }
 private void organization(LearningNote n,LearningNoteDTO dto,Long user){
  NoteTagIndex.parse(dto.getTags());
  if(dto.isFolderSpecified()&&dto.getFolderId()!=null){if(jdbc.queryForList("SELECT id FROM learning_note_folder WHERE id=? AND user_id=? FOR UPDATE",dto.getFolderId(),user).isEmpty())throw new BusinessException(404,"文件夹不存在");}
  if(dto.isSourceDocSpecified()&&dto.getSourceDocId()!=null&&!Objects.equals(dto.getSourceDocId(),n.getSourceDocId()))docs.getDocDetail(dto.getSourceDocId(),user);
 }
 private void syncTags(LearningNote n){jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>)c->{NoteTagIndex.sync(c,n.getUserId(),n.getId(),n.getTags());return null;});}
 private void fill(LearningNote n,LearningNoteDTO dto) {
  if(dto.isFolderSpecified())n.setFolderId(dto.getFolderId());
  if(dto.isSourceDocSpecified())n.setSourceDocId(dto.getSourceDocId());
  if(dto.getFavorite()!=null)n.setFavorite(dto.getFavorite());else if(n.getFavorite()==null)n.setFavorite(false);
  n.setTitle(dto.getTitle());n.setContent(Objects.requireNonNullElse(dto.getContent(),""));
  n.setTags(dto.getTags());n.setSourceTitle(dto.getSourceTitle());
  if(dto.getChapterId()!=null)n.setChapterId(dto.getChapterId());if(dto.getPathId()!=null)n.setPathId(dto.getPathId());
  n.setSourceType(n.getChapterId()!=null||n.getPathId()!=null?"CHAPTER":"GENERAL");
  n.setContentFormat("MARKDOWN");n.setStatus(Objects.requireNonNullElse(dto.getStatus(),"SAVED"));
 }
 @Transactional public LearningNote create(Long user,LearningNoteDTO dto) {
  validate(dto,user);LearningNote n=new LearningNote();n.setUserId(user);organization(n,dto,user);fill(n,dto);n.setRevision(1L);n.setDeleted(0);notes.insert(n);syncTags(n);snapshot(n,"CREATE");return n;
 }
 @Transactional public LearningNote update(Long id,Long user,LearningNoteDTO dto) {
  LearningNote n=detail(id,user,false);requireRevision(dto.getRevision(),n);
  // 兼容章节入口省略上下文，但不允许借此绕开既有关联权限。
  if(dto.getChapterId()==null)dto.setChapterId(n.getChapterId());if(dto.getPathId()==null)dto.setPathId(n.getPathId());validate(dto,user);
  LearningNote original=new LearningNote();org.springframework.beans.BeanUtils.copyProperties(n,original);
  organization(n,dto,user);fill(n,dto);
  if(Objects.equals(original.getFolderId(),n.getFolderId())&&Objects.equals(original.getFavorite(),n.getFavorite())&&Objects.equals(original.getSourceDocId(),n.getSourceDocId())&&Objects.equals(original.getTitle(),n.getTitle())&&Objects.equals(original.getContent(),n.getContent())&&Objects.equals(original.getTags(),n.getTags())&&Objects.equals(original.getSourceTitle(),n.getSourceTitle())&&Objects.equals(original.getStatus(),n.getStatus())&&Objects.equals(original.getChapterId(),n.getChapterId())&&Objects.equals(original.getPathId(),n.getPathId()))return original;
  persist(n,dto.getRevision(),0,"UPDATE");return n;
 }
 private void requireRevision(Long revision,LearningNote n) {
  if(revision==null||revision<1)throw new BusinessException(400,"必须提交笔记修订号");
  if(!revision.equals(n.getRevision()))throw new BusinessException(409,"这篇笔记已在其他位置修改");
 }
 private void persist(LearningNote n,Long expected,int deleted,String reason) {
  LearningNote before=detail(n.getId(),n.getUserId(),true);
  if(notes.compareAndSet(n,expected,deleted)!=1)throw new BusinessException(409,"这篇笔记已在其他位置修改");
  if(versions.selectCount(new LambdaQueryWrapper<NoteVersion>().eq(NoteVersion::getNoteId,n.getId()).eq(NoteVersion::getRevision,expected))==0)snapshot(before,"UPGRADE_BASELINE");
  n.setRevision(expected+1);n.setUpdateTime(LocalDateTime.now());syncTags(n);snapshot(n,reason);
 }
 @Transactional public LearningNote delete(Long id,Long user,Long revision) {
  LearningNote n=detail(id,user,false);requireRevision(revision,n);n.setDeleted(1);persist(n,revision,0,"DELETE");return n;
 }
 @Transactional public LearningNote restore(Long id,Long user,Long revision) {
  LearningNote n=detail(id,user,true);requireRevision(revision,n);if(n.getDeleted()!=1)throw new BusinessException(409,"笔记不在回收站");n.setDeleted(0);persist(n,revision,1,"RESTORE");return n;
 }
 public List<NoteVersion> history(Long id,Long user) {
  detail(id,user,true);return versions.selectList(new LambdaQueryWrapper<NoteVersion>().eq(NoteVersion::getNoteId,id).eq(NoteVersion::getUserId,user).orderByDesc(NoteVersion::getRevision));
 }
 @Transactional public LearningNote restoreVersion(Long id,Long user,Long target,Long revision) {
  LearningNote n=detail(id,user,false);requireRevision(revision,n);
  NoteVersion v=versions.selectOne(new LambdaQueryWrapper<NoteVersion>().eq(NoteVersion::getNoteId,id).eq(NoteVersion::getUserId,user).eq(NoteVersion::getRevision,target));
  if(v==null)throw new BusinessException(404,"历史版本不存在");
  var dto=new LearningNoteDTO();dto.setTitle(v.getTitle());dto.setContent(v.getContent());dto.setTags(v.getTags());dto.setSourceTitle(v.getSourceTitle());dto.setStatus(v.getStatus());dto.setChapterId(v.getChapterId());dto.setPathId(v.getPathId());validate(dto,user);
  dto.setFavorite(v.getFavorite());
  dto.setFolderId(v.getFolderId()!=null&&jdbc.queryForObject("SELECT COUNT(*) FROM learning_note_folder WHERE id=? AND user_id=?",Long.class,v.getFolderId(),user)>0?v.getFolderId():null);
  dto.setSourceDocId(v.getSourceDocId());organization(n,dto,user);
  n.setChapterId(v.getChapterId());n.setPathId(v.getPathId());fill(n,dto);persist(n,revision,0,"VERSION_RESTORE");return n;
 }
 private void snapshot(LearningNote n,String reason) {
  NoteVersion v=new NoteVersion();v.setFolderId(n.getFolderId());v.setFavorite(n.getFavorite());v.setSourceDocId(n.getSourceDocId());v.setNoteId(n.getId());v.setUserId(n.getUserId());v.setRevision(n.getRevision());v.setTitle(n.getTitle());v.setContent(n.getContent());v.setTags(n.getTags());v.setSourceTitle(n.getSourceTitle());v.setChapterId(n.getChapterId());v.setPathId(n.getPathId());v.setStatus(n.getStatus());v.setNoteDeleted(n.getDeleted());v.setReason(reason);v.setCreateTime(LocalDateTime.now());versions.insert(v);
 }
 @Transactional public LearningNote importLegacy(Long user,LearningNoteDTO dto) {
  validate(dto,user);if(dto.getLegacyKey()==null||dto.getLegacyKey().isBlank())throw new BusinessException(400,"缺少迁移来源");
  String key;
  try {var source=java.util.Arrays.asList(dto.getLegacyKey(),dto.getTitle(),Objects.requireNonNullElse(dto.getContent(),""),Objects.requireNonNullElse(dto.getTags(),""),Objects.requireNonNullElse(dto.getSourceTitle(),""),Objects.requireNonNullElse(dto.getStatus(),"SAVED"),dto.getChapterId(),dto.getPathId());key=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(source)));}catch(java.security.NoSuchAlgorithmException|com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
  LearningNote previous=notes.imported(user,key);if(previous!=null)return previous;
  LearningNote n=new LearningNote();n.setUserId(user);organization(n,dto,user);fill(n,dto);n.setImportKey(key);n.setRevision(1L);n.setDeleted(0);notes.insert(n);syncTags(n);snapshot(n,"LEGACY_IMPORT");return n;
 }
}
