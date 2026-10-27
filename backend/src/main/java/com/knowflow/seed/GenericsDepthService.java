package com.knowflow.seed;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.*;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.*;
import com.knowflow.security.DocumentAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Append-only content revision using the existing Seed/Quiz/Flashcard/Code entities. */
@Service @RequiredArgsConstructor
public class GenericsDepthService {
    public static final String KEY="java-generics-depth-v1";
    public static final String READING_VERSION="java-generics-reading-v5";
    private static final String READING_KEY="shared/document/"+READING_VERSION;
    private static final String CH="shared/java-core/chapter/java-core.generics/";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final InterviewSeedService seed;
    private final SysUserMapper users;
    private final LearningChapterMapper chapters;
    private final LearningPathMapper paths;
    private final DocDocumentMapper docs;
    private final QuizQuestionMapper questions;
    private final CodeQuestionMapper codes;
    private final LearningFlashcardMapper cards;
    private final KgEntityMapper knowledge;
    private final DocumentAccessPolicy documentAccess;

    private void actor(Long actor){var u=actor==null?null:users.selectById(actor);if(u==null)throw new BusinessException(401,"未登录");if(!InterviewSeedAccessPolicy.allowed(u,jdbc))throw new BusinessException(403,"内容修订仅供普通用户本人启用");}
    private String file(String name){try{return new ClassPathResource("seed/"+name).getContentAsString(StandardCharsets.UTF_8);}catch(Exception e){throw new IllegalStateException("Revision source unavailable",e);}}
    private JsonNode authored(){try{return json.readTree(file("generics-depth-questions.json"));}catch(Exception e){throw new IllegalStateException("Revision questions unavailable",e);}}
    private long old(String key){var rows=jdbc.queryForList("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key=?",InterviewSeedCatalog.KEY,key);if(rows.size()!=1)throw new BusinessException(409,"v1原资源索引不完整");return ((Number)rows.get(0).get("resource_id")).longValue();}
    private <T extends BaseEntity>T row(String key,BaseMapper<T> mapper){var ids=jdbc.queryForList("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key=?",Long.class,KEY,key);T result=ids.size()==1?mapper.selectById(ids.get(0)):null;if(result==null)throw new BusinessException(409,"修订资源已删除或损坏，未覆盖重建");return result;}
    public boolean available(Long chapter){return jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource WHERE seed_key=? AND resource_key=? AND resource_id=?",Integer.class,InterviewSeedCatalog.KEY,CH+"chapter",chapter)==1;}
    public String preview(Long chapter){return available(chapter)?file("generics-reading-v5.md"):null;}
    public boolean readingUpgradeAvailable(Long actor,Long chapter){return available(chapter)&&active(actor)&&jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource WHERE seed_key=? AND resource_key=?",Integer.class,KEY,readingChoice(actor))==0;}
    private String readingChoice(Long actor){return "user/"+actor+"/reading/"+READING_VERSION;}
    /** Explicit append-only selection; never updates old documents or training rows. Pack lock is held by activate. */
    private void selectReading(Long actor,long category){
        var existing=jdbc.queryForList("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key=?",KEY,READING_KEY);
        if(existing.isEmpty()){
            var d=new DocDocument();d.setTitle("泛型与 ConcurrentHashMap · 结构化阅读 V5");d.setContent(file("generics-reading-v5.md"));d.setSummary("类型、PECS、并发映射与值对象的十个知识单元；旧正文保留");d.setCategoryId(category);d.setCategoryPath(Long.toString(category));d.setStatus(1);d.setDifficulty(2);d.setTags("JAVA,GENERICS,"+READING_VERSION);d.setWordCount(d.getContent().length());d.setViewCount(0);d.setReadCount(0);d.setFavoriteCount(0);
            seed.insertRevision(KEY,READING_KEY,"DOC",docs,()->d);
            for(int i=1;i<=3;i++)seed.map("DOC",d.getId(),old(CH+"knowledge/java-core.generics.k"+i));
        }
        var d=row(READING_KEY,docs);documentAccess.requireRead(d,actor);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource WHERE seed_key=? AND resource_key=?",Integer.class,KEY,readingChoice(actor))==0)
            jdbc.update("INSERT INTO interview_seed_resource(seed_key,resource_key,resource_type,resource_id) VALUES(?,?,?,?)",KEY,readingChoice(actor),"DOC",d.getId());
    }
    private boolean active(Long actor){return jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_activation WHERE seed_key=? AND user_id=?",Integer.class,KEY,actor)==1;}
    private LearningChapter accessible(Long actor,Long chapter){var c=chapters.selectById(chapter);if(c==null)throw new BusinessException(404,"章节不存在");var p=paths.selectById(c.getPathId());if(p==null||!Integer.valueOf(1).equals(p.getStatus())||(p.getOwnerUserId()!=null&&p.getOwnerUserId()!=0&&!actor.equals(p.getOwnerUserId())))throw new BusinessException(403,"无权访问章节");return c;}

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String,Object> activate(Long actor){
        actor(actor);jdbc.queryForList("SELECT id FROM sys_user WHERE id=? AND deleted=0 FOR UPDATE",actor);actor(actor);
        if(!Boolean.TRUE.equals(seed.status(actor).get("activated")))throw new BusinessException(409,"请先启用v1学习包");
        int initialized=jdbc.queryForObject("SELECT initialized FROM interview_seed_pack WHERE seed_key=? FOR UPDATE",Integer.class,KEY);
        long chapter=old(CH+"chapter"),category=old("shared/java-core/category");var c=accessible(actor,chapter);
        if(initialized==0){
            var d=new DocDocument();d.setTitle("泛型与 ConcurrentHashMap · Java17 深度修订");d.setContent(file("generics-depth.md"));d.setSummary("类型约束、原子操作、值对象与业务不变量；保留原文");d.setCategoryId(category);d.setCategoryPath(Long.toString(category));d.setStatus(1);d.setDifficulty(2);d.setTags("JAVA,GENERICS,CONCURRENT_HASH_MAP,S,"+KEY);d.setWordCount(d.getContent().length());d.setViewCount(0);d.setReadCount(0);d.setFavoriteCount(0);
            seed.insertRevision(KEY,"shared/document","DOC",docs,()->d);for(int i=1;i<=3;i++)seed.map("DOC",d.getId(),old(CH+"knowledge/java-core.generics.k"+i));
            int order=0;
            for(var q:authored()){
                var question=new QuizQuestion();question.setTitle(q.get("title").asText());question.setContent(q.get("title").asText()+"\n先闭卷讲解，再提交；现有简答匹配不代表完整面试评分。");question.setAnswer(q.get("answer").asText());question.setExplanation("30秒："+q.get("answer").asText()+"\n90秒："+q.get("detail").asText()+"\n常见错误："+q.get("wrong").asText());question.setQuestionType("SHORT_ANSWER");question.setDifficulty(2);question.setDocId(d.getId());question.setCategoryId(category);question.setTags("JAVA,GENERICS,CONCURRENT_HASH_MAP,S,"+KEY);question.setSource("MANUAL");question.setStatus(1);question.setSortOrder(order++);
                seed.insertRevision(KEY,"shared/question/"+q.get("key").asText(),"QUIZ",questions,()->question);seed.map("QUIZ",question.getId(),old(CH+"knowledge/java-core.generics.k"+q.get("knowledge").asInt()));
            }
            var code=new CodeQuestion();code.setTitle("泛型与原子边界 · Java17 实验");code.setLanguage("java");code.setDifficulty(1);code.setDescription("先预测类型复制、擦除、屏障丢失更新、merge与null边界。复制完整文件，在可信本地运行 javac GenericsBoundaryDemo.java && java GenericsBoundaryDemo；网页不执行Java或自动记分。本实验不是性能测试。");code.setCodeTemplate(file("GenericsBoundaryDemo.java.txt"));code.setSolutionHint("复制为[1,2,3]；运行时类相同；屏障使错误计数为1，merge为2；拒绝null；LongAdder在停止写入后为1。说明映射和可变值是不同边界，多键不变量另需协议。");code.setExampleOutput("[1, 2, 3]\ntrue\nget+put=1\nmerge=2\nnull rejected\nquiescent sum=1");code.setTestCases("[]");code.setDuration(15);code.setStatus(1);code.setSubmitCount(0);code.setPassCount(0);code.setTags("JAVA,GENERICS,"+KEY);seed.insertRevision(KEY,"shared/exercise","CODE",codes,()->code);for(int i=1;i<=3;i++)seed.map("CODE_QUESTION",code.getId(),old(CH+"knowledge/java-core.generics.k"+i));
            jdbc.update("UPDATE interview_seed_pack SET initialized=1 WHERE seed_key=?",KEY);
        }
        if(!active(actor)){
            if(jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource WHERE seed_key=? AND resource_key LIKE ?",Integer.class,KEY,"user/"+actor+"/%")!=0)throw new BusinessException(409,"个人修订索引不完整");
            long document=row("shared/document",docs).getId();
            for(var q:authored()){
                var card=new LearningFlashcard();card.setUserId(actor);card.setChapterId(chapter);card.setPathId(c.getPathId());card.setDocId(document);card.setCategoryId(category);card.setFront(q.get("title").asText());card.setBack(q.get("answer").asText());card.setSourceType("IMPORT");card.setDifficulty(2);card.setTags("JAVA,GENERICS,"+KEY);card.setReviewCount(0);card.setReviewInterval(0);card.setEaseFactor(250);
                seed.insertRevision(KEY,"user/"+actor+"/card/"+q.get("key").asText(),"FLASHCARD",cards,()->card);seed.map("FLASHCARD",card.getId(),old(CH+"knowledge/java-core.generics.k"+q.get("knowledge").asInt()));
            }
            jdbc.update("INSERT INTO interview_seed_activation(seed_key,user_id,goal_id,activated_at) VALUES(?,?,?,CURRENT_TIMESTAMP)",KEY,actor,seed.status(actor).get("goalId"));
        }
        selectReading(actor,category);
        return Map.of("version",KEY,"activated",true,"chapterId",chapter,"revision",revision(actor,chapter));
    }
    private void link(String type,long resource,long kid){if(jdbc.queryForObject("SELECT COUNT(*) FROM resource_knowledge_mapping WHERE resource_type=? AND resource_id=? AND knowledge_id=? AND deleted=0 AND status='ACCEPTED'",Integer.class,type,resource,kid)!=1)throw new BusinessException(409,"修订知识关联损坏");}
    @Transactional(readOnly=true)
    public Map<String,Object> revision(Long actor,Long chapter){
        actor(actor);if(!available(chapter)||!active(actor))return null;var c=accessible(actor,chapter);var d=row("shared/document",docs);documentAccess.requireRead(d,actor);if(!Integer.valueOf(1).equals(d.getStatus()))throw new BusinessException(409,"修订文档未发布");
        List<Map<String,Object>> result=new ArrayList<>();Set<String> authoredKeys=new HashSet<>();for(var q:authored())authoredKeys.add(q.get("key").asText());
        for(var q:authored()){
            String key=q.get("key").asText();long kid=old(CH+"knowledge/java-core.generics.k"+q.get("knowledge").asInt());var question=row("shared/question/"+key,questions);if(!Integer.valueOf(1).equals(question.getStatus())||!d.getId().equals(question.getDocId()))throw new BusinessException(409,"修订题目来源冲突");link("QUIZ",question.getId(),kid);
            var f=row("user/"+actor+"/card/"+key,cards);if(!actor.equals(f.getUserId())||!chapter.equals(f.getChapterId())||!c.getPathId().equals(f.getPathId())||!d.getId().equals(f.getDocId()))throw new BusinessException(409,"私人卡片归属冲突");link("FLASHCARD",f.getId(),kid);
            Map<String,Object> item=new LinkedHashMap<>();item.put("id",question.getId());item.put("title",question.getTitle());item.put("answer",question.getAnswer());item.put("explanation",question.getExplanation());item.put("canonicalKey",KEY+"/"+key);String parent=q.get("parent").isNull()?null:q.get("parent").asText();if(parent!=null&&!authoredKeys.contains(parent))throw new IllegalStateException("Unknown follow-up parent");item.put("parentKey",parent==null?null:KEY+"/"+parent);item.put("importance","S");result.add(item);
        }
        var code=row("shared/exercise",codes);if(!Integer.valueOf(1).equals(code.getStatus()))throw new BusinessException(409,"修订实验未发布");List<KgEntity> ks=new ArrayList<>();for(int i=1;i<=3;i++){long kid=old(CH+"knowledge/java-core.generics.k"+i);link("DOC",d.getId(),kid);link("CODE_QUESTION",code.getId(),kid);var k=knowledge.selectById(kid);if(k==null)throw new BusinessException(409,"知识已删除");ks.add(k);}
        var selected=jdbc.queryForList("SELECT resource_id,resource_type FROM interview_seed_resource WHERE seed_key=? AND resource_key=?",KEY,readingChoice(actor));
        var reading=d;
        if(!selected.isEmpty()){
            reading=row(READING_KEY,docs);
            if(selected.size()!=1||!"DOC".equals(selected.get(0).get("resource_type"))||reading.getId().longValue()!=((Number)selected.get(0).get("resource_id")).longValue())throw new BusinessException(409,"阅读版本选择冲突");
            documentAccess.requireRead(reading,actor);if(!Integer.valueOf(1).equals(reading.getStatus()))throw new BusinessException(409,"阅读版本未发布");
            for(var k:ks)link("DOC",reading.getId(),k.getId());
        }
        Map<String,Object> response=new LinkedHashMap<>();response.put("version",KEY);response.put("readingVersion",selected.isEmpty()?KEY:READING_VERSION);response.put("previousReadingDocId",d.getId());response.put("content",reading.getContent());response.put("docId",reading.getId());response.put("docTitle",reading.getTitle());response.put("questions",result);response.put("exercises",List.of(code));response.put("knowledge",ks);response.put("notes",List.of());response.put("flashcardCount",6);return response;
    }
}
