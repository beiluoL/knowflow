package com.knowflow.seed;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.*;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import static com.knowflow.seed.InterviewSeedCatalog.text;

/** Writes learning content through existing models; deliberately never calls event-producing services. */
@Service @RequiredArgsConstructor
public class InterviewSeedService {
    private final InterviewSeedCatalog catalog;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final SysUserMapper users;
    private final LearningPathMapper paths;
    private final LearningChapterMapper chapters;
    private final DocCategoryMapper categories;
    private final DocDocumentMapper docs;
    private final KgEntityMapper knowledge;
    private final QuizQuestionMapper questions;
    private final CodeQuestionMapper exercises;
    private final ResourceKnowledgeMappingMapper mappings;
    private final LearningGoalMapper goals;
    private final LearningUserPathMapper enrollments;
    private final LearningFlashcardMapper cards;
    private final LearningNoteMapper notes;
    private final LearningTaskMapper tasks;
    private final com.knowflow.security.DocumentAccessPolicy documentAccess;
    private static final Map<String,Integer> COUNTS=Map.of("paths",8,"chapters",32,"knowledge",96,"documents",16,"notes",24,"flashcards",128,"questions",96,"exercises",32,"tasks",7);

    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String,Object> status(Long actor) {
        requireUser(actor);
        var rows=jdbc.queryForList("SELECT goal_id FROM interview_seed_activation WHERE seed_key=? AND user_id=?",InterviewSeedCatalog.KEY,actor);
        Map<String,Object> out=new LinkedHashMap<>();out.put("seedKey",InterviewSeedCatalog.KEY);out.put("activated",!rows.isEmpty());out.put("counts",COUNTS);
        if(!rows.isEmpty()) {validateResources("shared/");validateResources("user/"+actor+"/");validateMappings(actor);out.put("goalId",rows.get(0).get("goal_id"));out.put("pathIds",pathIds());out.put("paths",catalog.paths().stream().map(p->Map.of("id",id("shared/"+text(p,"key")+"/path"),"title",text(p,"title"))).toList());}
        return out;
    }
    private void requireUser(Long actor) {
        if(actor==null)throw new BusinessException(401,"未登录");
        SysUser user=users.selectById(actor);
        if(user==null)throw new BusinessException(401,"账号不存在或已删除");
        if(!InterviewSeedAccessPolicy.allowed(user,jdbc))throw new BusinessException(403,"学习包仅由普通用户本人启用，管理员数据不会自动初始化");
    }

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String,Object> activate(Long actor) {
        requireUser(actor);
        jdbc.queryForList("SELECT id FROM sys_user WHERE id=? AND deleted=0 FOR UPDATE",actor);
        requireUser(actor); // Re-check after waiting for a concurrent delete/role change.
        int initialized=jdbc.queryForObject("SELECT initialized FROM interview_seed_pack WHERE seed_key=? FOR UPDATE",Integer.class,InterviewSeedCatalog.KEY);
        var content=catalog.paths();
        if(initialized==0) {
            createTemplate(content);
            jdbc.update("UPDATE interview_seed_pack SET initialized=1 WHERE seed_key=?",InterviewSeedCatalog.KEY);
        } else validateResources("shared/");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_activation WHERE seed_key=? AND user_id=?",Integer.class,InterviewSeedCatalog.KEY,actor)>0) {
            validateResources("user/"+actor+"/");return status(actor);
        }
        createInstance(actor,content);
        return status(actor);
    }

    private void createTemplate(List<JsonNode> content) {
        int pathOrder=0;
        for(JsonNode p:content) {
            String pk=text(p,"key"),prefix="shared/"+pk+"/";
            Long categoryId=insert(prefix+"category","CATEGORY",categories,()->{
                DocCategory c=new DocCategory();c.setName(text(p,"title"));c.setCode("java-ai-v1-"+pk);c.setParentId(0L);c.setOwnerId(null);c.setStatus(1);c.setDescription(text(p,"description"));c.setDocCount(2);c.setSortOrder(100);return c;
            });
            final int po=pathOrder++;
            Long pathId=insert(prefix+"path","PATH",paths,()->{
                LearningPath path=new LearningPath();path.setTitle(text(p,"title"));path.setDescription(text(p,"description"));path.setOwnerUserId(0L);path.setLevel("进阶");path.setChapterCount(4);path.setTotalDuration(sumMinutes(p));path.setStatus(1);path.setSortOrder(100+po);path.setEnrolledCount(0);return path;
            });
            Map<String,Long> chapterIds=new HashMap<>();
            for(int pair=0;pair<2;pair++) {
                JsonNode a=p.required("chapters").get(pair*2),b=p.required("chapters").get(pair*2+1);
                String body="# "+text(p,"title")+"\n\n## "+text(a,"title")+"\n\n"+text(a,"material")+"\n\n## "+text(b,"title")+"\n\n"+text(b,"material");
                final int di=pair;
                Long docId=insert(prefix+"document/"+pair,"DOC",docs,()->{
                    DocDocument d=new DocDocument();d.setTitle(text(p,"title")+" · "+text(a,"title")+" / "+text(b,"title"));d.setContent(body);d.setSummary(text(a,"description")+"；"+text(b,"description"));d.setCategoryId(categoryId);d.setCategoryPath(categoryId.toString());d.setTags(tags(p,"S"));d.setStatus(1);d.setSortOrder(di);d.setWordCount(body.length());d.setDifficulty(2);d.setViewCount(0);d.setReadCount(0);d.setFavoriteCount(0);return d;
                });
                for(int ci=pair*2;ci<pair*2+2;ci++) {
                    JsonNode ch=p.required("chapters").get(ci);String ck=text(ch,"key"),cp=prefix+"chapter/"+ck+"/";final int order=ci;
                    Long chapterId=insert(cp+"chapter","CHAPTER",chapters,()->{
                        LearningChapter chapter=new LearningChapter();chapter.setPathId(pathId);chapter.setTitle(text(ch,"title"));chapter.setSortOrder(order+1);chapter.setDuration(ch.required("minutes").asInt());chapter.setDocIds(docId.toString());chapter.setFlashcardIds("");
                        List<String> dependencies=new ArrayList<>();ch.required("prerequisites").forEach(v->dependencies.add(chapterIds.get(v.asText()).toString()));chapter.setPrerequisiteChapterIds(String.join(",",dependencies));
                        chapter.setContent(chapterBody(ch));return chapter;
                    });chapterIds.put(ck,chapterId);
                    Map<String,Long> kIds=new HashMap<>();
                    for(JsonNode k:ch.required("knowledge")) {
                        Long kid=insert(cp+"knowledge/"+text(k,"key"),"KNOWLEDGE",knowledge,()->{
                            KgEntity ke=new KgEntity();ke.setDocId(docId);ke.setCategoryId(categoryId);ke.setName(text(k,"name"));ke.setDescription(text(k,"explanation"));ke.setType("CONCEPT");ke.setWeight(1);return ke;
                        });kIds.put(text(k,"key"),kid);map("DOC",docId,kid);
                    }
                    int qi=0;
                    for(JsonNode q:ch.required("questions")) {
                        final int qorder=qi++;
                        Long qid=insert(cp+"question/"+qorder,"QUIZ",questions,()->{
                            QuizQuestion question=new QuizQuestion();question.setTitle(text(q,"question"));question.setContent(text(q,"question")+"\n\n请按结论→原理→流程→工程场景→边界，用60–120秒回答。简答自动匹配仅作练习提示，不代表面试能力评分。");question.setQuestionType("SHORT_ANSWER");question.setAnswer(text(q,"answer"));question.setExplanation("关键点：\n"+bullets(q.required("keyPoints"))+"\n追问：\n"+bullets(q.required("followUps"))+"\n常见错误：\n"+bullets(q.required("commonMistakes")));question.setDifficulty(ch.required("difficulty").asInt());question.setCategoryId(categoryId);question.setDocId(docId);question.setTags(tags(p,text(q,"rank")));question.setSource("MANUAL");question.setStatus(1);question.setSortOrder(order*3+qorder);return question;
                        });references("QUIZ",qid,q,kIds);
                    }
                    var ex=ch.required("exercise");
                    Long eid=insert(cp+"exercise","CODE",exercises,()->{
                        CodeQuestion e=new CodeQuestion();e.setTitle(text(ex,"title"));e.setDescription(text(ex,"description")+"\n\nJava工程练习：当前网页自动判题执行器不提供Java自动判定；请在可信本地Java17环境练习，依据解题提示人工检查，不把运行结果当生产沙箱能力。");e.setLanguage("java");e.setDifficulty(ch.required("difficulty").asInt()-1);e.setTags(tags(p,"CODE"));e.setHint(ex.path("hint").asText());e.setExampleInput(ex.path("exampleInput").asText());e.setExampleOutput(ex.path("exampleOutput").asText());e.setCodeTemplate(text(ex,"codeTemplate"));e.setTestCases(ex.required("testCases").toString());e.setSolutionHint(ex.path("solutionHint").asText());e.setDuration(ch.required("minutes").asInt());e.setStatus(1);e.setSortOrder(100+po*4+order);e.setPassCount(0);e.setSubmitCount(0);return e;
                    });references("CODE_QUESTION",eid,ex,kIds);
                }
            }
        }
    }

    private void createInstance(Long userId,List<JsonNode> content) {
        String up="user/"+userId+"/";
        Long goal=insert(up+"goal","GOAL",goals,()->{
            LearningGoal g=new LearningGoal();g.setUserId(userId);g.setTitle("Java + AI 工程师面试冲刺");g.setDescription("面向 Java + AI Engineer / AI应用开发 / Agent开发 / 高级Java 岗位，系统掌握 Java 后端核心、高并发与中间件、大模型基础、RAG、Agent、Tool Calling、MCP、AI工程化和项目面试表达，并通过主动回忆、代码练习、测验和项目问答形成可验证的面试能力。");g.setTarget("能独立解释关键流程、完成工程练习，并基于真实个人项目回答60–120秒追问；不以预设成绩宣称掌握。");g.setStatus("ACTIVE");g.setPathId(id("shared/java-core/path"));g.setStartDate(LocalDate.now());g.setDailyMinutes(60);return g;
        });
        for(JsonNode p:content) {
            String pk=text(p,"key"),sp="shared/"+pk+"/";Long pathId=id(sp+"path"),categoryId=id(sp+"category");
            // Preserve a pre-existing enrollment rather than conflicting with its unique(user,path) key.
            var existing=jdbc.queryForList("SELECT id,deleted FROM learning_user_path WHERE user_id=? AND path_id=?",userId,pathId);
            if(!existing.isEmpty()) {if(((Number)existing.get(0).get("deleted")).intValue()!=0)throw new BusinessException(409,"学习包路径报名曾被删除，请先处理现有报名状态");jdbc.update("INSERT INTO interview_seed_resource(seed_key,resource_key,resource_type,resource_id) VALUES(?,?,?,?)",InterviewSeedCatalog.KEY,up+pk+"/enrollment","ENROLLMENT",existing.get(0).get("id"));}
            else insert(up+pk+"/enrollment","ENROLLMENT",enrollments,()->{LearningUserPath row=new LearningUserPath();row.setUserId(userId);row.setPathId(pathId);row.setProgress(BigDecimal.ZERO);row.setCompletedChapters(0);row.setEnrollTime(LocalDateTime.now());return row;});
            int ci=0;
            for(JsonNode ch:p.required("chapters")) {
                String ck=text(ch,"key"),cp=sp+"chapter/"+ck+"/";Long chapterId=id(cp+"chapter"),docId=id(sp+"document/"+(ci++/2));
                int fi=0;
                for(JsonNode f:ch.required("flashcards")) {
                    Long cardId=insert(up+ck+"/card/"+fi++,"FLASHCARD",cards,()->{
                        LearningFlashcard card=new LearningFlashcard();card.setUserId(userId);card.setPathId(pathId);card.setChapterId(chapterId);card.setDocId(docId);card.setCategoryId(categoryId);card.setFront(text(f,"front"));card.setBack(text(f,"back"));card.setCategory(text(p,"title"));card.setDifficulty(ch.required("difficulty").asInt());card.setTags(tags(p,text(ch,"importance"))+","+text(f,"type"));card.setSourceType("IMPORT");card.setReviewCount(0);card.setReviewInterval(0);card.setEaseFactor(250);return card;
                    });
                    for(JsonNode ref:f.required("knowledgeKeys"))map("FLASHCARD",cardId,id(cp+"knowledge/"+ref.asText()));
                }
            }
            int ni=0;
            for(JsonNode n:p.required("notes")) insert(up+pk+"/note/"+ni++,"NOTE",notes,()->{LearningNote note=new LearningNote();note.setUserId(userId);note.setPathId(pathId);note.setChapterId(id(sp+"chapter/"+text(n,"chapterKey")+"/chapter"));note.setTitle(text(n,"title"));note.setContent(text(n,"content"));return note;});
        }
        String[] titles={"Java Core：HashMap与ConcurrentHashMap","并发：AQS与ThreadPoolExecutor","JVM排障与Spring生命周期","MySQL、Redis与MQ","LLM、Embedding与RAG","Tool Calling、Agent与MCP","项目表达与综合模拟"};
        String[] directions={"java-core","concurrency","jvm","spring-data","rag","agent","project"};
        // Existing task consumer is newest-first. Insert backwards so Day1 is its first pending item.
        for(int day=6;day>=0;day--) {final int d=day;insert(up+"task/"+d,"TASK",tasks,()->{LearningTask t=new LearningTask();t.setUserId(userId);t.setTitle("Day "+(d+1)+" · "+titles[d]);t.setDescription("Java + AI 7-Day Interview Sprint：先读材料，闭卷回忆4张卡，用60–120秒讲解一道面试题，完成对应Java工程练习，最后写出仍讲不清的卡点。未学习前不预设成绩；当天不足可顺延。");t.setType("study");t.setTargetId(firstChapterId(content,directions[d]));t.setExpReward(0);t.setEnergyCost(0);t.setStatus(0);t.setDeadline(LocalDate.now().plusDays(d).atTime(23,59));return t;});}
        jdbc.update("INSERT INTO interview_seed_activation(seed_key,user_id,goal_id,activated_at) VALUES (?,?,?,?)",InterviewSeedCatalog.KEY,userId,goal,LocalDateTime.now());
    }

    private Long firstChapterId(List<JsonNode> content,String key) {JsonNode p=content.stream().filter(v->text(v,"key").equals(key)).findFirst().orElseThrow();return id("shared/"+key+"/chapter/"+text(p.required("chapters").get(0),"key")+"/chapter");}
    private List<Long> pathIds() {return catalog.paths().stream().map(p->id("shared/"+text(p,"key")+"/path")).toList();}
    private int sumMinutes(JsonNode p) {int n=0;for(JsonNode c:p.required("chapters"))n+=c.required("minutes").asInt();return n;}
    private String tags(JsonNode p,String rank) {return text(p,"tag")+",INTERVIEW,"+rank+","+InterviewSeedCatalog.KEY;}
    private static String bullets(JsonNode rows) {List<String> values=new ArrayList<>();rows.forEach(r->values.add("- "+r.asText()));return String.join("\n",values);}
    private String chapterBody(JsonNode ch) {return "## 学习目标\n"+bullets(ch.required("objectives"))+"\n\n难度："+ch.required("difficulty").asInt()+"；预计 "+ch.required("minutes").asInt()+" 分钟；面试重要度："+text(ch,"importance")+"\n\n"+text(ch,"description")+"\n\n"+text(ch,"material")+"\n\n## 主动回忆与练习\n先读关联文档，闭卷解释核心知识，再复习本章个人闪卡，回答题库面试题并完成Java代码练习。能讲清原理与边界后再标记完成；Seed本身不是掌握证据。";}
    private <T extends BaseEntity> Long insert(String key,String type,BaseMapper<T> mapper,Supplier<T> factory) {
        return insertRevision(InterviewSeedCatalog.KEY,key,type,mapper,factory);
    }
    <T extends BaseEntity> Long insertRevision(String seedKey,String key,String type,BaseMapper<T> mapper,Supplier<T> factory) {
        var existing=jdbc.queryForList("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key=?",seedKey,key);
        if(!existing.isEmpty())throw new BusinessException(409,"学习包索引存在未完成数据，未覆盖或重建："+key);
        T row=factory.get();mapper.insert(row);jdbc.update("INSERT INTO interview_seed_resource(seed_key,resource_key,resource_type,resource_id) VALUES(?,?,?,?)",seedKey,key,type,row.getId());return row.getId();
    }
    private Long id(String key) {
        var rows=jdbc.queryForList("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key=?",InterviewSeedCatalog.KEY,key);
        if(rows.size()!=1)throw new BusinessException(409,"学习包索引不完整："+key);return ((Number)rows.get(0).get("resource_id")).longValue();
    }
    private void references(String type,Long resourceId,JsonNode resource,Map<String,Long> kIds) {for(JsonNode key:resource.required("knowledgeKeys"))map(type,resourceId,kIds.get(key.asText()));}
    void map(String type,Long rid,Long kid) {ResourceKnowledgeMapping m=new ResourceKnowledgeMapping();m.setResourceType(type);m.setResourceId(rid);m.setKnowledgeId(kid);m.setSource("IMPORT");m.setConfidence(BigDecimal.ONE);m.setStatus("ACCEPTED");mappings.insert(m);}
    private void validateResources(String prefix) {
        var rows=jdbc.queryForList("SELECT resource_key,resource_type,resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key LIKE ?",InterviewSeedCatalog.KEY,prefix+"%");
        if(rows.isEmpty())throw new BusinessException(409,"学习包索引不完整");
        Set<String> actual=new HashSet<>();rows.forEach(r->actual.add(r.get("resource_key").toString()));
        if(!actual.equals(expectedKeys(prefix)))throw new BusinessException(409,"学习包索引不完整，未自动覆盖内容");
        for(var row:rows) {Long rid=((Number)row.get("resource_id")).longValue();BaseEntity resource=switch(row.get("resource_type").toString()) {
            case "CATEGORY"->categories.selectById(rid);case "PATH"->paths.selectById(rid);case "CHAPTER"->chapters.selectById(rid);case "DOC"->docs.selectById(rid);case "KNOWLEDGE"->knowledge.selectById(rid);case "QUIZ"->questions.selectById(rid);case "CODE"->exercises.selectById(rid);case "GOAL"->goals.selectById(rid);case "ENROLLMENT"->enrollments.selectById(rid);case "FLASHCARD"->cards.selectById(rid);case "NOTE"->notes.selectById(rid);case "TASK"->tasks.selectById(rid);default->null;};
            if(resource==null)throw new BusinessException(409,"学习包内容已删除或索引损坏，未自动复活");
            if(prefix.startsWith("user/")) {
                Long owner=resource instanceof LearningGoal r?r.getUserId():resource instanceof LearningUserPath r?r.getUserId():resource instanceof LearningFlashcard r?r.getUserId():resource instanceof LearningNote r?r.getUserId():resource instanceof LearningTask r?r.getUserId():null;
                if(!Objects.equals(owner,Long.valueOf(prefix.split("/")[1])))throw new BusinessException(409,"学习包个人资源归属发生冲突");
            }
        }
    }

    private Set<String> expectedKeys(String prefix) {
        Set<String> keys=new HashSet<>();boolean shared=prefix.equals("shared/");
        if(!shared){keys.add(prefix+"goal");for(int i=0;i<7;i++)keys.add(prefix+"task/"+i);}
        for(JsonNode p:catalog.paths()) {
            String pk=text(p,"key"),sp=prefix+pk+"/";
            if(shared){keys.add(sp+"category");keys.add(sp+"path");for(int i=0;i<2;i++)keys.add(sp+"document/"+i);}else {keys.add(sp+"enrollment");for(int i=0;i<3;i++)keys.add(sp+"note/"+i);}
            for(JsonNode ch:p.required("chapters")) {
                String ck=text(ch,"key");
                if(shared){String cp=sp+"chapter/"+ck+"/";keys.add(cp+"chapter");keys.add(cp+"exercise");for(int i=0;i<3;i++)keys.add(cp+"question/"+i);for(JsonNode k:ch.required("knowledge"))keys.add(cp+"knowledge/"+text(k,"key"));}
                else for(int i=0;i<4;i++)keys.add(prefix+ck+"/card/"+i);
            }
        }
        return keys;
    }
    private void validateMappings(Long actor) {
        for(JsonNode p:catalog.paths()) {
            String sp="shared/"+text(p,"key")+"/";int ci=0;
            for(JsonNode ch:p.required("chapters")) {
                String ck=text(ch,"key"),cp=sp+"chapter/"+ck+"/";Long docId=id(sp+"document/"+(ci++/2));
                for(JsonNode k:ch.required("knowledge"))requireMapping("DOC",docId,id(cp+"knowledge/"+text(k,"key")));
                // Each authored question has its own ordered resource key.
                for(int qi=0;qi<3;qi++)for(JsonNode ref:ch.required("questions").get(qi).required("knowledgeKeys"))requireMapping("QUIZ",id(cp+"question/"+qi),id(cp+"knowledge/"+ref.asText()));
                for(JsonNode ref:ch.required("exercise").required("knowledgeKeys"))requireMapping("CODE_QUESTION",id(cp+"exercise"),id(cp+"knowledge/"+ref.asText()));
                for(int fi=0;fi<4;fi++)for(JsonNode ref:ch.required("flashcards").get(fi).required("knowledgeKeys"))requireMapping("FLASHCARD",id("user/"+actor+"/"+ck+"/card/"+fi),id(cp+"knowledge/"+ref.asText()));
            }
        }
    }
    private void requireMapping(String type,Long resourceId,Long knowledgeId) {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM resource_knowledge_mapping WHERE resource_type=? AND resource_id=? AND knowledge_id=? AND status='ACCEPTED' AND deleted=0",Integer.class,type,resourceId,knowledgeId)!=1)throw new BusinessException(409,"学习包知识关联已删除或发生冲突，未自动重建");
    }

    /** Existing chapter page's resource strip; returns null for chapters outside this pack. */
    public Map<String,Object> chapterResources(Long actor,Long chapterId) {
        requireUser(actor);
        var rows=jdbc.queryForList("SELECT resource_key FROM interview_seed_resource WHERE seed_key=? AND resource_type='CHAPTER' AND resource_id=?",InterviewSeedCatalog.KEY,chapterId);
        if(rows.isEmpty())return null;
        String fullKey=rows.get(0).get("resource_key").toString();
        String prefix=fullKey.substring(0,fullKey.length()-"chapter".length());
        LearningChapter ch=chapters.selectById(chapterId);
        if(ch==null)throw new BusinessException(404,"章节不存在");
        LearningPath path=paths.selectById(ch.getPathId());
        if(path==null || !Integer.valueOf(1).equals(path.getStatus()) || (path.getOwnerUserId()!=null&&path.getOwnerUserId()!=0&&!path.getOwnerUserId().equals(actor)))throw new BusinessException(403,"无权访问章节");
        Long docId=Long.valueOf(ch.getDocIds());DocDocument doc=docs.selectById(docId);documentAccess.requireRead(doc,actor);
        List<QuizQuestion> questionList=new ArrayList<>();
        for(int i=0;i<3;i++) {QuizQuestion q=questions.selectById(id(prefix+"question/"+i));if(q!=null&&Integer.valueOf(1).equals(q.getStatus()))questionList.add(q);}
        CodeQuestion exercise=exercises.selectById(id(prefix+"exercise"));
        List<LearningNote> personalNotes=notes.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LearningNote>().eq(LearningNote::getUserId,actor).eq(LearningNote::getPathId,ch.getPathId()));
        Map<String,Object> out=new LinkedHashMap<>();out.put("chapterId",chapterId);out.put("docId",docId);out.put("docTitle",doc.getTitle());out.put("questions",questionList);out.put("exercise",exercise!=null&&Integer.valueOf(1).equals(exercise.getStatus())?exercise:null);out.put("notes",personalNotes);return out;
    }
}
