package com.knowflow.interview.speech;
import com.knowflow.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class SpeechV2AdmissionTest {
 JdbcTemplate jdbc;SpeechV2Admission admission;
 @BeforeEach void setup(){jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:admission_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));admission=new SpeechV2Admission(jdbc);}
 void table(){jdbc.execute("CREATE TABLE mock_interview_v2_state(session_id BIGINT PRIMARY KEY,user_id BIGINT,state VARCHAR(30),current_question_id VARCHAR(100),followup_depth INT,version BIGINT,current_prompt VARCHAR(1000))");}
 void state(String value){jdbc.update("INSERT INTO mock_interview_v2_state VALUES(1,7,?,'q1',0,0,'题目一')",value);}
 @Test void baseOnlyWithoutTableOrStateRemainsAllowed(){assertDoesNotThrow(()->admission.requireAllowed(7L,1L,"q1"));table();assertDoesNotThrow(()->admission.requireAllowed(7L,1L,"q1"));}
 @Test void blockedStatesRejectWhileParentCouldStillBeInProgress(){table();for(String s:new String[]{"PAUSED","EVALUATING","COMPLETED"}){state(s);assertEquals(409,assertThrows(BusinessException.class,()->admission.requireAllowed(7L,1L,"q1")).getCode());jdbc.update("DELETE FROM mock_interview_v2_state");}}
 @Test void answeringStatesAcceptOnlyCurrentQuestion(){table();for(String s:new String[]{"ASKING","ANSWERING","FOLLOW_UP"}){state(s);assertDoesNotThrow(()->admission.requireAllowed(7L,1L,"q1"));assertEquals(409,assertThrows(BusinessException.class,()->admission.requireAllowed(7L,1L,"q2")).getCode());jdbc.update("DELETE FROM mock_interview_v2_state");}}
 @Test void foreignUserDoesNotBypassParentOwnership(){table();state("ANSWERING");assertEquals(404,assertThrows(BusinessException.class,()->admission.requireAllowed(8L,1L,"q1")).getCode());}
 @Test void brokenSchemaIsNotSilentlyTreatedAsBaseMode(){jdbc.execute("CREATE TABLE mock_interview_v2_state(session_id BIGINT)");assertThrows(org.springframework.dao.DataAccessException.class,()->admission.requireAllowed(7L,1L,"q1"));}
}
