package com.knowflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Immutable, explicitly confirmed public text snapshots; no private message endpoint is exposed. */
@Service @RequiredArgsConstructor
public class ChatShareService {
 private final JdbcTemplate jdbc; private final ObjectMapper json;
 private static final SecureRandom RANDOM=new SecureRandom();
 private static final Pattern UNSAFE=Pattern.compile("(?is)(https?://|data:|file:|www\\.|!\\[|<img|sk-[a-z0-9_-]{8,}|bearer\\s+[a-z0-9._-]+|(?:api[_ -]?key|password|secret|access[_ -]?token)\\s*[\\\"']?\\s*[:=]|planJSON|tool_calls)");
 private void owner(long id,long user){Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM chat_conversation WHERE id=? AND user_id=? AND deleted=0",Integer.class,id,user);if(count==null||count==0)throw new BusinessException(404,"对话不存在");}
 private static String hash(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private static void safe(String text){if(text!=null&&UNSAFE.matcher(text).find())throw new BusinessException(400,"文本包含链接、图片、内部数据或疑似凭据，无法安全公开；请先移除后重试");}
 @Transactional
 public Map<String,Object> create(long id,long user,Integer hours,Boolean confirmed){
  owner(id,user);if(!Boolean.TRUE.equals(confirmed)||hours==null||!Set.of(24,168,720).contains(hours))throw new BusinessException(400,"请确认公开纯文本范围并选择有效期");
  // Any historical RAG turn may have contaminated later summaries or context, including deleted turns.
  for (String references : jdbc.query("SELECT doc_references FROM chat_message WHERE conversation_id=?", (rs,n)->rs.getString(1), id)) {
   if(references!=null&&!references.isBlank()&&!references.trim().equals("[]"))
    throw new BusinessException(400,"该会话包含知识库关联历史，后续回复可能沿用私有上下文，无法安全公开；请使用无知识库关联的新对话");
  }
  String title=jdbc.queryForObject("SELECT title FROM chat_conversation WHERE id=? AND user_id=? AND deleted=0",String.class,id,user);safe(title);List<Map<String,Object>> messages=new ArrayList<>();List<String> redactions=new ArrayList<>();
  for(var m:jdbc.queryForList("SELECT role,content,doc_references,create_time FROM chat_message WHERE conversation_id=? AND user_id=? AND deleted=0 ORDER BY create_time,id",id,user)){
   if(!Set.of("user","assistant").contains(Objects.toString(m.get("role"), ""))){redactions.add("已排除内部消息");continue;}
   if ("assistant".equals(m.get("role")) && Objects.toString(m.get("content"), "").contains("KNOWFLOW_LEARNING_ACTION")) {redactions.add("已排除内部学习行动数据");continue;}
   safe(Objects.toString(m.get("content"), ""));Map<String,Object> row=new LinkedHashMap<>();row.put("role",Objects.toString(m.get("role"), ""));row.put("content",Objects.toString(Objects.toString(m.get("content"), ""),""));row.put("createTime",m.get("create_time"));messages.add(row);
  }
  if(messages.isEmpty())throw new BusinessException(400,"没有可安全公开的文本消息");
  byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);var now=LocalDateTime.now();var expires=now.plusHours(hours);
  String snapshot;try{snapshot=json.writeValueAsString(Map.of("title",Objects.toString(title,"新对话"),"messages",messages,"redactions",redactions));}catch(Exception e){throw new IllegalStateException("Cannot encode share snapshot",e);}
  jdbc.update("INSERT INTO chat_public_share(conversation_id,user_id,token_hash,snapshot_json,created_at,expires_at,revoked) VALUES(?,?,?,?,?,?,FALSE)",id,user,hash(token),snapshot,now,expires);
  Long shareId=jdbc.queryForObject("SELECT id FROM chat_public_share WHERE token_hash=?",Long.class,hash(token));
  return Map.of("id",shareId,"urlPath","/share/chat/"+token,"createdAt",now,"expiresAt",expires,"redactions",redactions);
 }
 public List<Map<String,Object>> list(long id,long user){owner(id,user);return jdbc.query("SELECT id,created_at,expires_at,revoked FROM chat_public_share WHERE conversation_id=? AND user_id=? ORDER BY id DESC",(rs,n)->Map.of("id",rs.getLong(1),"createdAt",rs.getTimestamp(2).toLocalDateTime(),"expiresAt",rs.getTimestamp(3).toLocalDateTime(),"revoked",rs.getBoolean(4)),id,user);}
 public void revoke(long id,long user,long share){owner(id,user);int count=jdbc.update("UPDATE chat_public_share SET revoked=TRUE WHERE id=? AND conversation_id=? AND user_id=?",share,id,user);if(count==0)throw missing();}
 public Map<String,Object> read(String token){
  if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw missing();
  var rows=jdbc.queryForList("SELECT s.snapshot_json,s.created_at,s.expires_at FROM chat_public_share s JOIN chat_conversation c ON c.id=s.conversation_id AND c.user_id=s.user_id WHERE s.token_hash=? AND s.revoked=FALSE AND s.expires_at>? AND c.deleted=0 AND NOT EXISTS (SELECT 1 FROM chat_message m WHERE m.conversation_id=c.id AND m.doc_references IS NOT NULL AND TRIM(m.doc_references)<>'' AND TRIM(m.doc_references)<>'[]')",hash(token),LocalDateTime.now());
  if(rows.isEmpty())throw missing();var row=rows.get(0);
  try{Map<String,Object> result=json.readValue(row.get("snapshot_json").toString(),new TypeReference<>(){});result.put("createdAt",row.get("created_at"));result.put("expiresAt",row.get("expires_at"));return result;}catch(Exception e){throw new IllegalStateException("Invalid share snapshot",e);}
 }
 private static BusinessException missing(){return new BusinessException(404,"分享不存在或已失效");}
}
