package com.knowflow.exam.engine;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.HashSet;
import java.util.Set;

/** Real owner-scoped attempted identities. Pending/platform failures never become wrong-answer evidence. */
@Component
@RequiredArgsConstructor
public class QuestionHistoryLookup {
    private final JdbcTemplate jdbc;
    private static final String HISTORY="""

            SELECT question_id FROM quiz_answer_record WHERE user_id=? AND deleted=0 AND question_id IS NOT NULL
            UNION
            SELECT a.question_id FROM exam_answer a JOIN exam_attempt t ON t.id=a.attempt_id
             WHERE t.user_id=? AND t.deleted=0 AND a.deleted=0 AND a.question_id IS NOT NULL
               AND a.user_answer IS NOT NULL AND TRIM(a.user_answer)<>''
            UNION
            SELECT question_id FROM code_submission WHERE user_id=? AND deleted=0 AND submission_type='SUBMIT'
               AND judge_status IN ('ACCEPTED','WRONG_ANSWER','COMPILE_ERROR','RUNTIME_ERROR','TIME_LIMIT_EXCEEDED','MEMORY_LIMIT_EXCEEDED')
                        """;
    public Set<Long> answered(Long userId) {
        return new HashSet<>(jdbc.queryForList(HISTORY,Long.class,userId,userId,userId));
    }
    public Set<String> answeredCanonicalKeys(Long userId) {
        // Logical deletion/unpublishing cannot erase a user's earlier question identity.
        Set<String> result=new HashSet<>(jdbc.queryForList("SELECT DISTINCT q.canonical_key FROM quiz_question q JOIN ("+HISTORY+") h ON h.question_id=q.id WHERE q.canonical_key IS NOT NULL AND TRIM(q.canonical_key)<>''",String.class,userId,userId,userId));
        // The immutable exam identity survives later edits to the source's canonical key.
        var reader=new com.fasterxml.jackson.databind.ObjectMapper();
        for(String snapshot:jdbc.queryForList("SELECT p.snapshot FROM exam_answer a JOIN exam_attempt t ON t.id=a.attempt_id JOIN exam_paper_question p ON p.id=a.paper_question_id WHERE t.user_id=? AND t.deleted=0 AND a.deleted=0 AND p.deleted=0 AND a.user_answer IS NOT NULL AND TRIM(a.user_answer)<>''",String.class,userId)) {
            try {var node=reader.readTree(snapshot);String key=node==null?"":node.path("canonicalKey").asText("").trim();if(!key.isEmpty())result.add(key);}
            catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new com.knowflow.exception.BusinessException(500,"历史题目快照无效，无法安全排除已做身份");}
        }
        return result;
    }
}
