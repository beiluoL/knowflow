package com.knowflow.interview.service.career;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

import com.knowflow.interview.career.CareerMatcher;
/** 私人简历与岗位共用版本存储，所有资源访问先验证本人归属。 */
@Service
@RequiredArgsConstructor
public class CareerRecordService {
 private final JdbcTemplate jdbc;
 private final ObjectMapper json;
 private static final Set<String> SECTIONS=Set.of(
  "basics","intention","education","experience","projects","skills","certificates","selfIntro",
  "custom","requiredSkills","preferredSkills","responsibilities","experienceRequirement","educationRequirement","domain","other");
 public record Edit(Integer expectedVersion,String title,String source,Map<String,List<CareerMatcher.Claim>> sections){}
 private Map<String,Object> own(Long user,long id){
  if(user==null)throw new BusinessException(401,"请先登录");
  var rows=jdbc.queryForList("SELECT id,kind,title,current_version,is_default FROM interview_career_record WHERE id=? AND user_id=? AND deleted=0",id,user);
  if(rows.isEmpty())throw new BusinessException(404,"个人资料不存在");return rows.get(0);
 }
 private void validate(Edit edit){
  if(edit==null||edit.title()==null||edit.title().isBlank()||edit.title().length()>160||edit.source()==null||edit.source().isBlank()||edit.source().length()>50000||edit.sections()==null||!SECTIONS.containsAll(edit.sections().keySet()))throw new BusinessException(400,"标题、正文或结构化资料无效");
  try{for(var claims:edit.sections().values())CareerMatcher.validate(claims,edit.source());}
  catch(IllegalArgumentException invalid){throw new BusinessException(400,invalid.getMessage());}
 }
 @Transactional public Map<String,Object> create(Long user,String kind,Edit edit){
  if(user==null)throw new BusinessException(401,"请先登录");
  if(!Set.of("RESUME","JOB").contains(kind))throw new BusinessException(400,"资料类型无效");validate(edit);
  var key=new GeneratedKeyHolder();
  jdbc.update(connection->{var statement=connection.prepareStatement("INSERT INTO interview_career_record(user_id,kind,title) VALUES(?,?,?)",new String[]{"id"});statement.setLong(1,user);statement.setString(2,kind);statement.setString(3,edit.title());return statement;},key);
  long id=Objects.requireNonNull(key.getKey()).longValue();insertVersion(id,1,edit);return detail(user,id,null);
 }
 private void insertVersion(long id,int version,Edit edit){
  try{jdbc.update("INSERT INTO interview_career_version(record_id,version_no,title,source_text,sections_json) VALUES(?,?,?,?,?)",id,version,edit.title(),edit.source(),json.writeValueAsString(edit.sections()));}
  catch(com.fasterxml.jackson.core.JsonProcessingException invalid){throw new BusinessException(400,"结构化资料无法保存");}
 }
 @Transactional public Map<String,Object> update(Long user,long id,Edit edit){
  own(user,id);validate(edit);
  if(edit.expectedVersion()==null||edit.expectedVersion()<1)throw new BusinessException(400,"缺少原版本号");
  if(jdbc.update("UPDATE interview_career_record SET current_version=current_version+1,title=?,update_time=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND deleted=0 AND current_version=?",edit.title(),id,user,edit.expectedVersion())!=1)throw new BusinessException(409,"资料已被其他编辑更新，请刷新");
  insertVersion(id,edit.expectedVersion()+1,edit);return detail(user,id,null);
 }
 public List<Map<String,Object>> list(Long user,String kind){
  if(user==null)throw new BusinessException(401,"请先登录");
  return jdbc.queryForList("SELECT id FROM interview_career_record WHERE user_id=? AND kind=? AND deleted=0 ORDER BY id DESC LIMIT 100",user,kind).stream().map(row->detail(user,((Number)row.get("id")).longValue(),null)).toList();
 }
 public Map<String,Object> detail(Long user,long id,Integer version){
  var record=own(user,id);int selected=version==null?((Number)record.get("current_version")).intValue():version;
  var rows=jdbc.queryForList("SELECT version_no,title,source_text,sections_json,confirmed,create_time FROM interview_career_version WHERE record_id=? AND version_no=?",id,selected);
  if(rows.isEmpty())throw new BusinessException(404,"资料版本不存在");var row=rows.get(0);
  var result=new LinkedHashMap<String,Object>();result.put("id",id);result.put("kind",record.get("kind"));result.put("title",row.get("title"));result.put("currentVersion",record.get("current_version"));result.put("isDefault",((Number)record.get("is_default")).intValue()==1);result.put("version",selected);result.put("source",row.get("source_text"));result.put("confirmed",((Number)row.get("confirmed")).intValue()==1);
  try{result.put("sections",json.readValue(String.valueOf(row.get("sections_json")),new TypeReference<Map<String,List<CareerMatcher.Claim>>>(){}));}
  catch(com.fasterxml.jackson.core.JsonProcessingException invalid){throw new BusinessException(500,"资料版本无法读取");}
  return result;
 }
 public List<Map<String,Object>> versions(Long user,long id){own(user,id);return jdbc.queryForList("SELECT version_no,confirmed,create_time FROM interview_career_version WHERE record_id=? ORDER BY version_no DESC",id).stream().map(row->Map.<String,Object>of("version",row.get("version_no"),"confirmed",((Number)row.get("confirmed")).intValue()==1,"createdAt",String.valueOf(row.get("create_time")))).toList();}
 @Transactional public Map<String,Object> confirm(Long user,long id,int version){own(user,id);
  if(CareerUploadService.NEEDS_TEXT_SOURCE.equals(detail(user,id,version).get("source")))throw new BusinessException(422,"原文件未提取正文，请先手动粘贴并校对");
  if(jdbc.update("UPDATE interview_career_version SET confirmed=1,update_time=CURRENT_TIMESTAMP WHERE record_id=? AND version_no=?",id,version)!=1)throw new BusinessException(404,"资料版本不存在");return detail(user,id,version);}
 @Transactional public Map<String,Object> setDefault(Long user,long id){
  jdbc.queryForList("SELECT id FROM sys_user WHERE id=? FOR UPDATE",user);
  var record=detail(user,id,null);
  if(!"RESUME".equals(record.get("kind"))||!Boolean.TRUE.equals(record.get("confirmed")))throw new BusinessException(409,"仅可将最新已确认简历设为默认");
  jdbc.update("UPDATE interview_career_record SET is_default=0,update_time=CURRENT_TIMESTAMP WHERE user_id=? AND kind='RESUME' AND is_default=1",user);
  jdbc.update("UPDATE interview_career_record SET is_default=1,update_time=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND deleted=0",id,user);
  return detail(user,id,null);
 }
 @Transactional public void remove(Long user,long id){own(user,id);if(jdbc.update("UPDATE interview_career_record SET deleted=1,is_default=0,update_time=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND deleted=0",id,user)!=1)throw new BusinessException(409,"资料状态已变化");jdbc.update("UPDATE interview_career_file SET content_bytes=NULL,update_time=CURRENT_TIMESTAMP WHERE record_id=? AND user_id=?",id,user); }
}
