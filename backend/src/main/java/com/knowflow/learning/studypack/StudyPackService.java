package com.knowflow.learning.studypack;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.dto.*;
import com.knowflow.entity.*;
import com.knowflow.exception.BusinessException;
import com.knowflow.learning.actions.LearningWriteGuard;
import com.knowflow.learning.privatequiz.*;
import com.knowflow.learning.tutor.TutorContextService;
import com.knowflow.mapper.*;
import com.knowflow.service.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.*;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;

/** Durable orchestration and resource references. Existing content stores remain authoritative. */
@Service @RequiredArgsConstructor
@ConditionalOnProperty(name={"knowflow.product.study-pack.enabled","knowflow.product.private-quiz.enabled","knowflow.product.tutor.enabled"},havingValue="true")
public class StudyPackService {
 private final JdbcTemplate sql;
 private final ObjectMapper json;
 private final LearningWriteGuard guard;
 private final PlatformTransactionManager manager;
 private final DocumentTextExtractor parser;
 private final DocCategoryMapper categories;
 private final DocDocumentMapper documents;
 private final DocService docs;
 private final NoteService notes;
 private final LearningContentService generator;
 private final LearningService learning;
 private final PrivateQuizService quizzes;
 private final TutorContextService tutors;
 private final com.knowflow.security.DocumentAccessPolicy access;
 private static final List<String> KINDS=List.of("PARSE","NOTE","CARDS","QUIZ","TUTOR");
 private static final String COLUMNS="id,owner_id,source_hash,file_name,state,revision,doc_id,parsed_hash,create_time,update_time";
 private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(20),r->{var t=new Thread(r,"study-pack-worker");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 @PostConstruct void initialize(){sql.execute((ConnectionCallback<Void>)c->{StudyPackSchema.migrate(c);return null;});
  // A stopped process cannot claim that an interrupted paid model call never ran.
  sql.update("UPDATE study_pack_step SET state='FAILED',error_message='进程中断，生成结果未知；明确重试可能再次调用模型' WHERE state='RUNNING'");
  for(long job:sql.queryForList("SELECT id FROM study_pack_job WHERE state IN ('RUNNING','QUEUED')",Long.class)){
   var interrupted=sql.queryForList("SELECT kind FROM study_pack_step WHERE job_id=? AND state='FAILED'",String.class,job);
   if(interrupted.isEmpty())for(String kind:KINDS)if(step(job,kind).get("state").equals("PENDING")){sql.update("UPDATE study_pack_step SET state='FAILED',error_message='进程在排队期间中断，请明确重试' WHERE job_id=? AND kind=?",job,kind);break;}
   var states=sql.queryForList("SELECT state FROM study_pack_step WHERE job_id=?",String.class,job);
   String aggregate=states.size()==5&&states.stream().allMatch("SUCCEEDED"::equals)?"DRAFT_READY":states.contains("SUCCEEDED")?"PARTIAL_READY":"FAILED";
   sql.update("UPDATE study_pack_job SET state=?,update_time=CURRENT_TIMESTAMP WHERE id=?",aggregate,job);event(job,"JOB",aggregate.equals("DRAFT_READY")?"RECOVERED":"INTERRUPTED",null,null);
  }
 }
 @PreDestroy void close(){workers.shutdownNow();}
 private <T>T tx(long user,java.util.function.Supplier<T> work){var t=new TransactionTemplate(manager);t.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);return t.execute(s->{guard.lock(user);return work.get();});}
 private String textHash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Objects.requireNonNullElse(value,"").getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
 private <T>T decode(Object raw,TypeReference<T> type){try{return json.readValue(raw.toString(),type);}catch(Exception e){throw new BusinessException(409,"学习包产物数据不可解析");}}
 private long num(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
 private Map<String,Object> owned(long id,long user,boolean lock){var rows=sql.queryForList("SELECT "+COLUMNS+" FROM study_pack_job WHERE id=? AND owner_id=?"+(lock?" FOR UPDATE":""),id,user);if(rows.isEmpty())throw new BusinessException(404,"学习包不存在");var job=rows.get(0);if(job.get("doc_id")!=null){if(lock)sql.queryForList("SELECT id FROM doc_document WHERE id=? FOR UPDATE",num(job,"doc_id"));var doc=documents.selectById(num(job,"doc_id"));access.requireRead(doc,user);if(job.get("parsed_hash")==null||!job.get("parsed_hash").equals(textHash(doc.getContent())))throw new BusinessException(409,"资料正文已修改或缺少来源指纹，请上传新版本，原产物仍保留");}return job;}
 private Map<String,Object> step(long id,String kind){return sql.queryForMap("SELECT * FROM study_pack_step WHERE job_id=? AND kind=?",id,kind);}
 private Long ref(long id,String kind){Object value=step(id,kind).get("resource_id");return value==null?null:((Number)value).longValue();}
 private void event(long id,String kind,String state,Long ref,Long elapsed){sql.update("INSERT INTO study_pack_event(job_id,kind,state,resource_id,elapsed_ms,create_time) VALUES(?,?,?,?,?,CURRENT_TIMESTAMP)",id,kind,state,ref,elapsed);}
 public Map<String,Object> upload(long user,MultipartFile file){
  if(file==null||file.isEmpty()||file.getOriginalFilename()==null)throw new BusinessException(400,"请选择课程资料");
  String name=file.getOriginalFilename().replace('\\','/');name=name.substring(name.lastIndexOf('/')+1);if(name.length()>200)throw new BusinessException(400,"资料文件名过长");String ext=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
  if(!Set.of("txt","md","markdown","pdf").contains(ext))throw new BusinessException(400,"首批仅支持TXT、Markdown和小型文本PDF");
  long limit=ext.equals("pdf")?5L*1024*1024:256L*1024;if(file.getSize()>limit)throw new BusinessException(413,"资料超过首批大小限制（文本256KiB / PDF5MiB）");
  byte[] bytes;String hash;try{bytes=file.getBytes();hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new BusinessException(400,"资料读取失败");}
  if(ext.equals("pdf")&&(bytes.length<5||!new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")))throw new BusinessException(400,"PDF文件签名无效");
  String filename=name;
  var result=tx(user,()->{
   var existing=sql.queryForList("SELECT "+COLUMNS+" FROM study_pack_job WHERE owner_id=? AND source_hash=?",user,hash);if(!existing.isEmpty())return Map.<String,Object>of("id",existing.get(0).get("id"),"reused",true);
   if(sql.queryForObject("SELECT COUNT(*) FROM study_pack_job WHERE owner_id=? AND state IN ('QUEUED','RUNNING')",Integer.class,user)>0)throw new BusinessException(429,"请等待当前资料生成结束后再上传");
   sql.update("INSERT INTO study_pack_job(owner_id,source_hash,file_name,source_bytes,state,revision,create_time,update_time) VALUES(?,?,?,?,'QUEUED',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",user,hash,filename,bytes);
   long id=sql.queryForObject("SELECT id FROM study_pack_job WHERE owner_id=? AND source_hash=?",Long.class,user,hash);for(String kind:KINDS)sql.update("INSERT INTO study_pack_step(job_id,kind,state,run_count) VALUES(?,?,'PENDING',0)",id,kind);event(id,"JOB","QUEUED",null,null);return Map.<String,Object>of("id",id,"reused",false);
  });
  if(Boolean.FALSE.equals(result.get("reused")))schedule(((Number)result.get("id")).longValue(),user);return detail(((Number)result.get("id")).longValue(),user);
 }
 private void schedule(long id,long user){var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(SecurityContextHolder.getContext().getAuthentication());try{workers.execute(new DelegatingSecurityContextRunnable(()->run(id,user),context));}catch(RejectedExecutionException e){sql.update("UPDATE study_pack_job SET state='FAILED' WHERE id=?",id);sql.update("UPDATE study_pack_step SET state='FAILED',error_message='生成队列已满，请重试' WHERE job_id=? AND kind='PARSE' AND state='PENDING'",id);}}
 private void run(long id,long user){
  try{tx(user,()->{var job=owned(id,user,true);if(!Set.of("QUEUED","PARTIAL_READY").contains(job.get("state").toString()))return null;sql.update("UPDATE study_pack_job SET state='RUNNING',update_time=CURRENT_TIMESTAMP WHERE id=?",id);return null;});
   for(String kind:KINDS){var s=step(id,kind);if(!Set.of("PENDING","BLOCKED").contains(s.get("state").toString()))continue;
    if(!kind.equals("PARSE")&&ref(id,"PARSE")==null||Set.of("QUIZ","TUTOR").contains(kind)&&ref(id,"NOTE")==null){sql.update("UPDATE study_pack_step SET state='BLOCKED',error_message='等待来源或笔记步骤完成' WHERE job_id=? AND kind=?",id,kind);continue;}
    boolean committedArtifact=kind.equals("CARDS")?s.get("draft_json")!=null:s.get("resource_id")!=null;
    if(committedArtifact){tx(user,()->{owned(id,user,true);sql.update("UPDATE study_pack_step SET state='SUCCEEDED',error_message=NULL WHERE job_id=? AND kind=?",id,kind);event(id,kind,"RECOVERED",ref(id,kind),null);return null;});continue;}
    long started=System.nanoTime();tx(user,()->{owned(id,user,true);sql.update("UPDATE study_pack_step SET state='RUNNING',run_count=run_count+1,error_message=NULL WHERE job_id=? AND kind=?",id,kind);event(id,kind,"RUNNING",null,null);return null;});
    try{generate(id,user,kind);long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);tx(user,()->{owned(id,user,true);sql.update("UPDATE study_pack_step SET state='SUCCEEDED',elapsed_ms=? WHERE job_id=? AND kind=?",elapsed,id,kind);event(id,kind,"SUCCEEDED",ref(id,kind),elapsed);return null;});}
    catch(Exception e){String message=e instanceof BusinessException?e.getMessage():"生成失败，请检查AI配置或来源后重试该步骤";long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);sql.update("UPDATE study_pack_step SET state='FAILED',error_message=?,elapsed_ms=? WHERE job_id=? AND kind=?",message.substring(0,Math.min(message.length(),500)),elapsed,id,kind);event(id,kind,"FAILED",ref(id,kind),elapsed);}
   }
   tx(user,()->{var states=sql.queryForList("SELECT state FROM study_pack_step WHERE job_id=?",String.class,id);String state=states.stream().allMatch("SUCCEEDED"::equals)?"DRAFT_READY":states.contains("SUCCEEDED")?"PARTIAL_READY":"FAILED";sql.update("UPDATE study_pack_job SET state=?,update_time=CURRENT_TIMESTAMP WHERE id=?",state,id);event(id,"JOB",state,null,null);return null;});
  }catch(Exception e){sql.update("UPDATE study_pack_step SET state='FAILED',error_message='来源权限或任务执行中断，请核定后重试' WHERE job_id=? AND state='RUNNING'",id);sql.update("UPDATE study_pack_job SET state='FAILED',update_time=CURRENT_TIMESTAMP WHERE id=? AND owner_id=?",id,user);}
 }
 private void generate(long id,long user,String kind)throws Exception{
  var job=owned(id,user,false);
  if(kind.equals("PARSE")){
   byte[] bytes=sql.queryForObject("SELECT source_bytes FROM study_pack_job WHERE id=? AND owner_id=?",byte[].class,id,user);String name=job.get("file_name").toString();
   if(name.toLowerCase(Locale.ROOT).endsWith(".pdf"))try(var pdf=org.apache.pdfbox.pdmodel.PDDocument.load(bytes)){if(pdf.isEncrypted()||pdf.getNumberOfPages()>20)throw new BusinessException(400,"首批PDF仅支持未加密且不超过20页的文本资料");}
   String text=parser.extractText(new SourceFile(name,bytes));if(text==null||text.isBlank()||text.length()>20000||text.indexOf('\0')>=0)throw new BusinessException(400,"正文提取失败或超过20000字；扫描PDF/OCR不在首批范围");
   tx(user,()->{guard.lock(user);var cat=categories.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocCategory>().eq(DocCategory::getOwnerId,user).eq(DocCategory::getCode,"STUDY_PACK_"+user)).stream().findFirst().orElse(null);if(cat==null){cat=new DocCategory();cat.setOwnerId(user);cat.setCode("STUDY_PACK_"+user);cat.setName("私人学习资料");cat.setStatus(1);cat.setDocCount(0);categories.insert(cat);}
    var doc=new DocDocument();doc.setCategoryId(cat.getId());doc.setTitle(name);doc.setContent(text);doc.setStatus(1);doc.setFileName(name);doc.setFileSize((long)bytes.length);docs.saveDoc(doc);sql.update("UPDATE study_pack_job SET doc_id=?,parsed_hash=? WHERE id=?",doc.getId(),textHash(documents.selectById(doc.getId()).getContent()),id);sql.update("UPDATE study_pack_step SET resource_id=? WHERE job_id=? AND kind='PARSE'",doc.getId(),id);return null;});return;
  }
  long docId=num(job,"doc_id");
  switch(kind){
   case "NOTE"->{var draft=generator.generateFromDocument(docId,"NOTE",user);tx(user,()->{owned(id,user,true);var dto=new LearningNoteDTO();dto.setTitle(draft.get("title").toString());dto.setContent(draft.get("content").toString());dto.setSourceDocId(docId);dto.setStatus("DRAFT");dto.setSourceTitle(job.get("file_name").toString());var note=notes.create(user,dto);sql.update("UPDATE study_pack_step SET resource_id=?,draft_json=? WHERE job_id=? AND kind='NOTE'",note.getId(),encode(Map.of("chapters",draft.get("chapters"),"promptVersion","SOURCE_NOTE_V1")),id);return null;});}
   case "CARDS"->{var draft=generator.generateFromDocument(docId,"CARDS",user);tx(user,()->{owned(id,user,true);sql.update("UPDATE study_pack_step SET draft_json=? WHERE job_id=? AND kind='CARDS'",encode(draft.get("flashcards")),id);return null;});}
   case "QUIZ"->{long note=ref(id,"NOTE");var n=notes.detail(note,user,false);var quiz=quizzes.preview(user,new PrivateQuizRequests.Preview(note,n.getRevision(),"study-pack-"+id+"-note-r"+n.getRevision()));tx(user,()->{owned(id,user,true);sql.update("UPDATE study_pack_step SET resource_id=? WHERE job_id=? AND kind='QUIZ'",quiz.get("id"),id);return null;});}
   case "TUTOR"->{var tutor=tutors.create(ref(id,"NOTE"),user,"SOCRATIC");tx(user,()->{owned(id,user,true);sql.update("UPDATE study_pack_step SET resource_id=? WHERE job_id=? AND kind='TUTOR'",tutor.get("conversationId"),id);return null;});}
   default->throw new BusinessException(400,"学习包步骤无效");
  }
 }
 public List<Map<String,Object>> list(long user){return sql.queryForList("SELECT "+COLUMNS+" FROM study_pack_job WHERE owner_id=? ORDER BY id DESC LIMIT 50",user).stream().map(this::camel).toList();}
 public Map<String,Object> detail(long id,long user){var job=new LinkedHashMap<>(owned(id,user,false));var steps=sql.queryForList("SELECT kind,state,resource_id,error_message,run_count,elapsed_ms FROM study_pack_step WHERE job_id=?",id);steps.sort(Comparator.comparingInt(s->KINDS.indexOf(s.get("kind").toString())));job.put("steps",steps.stream().map(this::camel).toList());job.put("events",sql.queryForList("SELECT id,kind,state,resource_id,elapsed_ms,create_time FROM study_pack_event WHERE job_id=? ORDER BY id",id).stream().map(this::camel).toList());
  Long note=ref(id,"NOTE");if(note!=null){job.put("note",notes.detail(note,user,false));Object meta=step(id,"NOTE").get("draft_json");if(meta!=null)job.put("noteOutline",decode(meta,new TypeReference<Map<String,Object>>(){}));}
  Object cards=step(id,"CARDS").get("draft_json");job.put("cardDrafts",cards==null?List.of():decode(cards,new TypeReference<List<Map<String,Object>>>(){}));Object ids=step(id,"CARDS").get("resource_json");job.put("flashcardIds",ids==null?List.of():decode(ids,new TypeReference<List<Long>>(){}));
  job.put("quizId",ref(id,"QUIZ"));job.put("conversationId",ref(id,"TUTOR"));return camel(job);
 }
 private Map<String,Object> camel(Map<String,Object> raw){var out=new LinkedHashMap<String,Object>();for(var entry:raw.entrySet()){String key=entry.getKey();if(key.equals(key.toUpperCase(Locale.ROOT))||key.contains("_"))key=key.toLowerCase(Locale.ROOT);StringBuilder name=new StringBuilder();boolean upper=false;for(char c:key.toCharArray()){if(c=='_'){upper=true;continue;}name.append(upper?Character.toUpperCase(c):c);upper=false;}out.put(name.toString(),entry.getValue());}return out;}
 public Map<String,Object> retry(long id,long user,String kind){if(!KINDS.contains(kind))throw new BusinessException(400,"重试步骤无效");tx(user,()->{var job=owned(id,user,true);if(Set.of("QUEUED","RUNNING","READY").contains(job.get("state").toString()))throw new BusinessException(409,"当前学习包不能重试");var s=step(id,kind);if(!Set.of("FAILED","BLOCKED").contains(s.get("state").toString())){
   boolean staleQuiz=false;
   if(kind.equals("QUIZ")&&s.get("resource_id")!=null){var q=quizzes.detail(user,((Number)s.get("resource_id")).longValue());staleQuiz=((Number)q.get("sourceRevision")).longValue()!=notes.detail(ref(id,"NOTE"),user,false).getRevision();}
   if(!staleQuiz)throw new BusinessException(409,"成功产物不会重复生成");
   event(id,kind,"SOURCE_SUPERSEDED",ref(id,kind),null);sql.update("UPDATE study_pack_step SET resource_id=NULL WHERE job_id=? AND kind='QUIZ'",id);
  }sql.update("UPDATE study_pack_step SET state='PENDING',error_message=NULL WHERE job_id=? AND kind=?",id,kind);sql.update("UPDATE study_pack_job SET state='QUEUED',update_time=CURRENT_TIMESTAMP WHERE id=?",id);event(id,kind,"RETRY_REQUESTED",ref(id,kind),null);return null;});schedule(id,user);return detail(id,user);}
 public Map<String,Object> saveCards(long id,long user,long revision,List<FlashcardSaveDTO> cards){validateCards(cards);return tx(user,()->{var job=owned(id,user,true);if(!job.get("state").equals("DRAFT_READY")&&!job.get("state").equals("PARTIAL_READY"))throw new BusinessException(409,"当前学习包不可编辑草稿");if(sql.update("UPDATE study_pack_job SET revision=revision+1 WHERE id=? AND revision=?",id,revision)!=1)throw new BusinessException(409,"草稿已改变，请重新读取");sql.update("UPDATE study_pack_step SET draft_json=? WHERE job_id=? AND kind='CARDS'",encode(cards),id);return detail(id,user);});}
 private void validateCards(List<FlashcardSaveDTO> cards){if(cards==null||cards.size()!=5)throw new BusinessException(400,"需要确认5张闪卡");var fronts=new HashSet<String>();for(var c:cards)if(c==null||c.getFront()==null||c.getFront().isBlank()||c.getFront().length()>200||c.getBack()==null||c.getBack().isBlank()||c.getBack().length()>2000||!fronts.add(c.getFront().trim()))throw new BusinessException(400,"闪卡内容无效或问题重复");}
 public Map<String,Object> accept(long id,long user,long revision,List<FlashcardSaveDTO> cards){validateCards(cards);return tx(user,()->{var job=owned(id,user,true);if(job.get("state").equals("READY")){if(!encode(cards).equals(step(id,"CARDS").get("draft_json")))throw new BusinessException(409,"已确认学习包不能用不同内容覆盖");return detail(id,user);}if(!job.get("state").equals("DRAFT_READY")||num(job,"revision")!=revision)throw new BusinessException(409,"请等待全部步骤成功并读取最新草稿");var quiz=quizzes.detail(user,ref(id,"QUIZ"));if(!quiz.get("state").equals("SAVED"))throw new BusinessException(409,"请先确认保存3道私人测验");
  var cardIds=new ArrayList<Long>();for(var card:cards){var dto=new FlashcardSaveDTO();dto.setFront(card.getFront());dto.setBack(card.getBack());dto.setDifficulty(card.getDifficulty());dto.setDocId(num(job,"doc_id"));dto.setTags("学习包生成");cardIds.add(learning.createMyFlashcard(user,dto).getId());}
  sql.update("UPDATE study_pack_step SET draft_json=?,resource_json=? WHERE job_id=? AND kind='CARDS'",encode(cards),encode(cardIds),id);sql.update("UPDATE study_pack_job SET state='READY',revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",id);event(id,"JOB","READY",null,null);return detail(id,user);
 });}
 private record SourceFile(String originalFilename,byte[] bytes) implements MultipartFile {
  public String getName(){return "file";}public String getOriginalFilename(){return originalFilename;}public String getContentType(){return originalFilename.toLowerCase(Locale.ROOT).endsWith(".pdf")?"application/pdf":"text/plain";}public boolean isEmpty(){return bytes.length==0;}public long getSize(){return bytes.length;}public byte[] getBytes(){return bytes.clone();}public InputStream getInputStream(){return new ByteArrayInputStream(bytes);}public void transferTo(File dest)throws IOException{Files.write(dest.toPath(),bytes);}
 }
}
