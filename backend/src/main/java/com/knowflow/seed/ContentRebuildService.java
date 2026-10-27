package com.knowflow.seed;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.*;
import com.knowflow.mapper.*;
import com.knowflow.exception.BusinessException;
import com.knowflow.security.DocumentAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.*;

/** Versioned authored content only. Existing content and learning results are never updated. */
@Service @RequiredArgsConstructor
public class ContentRebuildService {
    public static final String KEY="java-ai-content-rebuild-v1";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final InterviewSeedService original;
    private final InterviewSeedCatalog catalog;
    private final SysUserMapper users;
    private final LearningChapterMapper chapters;
    private final LearningPathMapper paths;
    private final DocDocumentMapper docs;
    private final QuizQuestionMapper questions;
    private final LearningFlashcardMapper cards;
    private final CodeQuestionMapper exercises;
    private final ResourceKnowledgeMappingMapper mappings;
    private final KgEntityMapper knowledge;
    private final DocumentAccessPolicy access;

    private void actor(Long actor) {
        var u=actor==null?null:users.selectById(actor);
        if(u==null)throw new BusinessException(401,"未登录");
        if(!InterviewSeedAccessPolicy.allowed(u,jdbc))throw new BusinessException(403,"只能为本人启用课程内容");
    }
    private String file(String name) {
        try(var in=new ClassPathResource("seed/rebuild/"+name).getInputStream()) {
            return new String(in.readAllBytes(),StandardCharsets.UTF_8);
        } catch(java.io.IOException e) {throw new IllegalStateException("课程源不可用："+name,e);}
    }
    private List<JsonNode> units() {
        try {
            var root=json.readTree(file("manifest.json"));List<JsonNode> out=new ArrayList<>();Set<String> unitKeys=new HashSet<>();
            for(var u:root.required("units")) {
                if(!unitKeys.add(u.required("key").asText())||u.required("sources").size()<3)throw new IllegalStateException("课程身份或来源缺失");
                Set<String> seen=new HashSet<>();
                for(var q:u.required("questions")) {
                    String k=q.required("key").asText();
                    if(!seen.add(k)||q.required("answer").asText().isBlank()||q.required("explanation").asText().isBlank())throw new IllegalStateException("问题身份/解析不完整");
                    if(q.hasNonNull("parent")&&(k.equals(q.get("parent").asText())||!seen.contains(q.get("parent").asText())))throw new IllegalStateException("追问必须指向此前定义的其他问题");
                    int ki=q.required("knowledge").asInt();if(ki<1||ki>3)throw new IllegalStateException("知识引用越界");
                }
                if(file(u.required("body").asText()).isBlank()||file(u.required("experiment").asText()).isBlank())throw new IllegalStateException("正文或实验缺失");
                out.add(u);
            }
            return out;
        }catch(java.io.IOException e){throw new IllegalStateException("课程manifest损坏",e);}
    }
    private long oldId(String key) {
        var rows=jdbc.queryForList("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key=?",InterviewSeedCatalog.KEY,key);
        if(rows.size()!=1)throw new BusinessException(409,"原课程身份不存在，先启用完整v1包");
        return ((Number)rows.get(0).get("resource_id")).longValue();
    }
    private String base(JsonNode u){return "shared/"+u.required("path").asText()+"/chapter/"+u.required("chapter").asText()+"/";}
    private LearningChapter chapter(JsonNode u,Long actor) {
        var c=chapters.selectById(oldId(base(u)+"chapter"));if(c==null)throw new BusinessException(409,"原章节已删除");
        var p=paths.selectById(c.getPathId());
        if(p==null||!Integer.valueOf(1).equals(p.getStatus())||(p.getOwnerUserId()!=null&&p.getOwnerUserId()!=0&&!actor.equals(p.getOwnerUserId())))throw new BusinessException(403,"无权访问原课程");
        for(int i=1;i<=3;i++)if(knowledge.selectById(kid(u,i))==null)throw new BusinessException(409,"原知识已删除");return c;
    }
    private long kid(JsonNode u,int i){for(var p:catalog.paths())for(var c:p.required("chapters"))if(c.required("key").asText().equals(u.required("chapter").asText()))return oldId(base(u)+"knowledge/"+c.required("knowledge").get(i-1).required("key").asText());throw new BusinessException(409,"原知识身份不存在");}
    private Long find(String key){var rows=jdbc.queryForList("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key=?",KEY,key);return rows.isEmpty()?null:((Number)rows.get(0).get("resource_id")).longValue();}
    private <T extends BaseEntity> T row(String key,BaseMapper<T> mapper){var id=find(key);var r=id==null?null:mapper.selectById(id);if(r==null)throw new BusinessException(409,"课程资源缺失或已删除："+key);return r;}
    private <T extends BaseEntity> T add(String key,String type,BaseMapper<T> mapper,T r){mapper.insert(r);jdbc.update("INSERT INTO interview_seed_resource(seed_key,resource_key,resource_type,resource_id) VALUES(?,?,?,?)",KEY,key,type,r.getId());return r;}
    private void link(String type,long resource,long kid){var m=new ResourceKnowledgeMapping();m.setResourceType(type);m.setResourceId(resource);m.setKnowledgeId(kid);m.setSource("IMPORT");m.setStatus("ACCEPTED");m.setConfidence(BigDecimal.ONE);mappings.insert(m);}
    private void requireLink(String type,long resource,long kid){if(jdbc.queryForObject("SELECT COUNT(*) FROM resource_knowledge_mapping WHERE resource_type=? AND resource_id=? AND knowledge_id=? AND status='ACCEPTED' AND deleted=0",Integer.class,type,resource,kid)!=1)throw new BusinessException(409,"课程知识关联损坏");}
    private String shared(JsonNode u){return "shared/"+u.required("key").asText()+"/";}
    private long questionKnowledge(JsonNode u,JsonNode item){if(!item.has("knowledgeKey"))return kid(u,item.required("knowledge").asInt());String key=item.get("knowledgeKey").asText();if(!key.startsWith("shared/")||!key.contains("/knowledge/"))throw new IllegalStateException("非法知识身份");long id=oldId(key);if(knowledge.selectById(id)==null)throw new BusinessException(409,"题目知识已删除");return id;}
    private String personal(JsonNode u,Long actor){return "user/"+actor+"/"+u.required("key").asText()+"/";}
    public boolean available(Long chapterId){return jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource WHERE seed_key=? AND resource_type='CHAPTER' AND resource_id=? AND resource_key IN (?,?,?,?,?)",Integer.class,InterviewSeedCatalog.KEY,chapterId,"shared/java-core/chapter/java-core.hashmap/chapter","shared/java-core/chapter/java-core.generics/chapter","shared/concurrency/chapter/concurrency.aqs/chapter","shared/spring-data/chapter/spring-transaction/chapter","shared/rag/chapter/citation-evaluation/chapter")==1;}
    @Transactional(readOnly=true)
    public Map<String,Object> dryRun(Long actor){actor(actor);List<Map<String,Object>> rows=new ArrayList<>();for(var u:units()){var c=chapter(u,actor);rows.add(Map.of("key",u.required("key").asText(),"chapterId",c.getId(),"title",u.required("title").asText(),"questions",u.required("questions").size(),"flashcards",10,"experiments",1,"sources",u.required("sources").size()));}return Map.of("version",KEY,"dryRun",true,"chapters",rows,"activated",active(actor));}
    private boolean active(Long actor){return jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_activation WHERE seed_key=? AND user_id=?",Integer.class,KEY,actor)==1;}
    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String,Object> activate(Long actor){
        actor(actor);jdbc.queryForList("SELECT id FROM sys_user WHERE id=? AND deleted=0 FOR UPDATE",actor);actor(actor);
        if(!Boolean.TRUE.equals(original.status(actor).get("activated")))throw new BusinessException(409,"请先启用v1学习包");
        int initialized=jdbc.queryForObject("SELECT initialized FROM interview_seed_pack WHERE seed_key=? FOR UPDATE",Integer.class,KEY);
        var units=units();for(var u:units)chapter(u,actor);
        if(initialized==0){if(jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource WHERE seed_key=?",Integer.class,KEY)!=0)throw new BusinessException(409,"课程版本存在部分索引");for(var u:units)createShared(u);jdbc.update("UPDATE interview_seed_pack SET initialized=1 WHERE seed_key=?",KEY);}
        for(var u:units)validateShared(u,actor);
        if(!active(actor)){
            if(jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource WHERE seed_key=? AND resource_key LIKE ?",Integer.class,KEY,"user/"+actor+"/%")!=0)throw new BusinessException(409,"个人课程索引不完整");
            for(var u:units){var c=chapter(u,actor);var d=row(shared(u)+"document",docs);int count=0;
                for(var q:u.required("questions")){if(!"SHORT_ANSWER".equals(q.required("type").asText()))continue;
                    var f=new LearningFlashcard();f.setUserId(actor);f.setChapterId(c.getId());f.setPathId(c.getPathId());f.setCategoryId(d.getCategoryId());f.setDocId(d.getId());f.setFront(q.required("title").asText());f.setBack(q.required("answer").asText());f.setDifficulty(q.required("difficulty").asInt());f.setTags(KEY+",S,"+u.required("key").asText());f.setSourceType("IMPORT");f.setReviewCount(0);f.setReviewInterval(0);f.setEaseFactor(250);add(personal(u,actor)+"card/"+q.required("key").asText(),"FLASHCARD",cards,f);link("FLASHCARD",f.getId(),questionKnowledge(u,q));count++;
                }if(count!=10)throw new IllegalStateException("作者卡片数不正确");
            }
            jdbc.update("INSERT INTO interview_seed_activation(seed_key,user_id,goal_id,activated_at) VALUES(?,?,?,CURRENT_TIMESTAMP)",KEY,actor,original.status(actor).get("goalId"));
        }
        for(var u:units)validatePrivate(u,actor);var result=new LinkedHashMap<String,Object>(dryRun(actor));result.put("dryRun",false);return result;
    }
    private void createShared(JsonNode u){
        var d=new DocDocument();d.setTitle(u.required("title").asText()+" · Rebuild V1");d.setContent(file(u.required("body").asText()));d.setSummary(u.required("summary").asText());d.setCategoryId(oldId("shared/"+u.required("path").asText()+"/category"));d.setCategoryPath(d.getCategoryId().toString());d.setDifficulty(2);d.setStatus(1);d.setTags(KEY+",S,"+u.required("key").asText());d.setWordCount(d.getContent().length());d.setViewCount(0);d.setReadCount(0);d.setFavoriteCount(0);add(shared(u)+"document","DOC",docs,d);for(int i=1;i<=3;i++)link("DOC",d.getId(),kid(u,i));int order=0;
        for(var item:u.required("questions")){var q=new QuizQuestion();q.setTitle(item.required("title").asText());q.setContent(item.required("title").asText());q.setQuestionType(item.required("type").asText());q.setOptions(item.has("options")?item.get("options").toString():null);q.setAnswer(item.required("answer").asText());q.setExplanation(item.required("explanation").asText()+"\n\n来源（版本/定位见正文）：\n"+u.required("sources").toString());q.setCategoryId(d.getCategoryId());q.setDocId(d.getId());q.setDifficulty(item.required("difficulty").asInt());q.setStatus(1);q.setSource("MANUAL");q.setSortOrder(order++);q.setTags(KEY+",S,"+u.required("key").asText()+(item.path("scenario").asBoolean()?",SCENARIO":""));add(shared(u)+"question/"+item.required("key").asText(),"QUIZ",questions,q);link("QUIZ",q.getId(),questionKnowledge(u,item));}
        var e=new CodeQuestion();e.setTitle(u.required("className").asText()+" · Java17可复现实验");e.setDescription(u.required("experimentDescription").asText()+"\n\n在可信本地 Java17 用 javac 编译运行；网页无Java沙箱判题。实验不调用付费模型，不代表生产组件验证。");e.setLanguage("java");e.setCodeTemplate(file(u.required("experiment").asText()));e.setExampleOutput(u.required("output").asText());e.setSolutionHint(u.required("experimentDescription").asText());e.setTestCases("[]");e.setDifficulty(1);e.setStatus(1);e.setDuration(20);e.setPassCount(0);e.setSubmitCount(0);e.setTags(KEY+","+u.required("key").asText());add(shared(u)+"experiment","CODE",exercises,e);link("CODE_QUESTION",e.getId(),kid(u,2));
    }
    private void validateShared(JsonNode u,Long actor){var d=row(shared(u)+"document",docs);access.requireRead(d,actor);if(!Integer.valueOf(1).equals(d.getStatus()))throw new BusinessException(409,"课程文档未发布");for(int i=1;i<=3;i++)requireLink("DOC",d.getId(),kid(u,i));for(var item:u.required("questions")){var q=row(shared(u)+"question/"+item.required("key").asText(),questions);if(!d.getId().equals(q.getDocId())||!Integer.valueOf(1).equals(q.getStatus()))throw new BusinessException(409,"题目来源关系不正确");requireLink("QUIZ",q.getId(),questionKnowledge(u,item));}var e=row(shared(u)+"experiment",exercises);if(!Integer.valueOf(1).equals(e.getStatus()))throw new BusinessException(409,"实验未发布");requireLink("CODE_QUESTION",e.getId(),kid(u,2));}
    private void validatePrivate(JsonNode u,Long actor){var c=chapter(u,actor);var d=row(shared(u)+"document",docs);for(var item:u.required("questions")){if(!"SHORT_ANSWER".equals(item.required("type").asText()))continue;var f=row(personal(u,actor)+"card/"+item.required("key").asText(),cards);if(!actor.equals(f.getUserId())||!c.getId().equals(f.getChapterId())||!c.getPathId().equals(f.getPathId())||!d.getId().equals(f.getDocId()))throw new BusinessException(409,"个人卡片归属不正确");requireLink("FLASHCARD",f.getId(),questionKnowledge(u,item));}}
    @Transactional(readOnly=true)
    public Map<String,Object> revision(Long actor,Long chapterId){actor(actor);if(!active(actor))return null;for(var u:units()){var c=chapter(u,actor);if(!chapterId.equals(c.getId()))continue;validateShared(u,actor);validatePrivate(u,actor);var d=row(shared(u)+"document",docs);List<Map<String,Object>> qs=new ArrayList<>();for(var item:u.required("questions")){var q=row(shared(u)+"question/"+item.required("key").asText(),questions);var value=new LinkedHashMap<String,Object>();value.put("id",q.getId());value.put("title",q.getTitle());value.put("answer",q.getAnswer());value.put("explanation",q.getExplanation());value.put("questionType",q.getQuestionType());value.put("options",q.getOptions());value.put("canonicalKey",KEY+"/"+u.required("key").asText()+"/"+item.required("key").asText());value.put("parentKey",item.hasNonNull("parent")?KEY+"/"+u.required("key").asText()+"/"+item.get("parent").asText():null);value.put("importance","S");qs.add(value);}List<KgEntity> ks=new ArrayList<>();for(int i=1;i<=3;i++)ks.add(knowledge.selectById(kid(u,i)));return Map.of("version",KEY,"content",d.getContent(),"docId",d.getId(),"docTitle",d.getTitle(),"questions",qs,"exercises",List.of(row(shared(u)+"experiment",exercises)),"knowledge",ks,"notes",List.of(),"flashcardCount",10);}return null;}
}
