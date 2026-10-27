package com.knowflow.learning.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.learning.actions.LearningActionProgress;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

/** Same-transaction capture for producers already using recordRequired; no event re-emission. */
@Component @RequiredArgsConstructor
public class LearningEvidenceAdapter {
    private final LearningEvidenceService evidence;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
    public void capture(LearningActionProgress.Committed signal) {
        var e=signal.event();if(e==null||e.getUserId()==null||e.getId()==null)return;
        try {
            var meta=json.readTree(e.getMetadata()==null?"{}":e.getMetadata());
            if("QUESTION_ANSWERED".equals(e.getEventType())&&"QUIZ".equals(e.getResourceType())&&meta.path("answerRecordId").isIntegralNumber()) {
                long answer=meta.path("answerRecordId").longValue();
                if(e.getResourceId()==null||jdbc.queryForObject("SELECT COUNT(*) FROM quiz_answer_record WHERE id=? AND user_id=? AND question_id=? AND deleted=0",Long.class,answer,e.getUserId(),e.getResourceId())!=1L)
                    throw new com.knowflow.exception.BusinessException(409,"原始作答与学习事件引用不一致");
                evidence.captureRequired(e.getUserId(),"QUIZ_ANSWER",answer);
            }
            else if("FLASHCARD_REVIEWED".equals(e.getEventType())&&"FLASHCARD".equals(e.getResourceType()))
                evidence.captureRequired(e.getUserId(),"FLASHCARD_REVIEW",e.getId());
            else if("INTERVIEW_COMPLETED".equals(e.getEventType())&&"INTERVIEW".equals(e.getResourceType()))
                for(long turn:jdbc.queryForList("SELECT id FROM mock_interview_v2_turn WHERE session_id=? AND user_id=? ORDER BY id",Long.class,e.getResourceId(),e.getUserId()))evidence.captureRequired(e.getUserId(),"INTERVIEW_TURN",turn);
        } catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new IllegalStateException("Invalid evidence source event",ex);}
    }
}
