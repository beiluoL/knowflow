package com.knowflow.interview.career;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;
import java.util.Map;

/** 私人编辑草稿与正式内容版本分离；修订号避免跨标签页静默覆盖。 */
@Service @RequiredArgsConstructor
public class ResumeDraftService {
 private final JdbcTemplate jdbc;
 private final CareerRecordService records;
 private final ObjectMapper json;
 public record Draft(Long revision, Integer baseVersion, CareerRecordService.Edit payload) {}
 private void own(Long user,long id){
  if(user==null)throw new BusinessException(401,"请先登录");
  if(id<0)throw new BusinessException(400,"简历身份无效");
  if(id>0&&!"RESUME".equals(records.detail(user,id,null).get("kind")))throw new BusinessException(400,"仅支持简历草稿");
 }
 public Map<String,Object> get(Long user,long id){
  own(user,id);
  var rows=jdbc.queryForList("SELECT revision,base_version,payload_json,update_time FROM interview_resume_draft WHERE user_id=? AND record_id=?",user,id);
  Map<String,Object> result=new LinkedHashMap<>(); result.put("revision",0);result.put("baseVersion",0);result.put("payload",null);
  if(!rows.isEmpty()){
   var row=rows.get(0);result.put("revision",row.get("revision"));result.put("baseVersion",row.get("base_version"));result.put("updatedAt",String.valueOf(row.get("update_time")));
   try{result.put("payload",row.get("payload_json")==null?null:json.readValue(String.valueOf(row.get("payload_json")),new TypeReference<Map<String,Object>>(){}));}
   catch(com.fasterxml.jackson.core.JsonProcessingException invalid){throw new BusinessException(500,"草稿无法读取");}
  }
  return result;
 }
 @Transactional public Map<String,Object> save(Long user,long id,Draft request){
  own(user,id);
  if(request==null||request.revision()==null||request.revision()<0||request.baseVersion()==null||request.baseVersion()<0||request.payload()==null)throw new BusinessException(400,"草稿参数无效");
  var p=request.payload();
  if(p.title()==null||p.title().length()>160||p.source()==null||p.source().length()>50000||p.sections()==null)throw new BusinessException(400,"草稿长度无效");
  String payload;
  try{payload=json.writeValueAsString(p);}catch(com.fasterxml.jackson.core.JsonProcessingException invalid){throw new BusinessException(400,"草稿无法保存");}
  if(payload.length()>250000)throw new BusinessException(400,"草稿过大");
  // 同一用户草稿写入串行，确保首写也受到修订号保护。
  jdbc.queryForList("SELECT id FROM sys_user WHERE id=? FOR UPDATE",user);
  var previous=get(user,id);
  if(((Number)previous.get("revision")).longValue()!=request.revision())throw new BusinessException(409,"草稿已在其他窗口更新，请重新加载后选择恢复");
  if(request.revision()==0)jdbc.update("INSERT INTO interview_resume_draft(user_id,record_id,revision,base_version,payload_json) VALUES(?,?,1,?,?)",user,id,request.baseVersion(),payload);
  else jdbc.update("UPDATE interview_resume_draft SET revision=revision+1,base_version=?,payload_json=?,update_time=CURRENT_TIMESTAMP WHERE user_id=? AND record_id=?",request.baseVersion(),payload,user,id);
  return get(user,id);
 }
 @Transactional public Map<String,Object> remove(Long user,long id,long revision){
  own(user,id);
  jdbc.queryForList("SELECT id FROM sys_user WHERE id=? FOR UPDATE",user);
  int count=jdbc.update("UPDATE interview_resume_draft SET payload_json='null',revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE user_id=? AND record_id=? AND revision=?",user,id,revision);
  if(count==0&&((Number)get(user,id).get("revision")).longValue()!=0)throw new BusinessException(409,"草稿已变化，未清除其他窗口的修改");
  return get(user,id);
 }
}
