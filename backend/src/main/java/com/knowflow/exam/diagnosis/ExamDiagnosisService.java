package com.knowflow.exam.diagnosis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.QuizQuestion;
import com.knowflow.exam.engine.PaperComposer;
import com.knowflow.exam.service.ExamService;
import com.knowflow.exam.vo.ExamVos;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.KgEntityMapper;
import com.knowflow.security.DocumentAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Read-only exam observations. Never writes Mastery or changes Evidence/NextAction semantics. */
@Service @RequiredArgsConstructor
public class ExamDiagnosisService {
    private final ExamService exams;
    private final PaperComposer composer;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final KgEntityMapper knowledge;
    private final DocumentAccessPolicy access;
    private static final Set<String> OBJECTIVE=Set.of("SINGLE_CHOICE","MULTIPLE_CHOICE","TRUE_FALSE","FILL_BLANK");
    private record History(Long attemptId,Long paperQuestionId,Long questionId,String type,String topic,
                           JsonNode snapshot,boolean answered,boolean graded,Boolean correct) {
        String identity() {
            String key=snapshot.path("canonicalKey").asText("");
            return key.isBlank() ? (questionId==null?"personal:"+paperQuestionId:"q:"+questionId) : "canonical:"+key;
        }
        String versionSignature() { return identity()+"|"+type+"|"+snapshot.toString(); }
        ExamDiagnosis.Evidence evidence() {
            return new ExamDiagnosis.Evidence(attemptId,paperQuestionId,snapshot.path("title").asText(""),
                    snapshot.hasNonNull("difficulty")?snapshot.path("difficulty").asInt():null,correct,answered,graded);
        }
    }
    @Transactional(readOnly=true)
    public ExamDiagnosis diagnose(Long attemptId,Long userId) {
        // Owner check precedes explicit submit-state gate and all auxiliary reads.
        ExamVos.ResultVO result=exams.getResult(attemptId,userId);
        if(result.getSubmitTime()==null) throw new BusinessException(409,"考试尚未交卷，不能生成诊断");
        List<ExamVos.ResultItemVO> items=result.getItems();
        List<History> history=history(userId,result.getSubmitTime(),attemptId);
        List<QuizQuestion> bank=composer.loadAccessibleBank();
        Map<String,List<ExamVos.ResultItemVO>> groups=items.stream().collect(Collectors.groupingBy(
                i->topic(i.getTopic()),LinkedHashMap::new,Collectors.toList()));
        List<ExamDiagnosis.Topic> topics=new ArrayList<>();
        for(var entry:groups.entrySet()) {
            String code=entry.getKey(); var current=entry.getValue();
            int answered=(int)current.stream().filter(i->answered(i.getUserAnswer())).count();
            int graded=(int)current.stream().filter(i->Boolean.TRUE.equals(i.getGraded())).count();
            int wrong=(int)current.stream().filter(i->answered(i.getUserAnswer())&&Boolean.TRUE.equals(i.getGraded())&&Boolean.FALSE.equals(i.getCorrect())).count();
            int ungraded=current.size()-graded,unanswered=current.size()-answered;
            int observed=(int)current.stream().filter(i->answered(i.getUserAnswer())&&Boolean.TRUE.equals(i.getGraded())&&i.getCorrect()!=null).count();
            List<String> notes=new ArrayList<>();
            notes.add("本次实际答错 "+wrong+" 题，未作答 "+unanswered+" 题，未评分 "+ungraded+" 题；三个状态分开统计。");
            if(observed<3) notes.add("本次有效已评分作答少于3条，知识点覆盖不足；不能据此判断全局掌握度。");
            if(ungraded>0) notes.add("未评分回答不作为用户能力不足证据；等待AI或Judge评估，不按0分或答错处理。");
            List<History> matching=history.stream().filter(h->code.equals(h.topic())).toList();
            Map<String,List<History>> byIdentity=matching.stream().filter(h->h.answered()&&h.graded()&&h.correct()!=null)
                    .collect(Collectors.groupingBy(History::identity,LinkedHashMap::new,Collectors.toList()));
            long repeated=byIdentity.values().stream().filter(rows->rows.stream().filter(h->Boolean.FALSE.equals(h.correct())).map(History::attemptId).distinct().count()>=2).count();
            long consecutive=byIdentity.values().stream().filter(rows->rows.size()>=2&&!rows.get(0).attemptId().equals(rows.get(1).attemptId())&&Boolean.FALSE.equals(rows.get(0).correct())&&Boolean.FALSE.equals(rows.get(1).correct())).count();
            if(repeated>0) notes.add("最近最多1000条已交卷作答中，"+repeated+"个题目身份在不同考试里重复答错。");
            if(consecutive>0) notes.add(consecutive+"个题目身份最近两次有效作答连续错误，建议先对照题目解析再复练。");
            long overdue=overdueExamMistakes(userId,code);
            if(overdue>0) notes.add("本主题关联的考试错题有 "+overdue+" 条至少7天没有复习；建议先复习解析，再完成基础复测。");
            notes.add("仅凭作答结果不能区分概念、原理和表达问题；本报告不编造六维诊断。");
            LinkedHashMap<String,ExamDiagnosis.Candidate> candidates=new LinkedHashMap<>();
            bank.stream().filter(q->OBJECTIVE.contains(q.getQuestionType())&&code.equals(topic(PaperComposer.topicOfQuestion(q))))
                    .sorted(Comparator.comparing(QuizQuestion::getId)).forEach(q->{
                        String identity=q.getCanonicalKey()==null||q.getCanonicalKey().isBlank()?"q:"+q.getId():"canonical:"+q.getCanonicalKey().trim();
                        candidates.putIfAbsent(identity,new ExamDiagnosis.Candidate("QUIZ:"+q.getId(),q.getId(),q.getTitle(),q.getDifficulty(),q.getQuestionType()));
                    });
            List<ExamDiagnosis.Candidate> picks=candidates.values().stream().limit(3).toList();
            if(picks.isEmpty()) notes.add("当前可读已发布题库没有本主题的客观题候选，不伪造复测题目或数量。");
            List<ExamDiagnosis.Material> materials=materials(userId,matching.stream().map(History::questionId).filter(Objects::nonNull).distinct().toList());
            if(materials.isEmpty()) notes.add("缺少可读的已确认知识映射，可先使用本卷逐题解析；主题编码不冒充知识实体ID。");
            topics.add(new ExamDiagnosis.Topic(code,answered,graded,wrong,unanswered,ungraded,
                    observed<3?"INSUFFICIENT":"OBSERVED",notes,matching.stream().limit(30).map(History::evidence).toList(),picks,materials,picks.size(),
                    wrong>0&&result.getSubmitTime()!=null?result.getSubmitTime().plusDays(1):null));
        }
        Double accuracy=accuracy(items);
        List<History> now=history.stream().filter(h->attemptId.equals(h.attemptId())).toList();
        Long prior=history.stream().map(History::attemptId).filter(id->!attemptId.equals(id)).findFirst().orElse(null);
        List<History> before=prior==null?List.of():history.stream().filter(h->prior.equals(h.attemptId())).toList();
        boolean comparable=prior!=null&&!now.isEmpty()&&signatures(now).equals(signatures(before));
        Double priorAccuracy=historyAccuracy(before);
        String note=prior==null?"没有更早的已提交考试记录，不能编造历史变化。":comparable?
                "题目与内容版本相同，可对照客观作答比例；重复训练和查看解析后的变化不等于全局Mastery提升。":
                "题目覆盖或内容版本不同，历史正确率仅作记录，不据此宣称进步。";
        int objectiveAnswered=(int)items.stream().filter(i->OBJECTIVE.contains(i.getQuestionType())&&answered(i.getUserAnswer())&&Boolean.TRUE.equals(i.getGraded())&&i.getCorrect()!=null).count();
        int objectiveCorrect=(int)items.stream().filter(i->OBJECTIVE.contains(i.getQuestionType())&&answered(i.getUserAnswer())&&Boolean.TRUE.equals(i.getGraded())&&Boolean.TRUE.equals(i.getCorrect())).count();
        return new ExamDiagnosis("exam-diagnosis-v1",attemptId,"EXAM_OBSERVATIONS_NOT_GLOBAL_MASTERY",objectiveAnswered,objectiveCorrect,accuracy,
                (int)items.stream().filter(i->!answered(i.getUserAnswer())).count(),(int)items.stream().filter(i->!Boolean.TRUE.equals(i.getGraded())).count(),topics,
                new ExamDiagnosis.Comparison(prior,comparable,accuracy,priorAccuracy,note));
    }
    private List<History> history(Long userId,LocalDateTime until,Long currentId) {
        if(until==null) return List.of();
        return jdbc.query("""
            SELECT a.attempt_id,a.paper_question_id,p.question_id,p.question_type,p.topic,p.snapshot,a.user_answer,a.graded,a.is_correct
              FROM exam_answer a JOIN exam_attempt t ON t.id=a.attempt_id JOIN exam_paper_question p ON p.id=a.paper_question_id
             WHERE t.user_id=? AND t.submit_time IS NOT NULL AND (t.submit_time<? OR (t.submit_time=? AND t.id=?)) AND t.deleted=0 AND a.deleted=0 AND p.deleted=0
             ORDER BY t.submit_time DESC,t.id DESC,a.ordinal ASC LIMIT 1000
            """,(rs,row)->{
                JsonNode snapshot;
                try{snapshot=json.readTree(rs.getString("snapshot"));if(snapshot==null||!snapshot.isObject())throw new IllegalArgumentException("snapshot object required");}catch(Exception ex){throw new BusinessException(500,"考试快照无效，无法生成可追溯诊断");}
                Object qid=rs.getObject("question_id"),correct=rs.getObject("is_correct");
                return new History(rs.getLong("attempt_id"),rs.getLong("paper_question_id"),qid==null?null:((Number)qid).longValue(),rs.getString("question_type"),topic(rs.getString("topic")),snapshot,
                        answered(rs.getString("user_answer")),rs.getInt("graded")==1,correct==null?null:((Number)correct).intValue()==1);
            },userId,until,until,currentId);
    }
    private List<ExamDiagnosis.Material> materials(Long userId,List<Long> ids) {
        LinkedHashMap<Long,ExamDiagnosis.Material> result=new LinkedHashMap<>();
        for(Long id:ids) {
            for(Long kid:jdbc.queryForList("SELECT knowledge_id FROM resource_knowledge_mapping WHERE resource_type='QUIZ' AND resource_id=? AND status='ACCEPTED' AND deleted=0",Long.class,id)) {
                var entity=knowledge.selectById(kid);
                if(access.canReadKnowledge(entity,userId)) result.putIfAbsent(kid,new ExamDiagnosis.Material(kid,entity.getName()));
            }
        }
        return result.values().stream().limit(10).toList();
    }
    private long overdueExamMistakes(Long userId,String code) {
        LocalDateTime cutoff=LocalDateTime.now().minusDays(7);
        return jdbc.query("SELECT q.topic_code,q.tags,m.last_review_time,m.create_time FROM learning_mistake m JOIN quiz_question q ON q.id=m.question_id WHERE m.user_id=? AND m.source='EXAM' AND m.mastered=0 AND m.deleted=0",(rs,index)->{
            QuizQuestion q=new QuizQuestion();q.setTopicCode(rs.getString("topic_code"));q.setTags(rs.getString("tags"));
            var reviewed=rs.getTimestamp("last_review_time");var created=rs.getTimestamp("create_time");
            LocalDateTime last=reviewed!=null?reviewed.toLocalDateTime():created==null?null:created.toLocalDateTime();
            return code.equals(topic(PaperComposer.topicOfQuestion(q)))&&last!=null&&last.isBefore(cutoff);
        },userId).stream().filter(Boolean::booleanValue).count();
    }
    private static boolean answered(String answer){return answer!=null&&!answer.isBlank();}
    private static String topic(String value){return value==null||value.isBlank()?"UNMAPPED":value.trim().toUpperCase(Locale.ROOT);}
    private static List<String> signatures(List<History> rows){return rows.stream().map(History::versionSignature).sorted().toList();}
    private static Double accuracy(List<ExamVos.ResultItemVO> items){
        var valid=items.stream().filter(i->OBJECTIVE.contains(i.getQuestionType())&&answered(i.getUserAnswer())&&Boolean.TRUE.equals(i.getGraded())&&i.getCorrect()!=null).toList();
        return valid.isEmpty()?null:Math.round(valid.stream().filter(i->Boolean.TRUE.equals(i.getCorrect())).count()*1000.0/valid.size())/10.0;
    }
    private static Double historyAccuracy(List<History> rows){
        var valid=rows.stream().filter(h->OBJECTIVE.contains(h.type())&&h.answered()&&h.graded()&&h.correct()!=null).toList();
        return valid.isEmpty()?null:Math.round(valid.stream().filter(h->Boolean.TRUE.equals(h.correct())).count()*1000.0/valid.size())/10.0;
    }
}
