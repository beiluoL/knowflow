package com.knowflow.interview.radar;

import com.knowflow.entity.*;
import com.knowflow.mapper.*;
import com.knowflow.security.DocumentAccessPolicy;
import com.knowflow.seed.InterviewRadarImportService;
import com.knowflow.exception.BusinessException;
import com.knowflow.interview.core.InterviewQuestionBank.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.*;

/** Owner-private reference practice. Pending content never becomes an approved public bank. */
@Component @RequiredArgsConstructor
public class RadarInterviewBank {
 public static final String TRACK="RADAR_PRIVATE";
 private static final Pattern NUMBER=Pattern.compile("(?:ORIG-)?Q\\d{3}(?:-\\d{2})?");
 private static final Pattern SOURCE=Pattern.compile("(?m)^## 来源：(.+)$");
 private final JdbcTemplate jdbc;
 private final RadarSchema schema;
 private final com.fasterxml.jackson.databind.ObjectMapper json;
 private final SysUserMapper users;
 private final QuizQuestionMapper questions;
 private final DocDocumentMapper documents;
 private final LearningChapterMapper chapters;
 private final DocumentAccessPolicy access;
 public record Question(long questionId,String sourceQuestionId,String sourceNamespace,String title,String domain,Integer difficulty,String priority,Long chapterId,String reviewStatus,boolean eligible,List<String> warnings,List<String> sourceFiles){}
 public record Material(Question metadata,QuizQuestion question,DocDocument document,String prompt){}
 private void actor(Long actor){if(actor==null||users.selectById(actor)==null)throw new BusinessException(401,"请先登录");}
 public List<Question> catalog(Long actor){actor(actor);return materials(actor).stream().map(Material::metadata).toList();}
 public Material material(Long actor,long id){actor(actor);return materials(actor).stream().filter(m->m.metadata().questionId()==id).findFirst().orElseThrow(()->new BusinessException(404,"本人雷达题目不存在或来源已不可读"));}
 public static String domainTrack(String domain){return "RADAR_D_"+Base64.getUrlEncoder().withoutPadding().encodeToString(domain.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 public List<Track> tracks(Long actor){
  if(actor==null)return List.of();
  var available=materials(actor).stream().filter(m->m.metadata().eligible()).toList();
  if(available.isEmpty())return List.of();
  List<Track> result=new ArrayList<>();result.add(track(TRACK,"Java + AI 面试雷达 · 本人参考训练",available));
  var groups=new LinkedHashMap<String,List<Material>>();for(var m:available)groups.computeIfAbsent(m.metadata().domain(),k->new ArrayList<>()).add(m);
  groups.forEach((domain,entries)->result.add(track(domainTrack(domain),"雷达专项 · "+domain,entries)));
  Map<String,Double> recent=new HashMap<>();Set<String> seen=new HashSet<>();
  for(var row:jdbc.queryForList("SELECT t.question_id,t.evaluation_json FROM mock_interview_v2_turn t JOIN interview_radar_session_binding b ON b.session_id=t.session_id WHERE t.user_id=? AND b.user_id=? AND t.depth=0 ORDER BY t.id DESC",actor,actor)){
   String id=row.get("question_id").toString();if(!seen.add(id)||row.get("evaluation_json")==null)continue;
   try{var e=json.readTree(row.get("evaluation_json").toString());if(RadarRubric.VERSION.equals(e.path("rubricVersion").asText())&&e.path("radarGrade").path("score").isNumber())recent.put(id,e.path("radarGrade").path("score").asDouble());}
   catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new BusinessException(500,"雷达评价记录损坏，请保留记录后检查");}
  }
  var weak=available.stream().filter(m->recent.containsKey("RADAR-"+m.metadata().questionId())&&recent.get("RADAR-"+m.metadata().questionId())<8.5).sorted(Comparator.comparingDouble(m->recent.get("RADAR-"+m.metadata().questionId()))).toList();
  if(!weak.isEmpty())result.add(track("RADAR_WEAK","雷达薄弱题 · 真实低分记录",weak));return List.copyOf(result);
 }
 private Track track(String code,String name,List<Material> entries){
  var items=entries.stream().map(m->new SeedQuestion("RADAR-"+m.metadata().questionId(),m.prompt(),List.of(m.question().getAnswer()),List.of(),new Rubric("技术准确性0–4、原理理解0–3、工程应用0–2、表达0–1；没有回答不评分","只依据真实回答和来源答案；未提及的正确点不能算已掌握","完整解释概念、因果、工程与边界；来源为本人待审资料，AI评价不是客观掌握度"))).toList();
  return new Track(code,name,"私人来源保留待审状态；身份冲突或题干不明确的材料暂停训练。",items);
 }
 private List<Material> materials(Long actor){
  String pack=InterviewRadarImportService.KEY+actor;
  var mappings=jdbc.queryForList("SELECT resource_key,resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_type='QUIZ' AND resource_key LIKE 'question/%' ORDER BY resource_key",pack);
  List<Material> result=new ArrayList<>();
  for(var mapping:mappings){
   Long id=((Number)mapping.get("resource_id")).longValue();var q=questions.selectById(id);
   if(q==null||!actor.equals(q.getCreatorId())||q.getDocId()==null||!QuizQuestion.TYPE_SHORT_ANSWER.equals(q.getQuestionType()))continue;
   var doc=documents.selectById(q.getDocId());if(doc==null||!access.canRead(doc,actor))continue;
   String suffix=mapping.get("resource_key").toString().substring("question/".length());
   var cids=jdbc.queryForList("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key=? AND resource_type='CHAPTER'",Long.class,pack,"chapter/"+suffix);
   Long chapter=cids.isEmpty()?null:cids.get(0);if(chapter!=null&&chapters.selectById(chapter)==null)chapter=null;
   List<String> warnings=new ArrayList<>(),files=new ArrayList<>();boolean conflict=false;
   String source=Objects.toString(doc.getSourcePath(),"");int split=source.lastIndexOf(':');String namespace=split<0?"":source.substring(0,split),number=split<0?"":source.substring(split+1);
   if(!NUMBER.matcher(number).matches()){warnings.add("缺少可靠原始题号");conflict=true;}
   Matcher matcher=SOURCE.matcher(Objects.toString(doc.getContent(),""));
   while(matcher.find()){
    String file=matcher.group(1).trim();files.add(file);var fileNumber=NUMBER.matcher(file.substring(file.lastIndexOf('/')+1));
    if(fileNumber.find()){
     String original=fileNumber.group();
     if(!base(original).equals(base(number))){warnings.add("文件名题号"+original+"与正文身份"+number+"冲突："+file);conflict=true;}
     else if(!original.equals(number))warnings.add("子文件号"+original+"保留为来源别名，归入正文题号"+number);
    }
   }
   String title=Objects.toString(q.getTitle(),number),prompt=Objects.toString(q.getContent(),title);
   String substantive=title.replaceAll("(?:ORIG-)?Q\\d{3}(?:-\\d{2})?|Java\\s*\\+\\s*AI|面试雷达|V\\d+(?:\\.\\d+)*|完整复盘|复盘|[\\p{P}\\p{Z}\\s]","");
   if(substantive.length()<3){warnings.add("题干主题不明确，需核对原始面试问题");conflict=true;}
   if(files.isEmpty()){warnings.add("缺少完整来源清单");conflict=true;}
   if(chapter==null){warnings.add("对应章节不可用");conflict=true;}
   if(q.getAnswer()==null||q.getAnswer().isBlank()){warnings.add("缺少标准答案候选");conflict=true;}
   if(!QuizQuestion.REVIEW_APPROVED.equals(q.getReviewStatus()))warnings.add("参考内容尚未技术审核；AI评分仅为训练建议");
   String body=Objects.toString(doc.getContent(),"");var priority=Pattern.compile("\\bP[012]\\b").matcher(body);
   var meta=new Question(id,number,namespace,title,domain(title,body),QuizQuestion.REVIEW_APPROVED.equals(q.getReviewStatus())?q.getDifficulty():null,priority.find()?priority.group():null,chapter,q.getReviewStatus(),!conflict,List.copyOf(warnings),List.copyOf(files));
   result.add(new Material(meta,q,doc,prompt));
  }
  result.sort(Comparator.comparing(m->m.metadata().sourceQuestionId()));return result;
 }
 private static String base(String id){return id.replaceFirst("-\\d{2}$","");}
 private static String domain(String title,String body){String named=classify(title);return named.equals("未分类")?classify(body.substring(0,Math.min(1400,body.length()))):named;}
 private static String classify(String text){
  String[][] rules={{"JVM","JVM|GC Roots|G1|垃圾回收|类加载|OOM"},{"Java 并发","并发|线程|volatile|synchronized|CAS|AQS|ThreadLocal|JUC"},{"Spring","Spring|事务传播|Transactional"},{"MySQL","MySQL|InnoDB|MVCC|SQL|数据库索引"},{"Redis","Redis|缓存穿透|缓存击穿"},{"消息队列","Kafka|RabbitMQ|消息队列"},{"分布式","分布式|微服务|Raft|Gateway|Feign"},{"RAG","RAG|向量|Embedding|检索"},{"Agent","Agent|MCP|Tool Calling|工具调用"},{"AI 工程化","Token 成本|可观测|SSE|结构化输出|模型降级|Prompt Injection|Golden Dataset"},{"大模型基础","LLM|大模型|Tokenizer|Attention|Temperature"},{"项目面试","项目|系统架构|架构设计"},{"Java 基础","HashMap|ArrayList|LinkedList|泛型|Java|集合"}};
  for(var rule:rules)if(Pattern.compile(rule[1],Pattern.CASE_INSENSITIVE).matcher(text).find())return rule[0];return "未分类";
 }
}
