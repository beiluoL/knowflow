package com.knowflow.seed;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.*;
import com.knowflow.mapper.*;
import com.knowflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.*;

/** Append-only revision. No chapter, original document, private history or learning event is updated. */
@Service @RequiredArgsConstructor
public class CollectionsDepthService {
    public static final String KEY="java-collections-depth-v1";
    private static final String CH="shared/java-core/chapter/java-core.hashmap/";
    private final JdbcTemplate jdbc;
    private final InterviewSeedService original;
    private final SysUserMapper users;
    private final LearningChapterMapper chapters;
    private final LearningPathMapper paths;
    private final DocDocumentMapper docs;
    private final QuizQuestionMapper questions;
    private final CodeQuestionMapper exercises;
    private final LearningFlashcardMapper cards;
    private final LearningNoteMapper notes;
    private final KgEntityMapper knowledge;
    private final ResourceKnowledgeMappingMapper mappings;
    private final com.knowflow.security.DocumentAccessPolicy documentAccess;

    private void user(Long actor) {var u=actor==null?null:users.selectById(actor);if(u==null)throw new BusinessException(401,"未登录");if(!InterviewSeedAccessPolicy.allowed(u,jdbc))throw new BusinessException(403,"修订仅由普通用户本人启用");}
    private long oldId(String key) {var rows=jdbc.queryForList("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key=?",InterviewSeedCatalog.KEY,key);if(rows.size()!=1)throw new BusinessException(409,"请先启用完整的v1学习包");return ((Number)rows.get(0).get("resource_id")).longValue();}
    private Long find(String key) {var rows=jdbc.queryForList("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key=?",KEY,key);return rows.isEmpty()?null:((Number)rows.get(0).get("resource_id")).longValue();}
    private <T extends BaseEntity> T resource(String key,BaseMapper<T> mapper) {Long id=find(key);T row=id==null?null:mapper.selectById(id);if(row==null)throw new BusinessException(409,"修订资源已删除或索引损坏，未自动重建："+key);return row;}
    private <T extends BaseEntity> T add(String key,String type,BaseMapper<T> mapper,T row) {if(find(key)!=null)throw new BusinessException(409,"修订索引存在未完成数据");mapper.insert(row);jdbc.update("INSERT INTO interview_seed_resource(seed_key,resource_key,resource_type,resource_id) VALUES(?,?,?,?)",KEY,key,type,row.getId());return row;}
    private String file(String name) {try(var in=new ClassPathResource("seed/"+name).getInputStream()){return new String(in.readAllBytes(),StandardCharsets.UTF_8);}catch(java.io.IOException e){throw new IllegalStateException("Missing revision content",e);}}
    private void link(String type,Long rid,long kid) {ResourceKnowledgeMapping m=new ResourceKnowledgeMapping();m.setResourceType(type);m.setResourceId(rid);m.setKnowledgeId(kid);m.setSource("IMPORT");m.setStatus("ACCEPTED");m.setConfidence(BigDecimal.ONE);mappings.insert(m);}
    private static final String[] KEYS={"structure","hash","resize","treeify","threshold","concurrency","selection","failure"};
    private static final String[] TITLES={"HashMap底层结构与键契约是什么？","为什么混合高位并使用容量掩码？","扩容为什么用hash & oldCap分流？","为什么不能说第八个元素必定树化？","8、6、64与负载因子分别解决什么？","HashMap为什么线程不安全，fail-fast能保证安全吗？","什么时候选择ConcurrentHashMap而非HashMap？","键插入后查不到值，你如何排查？"};
    private static final String[] ANSWERS={"Node数组和桶链表/树；hash定位候选，equals判等。等价键须同散列，入表后不得改变等价字段。","容量二次幂使低位掩码可定位；h xor高16位把高位信息混入低位，但仍可能碰撞。","新容量翻倍只新增一位掩码；缓存hash与oldCap的结果决定保留原位或移动oldCap，不必重算键hashCode。","链表计数与treeifyBin联合决定树化；容量小于64优先扩容，阈值不是API保证。","8触发树化判断，6用于扩容树桶拆分退化，64限制小表树化；0.75折中空间与冲突，删除退化也看结构。","没有同步或原子复合操作保证，可能丢更新和不可见。fail-fast只是尽力诊断结构修改，不能代替同步。","共享并发映射选CHM；禁止null，单键原子更新复用compute/merge，多键业务事务另需协议。","先复现可变键与hash/equals，再区分null映射和不存在，检查并发、淘汰及业务删除；依据证据定位，不宣称随机丢数据。"};
    private static final String[] DETAIL={
        "从Map一键一值语义开始。putVal先定位桶，首节点/树/链表比较hash和equals；等价键替换值，新键增加size。getNode沿同一定位路径查询。可变键实验中节点保留插入散列，改id后新查询去另一个桶。采用不可变record键并遵守equals/hashCode契约。",
        "table容量按二次幂组织，n-1的低位全1，按位与得到合法桶号。只使用低位会忽略原hashCode高位差异，因此hash混入高16位。零散列对应null键是实现细节。散列混合不会把糟糕hashCode变成无碰撞函数，仍要equals和桶遍历。",
        "例如oldCap16，缓存hash17旧位置1，新容量32位置17。hash33仍从位置1到1。新增的容量位决定低/高分组，链表按组重建并保序。resize并非免费：要分配新数组和扫描旧桶，还要处理树桶拆分。容量治理要结合真实条目数和迭代成本。",
        "沿putVal观察binCount，再追treeifyBin：小容量表的长链可能只是桶少，先扩容比树节点开销更划算。容量足够才进入树化。不要只看常量8推断任意插入序号；碰撞键、容量与历史扩容共同影响结果。阈值不属于用户可依赖API。",
        "树节点比普通Node占用更多空间，8和6避免边界频繁转换。扩容split以分支节点数决定退化；removeTreeNode还看树结构。默认0.75兼顾空间和查找，初始容量按预估映射数考虑取整，但过大的稀疏表遍历也慢。严禁把所有退化说成size小于6。",
        "JDK7的并发扩容历史环不代表全部版本风险，JDK8迁移变化没有引入同步契约。线程间更新与扩容仍可能竞争；单线程结构修改也能触发CME。modCount检测是best-effort，不提供happens-before。选择同步包装时复合操作/遍历仍按锁协议执行。",
        "Java17 CHM用空桶CAS与非空桶局部同步，ForwardingNode引导迁移后访问；不能仍回答JDK7 Segment。compute/merge表达单键原子变换，回调保持短小，避免同键递归和远程IO；其size在并发变化下不适合强一致控制。禁止null消除查询语义歧义。",
        "记录插入和查询时键值/hashCode，最小实验验证键是否变动；containsKey区分不存在和null值。接着审计删除、TTL、并发和持有引用。修复稳定键后加入回归，再按业务规模配置上限，只有真实测量才讨论冲突或无界增长问题。"
    };
    private static final String[] WRONG={"hashCode相同就意味着equals相等。","扰动能消除所有碰撞。","扩容会重新调用每个键的hashCode。","第八个元素必然树化。","删除到六个节点一定马上退链表。","不抛ConcurrentModificationException就线程安全。","ConcurrentHashMap支持任意多键事务且所有方法无锁。","容器随机丢了数据，所以直接换HashTable。"};
    private int kIndex(int i){return i==0||i==7?1:i<=2?2:3;}
    public boolean available(Long chapterId) {return jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource WHERE seed_key=? AND resource_key=? AND resource_id=?",Integer.class,InterviewSeedCatalog.KEY,CH+"chapter",chapterId)==1;}
    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String,Object> activate(Long actor) {
        user(actor);jdbc.queryForList("SELECT id FROM sys_user WHERE id=? AND deleted=0 FOR UPDATE",actor);user(actor);
        if(!Boolean.TRUE.equals(original.status(actor).get("activated")))throw new BusinessException(409,"请先启用v1学习包");
        // Immutable lock row is provisioned at startup, never content.
        int initialized=jdbc.queryForObject("SELECT initialized FROM interview_seed_pack WHERE seed_key=? FOR UPDATE",Integer.class,KEY);
        long chapterId=oldId(CH+"chapter");LearningChapter ch=chapters.selectById(chapterId);if(ch==null)throw new BusinessException(409,"原章节已删除");
        long category=oldId("shared/java-core/category");
        if(initialized==0) {
            DocDocument d=new DocDocument();d.setTitle("HashMap · Java17深度修订");d.setContent(file("collections-depth-hashmap.md"));d.setSummary("键契约、源码决策、两实验、连续追问；保留v1原文");d.setCategoryId(category);d.setCategoryPath(Long.toString(category));d.setDifficulty(2);d.setStatus(1);d.setTags("JAVA,HASHMAP,S,"+KEY);d.setWordCount(d.getContent().length());d.setViewCount(0);d.setReadCount(0);d.setFavoriteCount(0);add("shared/hashmap/document","DOC",docs,d);
            for(int i=1;i<=3;i++)link("DOC",d.getId(),oldId(CH+"knowledge/java-core.hashmap.k"+i));
            for(int i=0;i<KEYS.length;i++){QuizQuestion q=new QuizQuestion();q.setTitle(TITLES[i]);q.setContent(TITLES[i]+"\n请先闭卷口述，再对照答案。简答匹配不等于面试评分。");q.setQuestionType("SHORT_ANSWER");q.setAnswer(ANSWERS[i]);q.setExplanation("30秒："+ANSWERS[i]+"\n90秒："+DETAIL[i]+"\n深挖："+(i+1<KEYS.length?TITLES[i+1]+"\n"+DETAIL[i+1]:"如何用不可变键与并发容器防止再次发生？\n"+DETAIL[6])+"\n常见错误："+WRONG[i]);q.setDifficulty(2);q.setCategoryId(category);q.setDocId(d.getId());q.setStatus(1);q.setSource("MANUAL");q.setTags("JAVA,HASHMAP,S,"+KEY);q.setSortOrder(i);add("shared/hashmap/question/"+KEYS[i],"QUIZ",questions,q);link("QUIZ",q.getId(),oldId(CH+"knowledge/java-core.hashmap.k"+kIndex(i)));}
            String[] files={"MutableKeyDemo","ResizeBitDemo"},outputs={"saved\nnull\n1\nsaved","1:1->1\n17:1->17\n33:1->1\n49:1->17\n65537:0->0"};
            for(int i=0;i<2;i++){CodeQuestion e=new CodeQuestion();e.setTitle(files[i]+" · Java17实验");e.setLanguage("java");e.setDifficulty(1);e.setDescription("先预测，再运行完整文件。可信本地执行：javac "+files[i]+".java && java "+files[i]+"。网页不提供Java沙箱判题，不自动记成绩。"+(i==1?" 位拆分为数学模型，非内部table观测。":" 修改参与hash/equals的键会破坏查询；record版本作对照。"));e.setCodeTemplate(file(files[i]+".java.txt"));e.setExampleOutput(outputs[i]);e.setTestCases("[]");e.setStatus(1);e.setDuration(10);e.setPassCount(0);e.setSubmitCount(0);e.setTags("JAVA,HASHMAP,"+KEY);add("shared/hashmap/exercise/"+files[i],"CODE",exercises,e);link("CODE_QUESTION",e.getId(),oldId(CH+"knowledge/java-core.hashmap.k"+(i+1)));}
            jdbc.update("UPDATE interview_seed_pack SET initialized=1 WHERE seed_key=?",KEY);
        }
        validateShared();
        String prefix="user/"+actor+"/hashmap/";
        boolean activated=jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_activation WHERE seed_key=? AND user_id=?",Integer.class,KEY,actor)>0;
        if(!activated){if(jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource WHERE seed_key=? AND resource_key LIKE ?",Integer.class,KEY,prefix+"%")!=0)throw new BusinessException(409,"个人修订索引不完整");long doc=resource("shared/hashmap/document",docs).getId();
            for(int i=0;i<KEYS.length;i++){LearningFlashcard f=new LearningFlashcard();f.setUserId(actor);f.setChapterId(chapterId);f.setPathId(ch.getPathId());f.setCategoryId(category);f.setDocId(doc);f.setFront(TITLES[i]);f.setBack(ANSWERS[i]);f.setTags("JAVA,HASHMAP,S,"+KEY);f.setSourceType("IMPORT");f.setDifficulty(2);f.setReviewCount(0);f.setReviewInterval(0);f.setEaseFactor(250);add(prefix+"card/"+KEYS[i],"FLASHCARD",cards,f);link("FLASHCARD",f.getId(),oldId(CH+"knowledge/java-core.hashmap.k"+kIndex(i)));}
            LearningNote n=new LearningNote();n.setUserId(actor);n.setChapterId(chapterId);n.setPathId(ch.getPathId());n.setTitle("HashMap · 深度修订个人速记");n.setContent("## 我的闭卷解释\n\n## 实验预测与观察\n\n## 仍讲不清的追问\n\n仅模板，未预置学习结论。");add(prefix+"note","NOTE",notes,n);
            jdbc.update("INSERT INTO interview_seed_activation(seed_key,user_id,goal_id,activated_at) VALUES(?,?,?,CURRENT_TIMESTAMP)",KEY,actor,original.status(actor).get("goalId"));
        }
        return Map.of("version",KEY,"activated",true,"chapterId",chapterId,"revision",revision(actor,chapterId));
    }
    private void requireLink(String type,long rid,long kid){if(jdbc.queryForObject("SELECT COUNT(*) FROM resource_knowledge_mapping WHERE resource_type=? AND resource_id=? AND knowledge_id=? AND status='ACCEPTED' AND deleted=0",Integer.class,type,rid,kid)!=1)throw new BusinessException(409,"修订知识关联损坏，未自动重建");}
    private void exactKeys(String prefix,Set<String> expected){Set<String> actual=new HashSet<>(jdbc.queryForList("SELECT resource_key FROM interview_seed_resource WHERE seed_key=? AND resource_key LIKE ?",String.class,KEY,prefix+"%"));if(!actual.equals(expected))throw new BusinessException(409,"修订索引缺失或冲突");}
    private void validateShared(){Set<String> expected=new HashSet<>();expected.add("shared/hashmap/document");var d=resource("shared/hashmap/document",docs);if(!Integer.valueOf(1).equals(d.getStatus()))throw new BusinessException(409,"修订文档未发布");for(int k=1;k<=3;k++)requireLink("DOC",d.getId(),oldId(CH+"knowledge/java-core.hashmap.k"+k));for(int i=0;i<KEYS.length;i++){String key="shared/hashmap/question/"+KEYS[i];expected.add(key);var q=resource(key,questions);if(!Integer.valueOf(1).equals(q.getStatus())||!d.getId().equals(q.getDocId()))throw new BusinessException(409,"修订题目关系冲突");requireLink("QUIZ",q.getId(),oldId(CH+"knowledge/java-core.hashmap.k"+kIndex(i)));}int i=1;for(String key:List.of("MutableKeyDemo","ResizeBitDemo")){expected.add("shared/hashmap/exercise/"+key);var e=resource("shared/hashmap/exercise/"+key,exercises);if(!Integer.valueOf(1).equals(e.getStatus()))throw new BusinessException(409,"修订练习未发布");requireLink("CODE_QUESTION",e.getId(),oldId(CH+"knowledge/java-core.hashmap.k"+i++));}exactKeys("shared/",expected);}
    @Transactional(readOnly=true)
    public Map<String,Object> revision(Long actor,Long chapterId) {
        user(actor);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_activation WHERE seed_key=? AND user_id=?",Integer.class,KEY,actor)==0)return null;
        if(oldId(CH+"chapter")!=chapterId)return null;
        validateShared();LearningChapter ch=chapters.selectById(chapterId);if(ch==null)throw new BusinessException(404,"章节不存在");var path=paths.selectById(ch.getPathId());if(path==null||!Integer.valueOf(1).equals(path.getStatus())||(path.getOwnerUserId()!=null&&path.getOwnerUserId()!=0&&!actor.equals(path.getOwnerUserId())))throw new BusinessException(403,"无权访问章节");
        DocDocument doc=resource("shared/hashmap/document",docs);documentAccess.requireRead(doc,actor);String prefix="user/"+actor+"/hashmap/";Set<String> expected=new HashSet<>();expected.add(prefix+"note");for(String key:KEYS)expected.add(prefix+"card/"+key);exactKeys("user/"+actor+"/",expected);List<Map<String,Object>> qs=new ArrayList<>();
        for(int i=0;i<KEYS.length;i++){var q=resource("shared/hashmap/question/"+KEYS[i],questions);Map<String,Object> row=new LinkedHashMap<>();row.put("id",q.getId());row.put("title",q.getTitle());row.put("content",q.getContent());row.put("answer",q.getAnswer());row.put("explanation",q.getExplanation());row.put("canonicalKey",KEY+"/hashmap/"+KEYS[i]);row.put("parentKey",i==0?null:KEY+"/hashmap/"+KEYS[i-1]);row.put("importance","S");row.put("depth","Q"+i);qs.add(row);var f=resource(prefix+"card/"+KEYS[i],cards);if(!actor.equals(f.getUserId())||!chapterId.equals(f.getChapterId()))throw new BusinessException(409,"个人卡片归属冲突");}
        for(int i=0;i<KEYS.length;i++){var f=resource(prefix+"card/"+KEYS[i],cards);requireLink("FLASHCARD",f.getId(),oldId(CH+"knowledge/java-core.hashmap.k"+kIndex(i)));if(!ch.getPathId().equals(f.getPathId())||!doc.getId().equals(f.getDocId()))throw new BusinessException(409,"个人卡片来源冲突");}
        var n=resource(prefix+"note",notes);if(!actor.equals(n.getUserId())||!chapterId.equals(n.getChapterId())||!ch.getPathId().equals(n.getPathId()))throw new BusinessException(409,"个人笔记归属冲突");
        List<KgEntity> ks=new ArrayList<>();for(int i=1;i<=3;i++){var k=knowledge.selectById(oldId(CH+"knowledge/java-core.hashmap.k"+i));if(k==null)throw new BusinessException(409,"原知识已删除");ks.add(k);}
        var related=knowledge.selectById(oldId("shared/java-core/chapter/java-core.generics/knowledge/java-core.generics.k2"));
        Map<String,Object> out=new LinkedHashMap<>();out.put("version",KEY);out.put("content",doc.getContent());out.put("docId",doc.getId());out.put("docTitle",doc.getTitle());out.put("questions",qs);out.put("exercises",List.of(resource("shared/hashmap/exercise/MutableKeyDemo",exercises),resource("shared/hashmap/exercise/ResizeBitDemo",exercises)));out.put("knowledge",ks);out.put("relatedKnowledge",related==null?List.of():List.of(related));out.put("notes",List.of(n));out.put("flashcardCount",KEYS.length);return out;
    }
}
