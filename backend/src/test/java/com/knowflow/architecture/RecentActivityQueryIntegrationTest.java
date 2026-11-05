package com.knowflow.architecture;

import com.knowflow.exam.contract.RecentExamActivityQuery;
import com.knowflow.interview.contract.RecentInterviewActivityQuery;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证近期活动查询的模块注册及持久化行为。
 */
@SpringBootTest
@ActiveProfiles("local")
class RecentActivityQueryIntegrationTest {
    @Autowired JdbcTemplate sql;
    @Autowired RecentExamActivityQuery exams;
    @Autowired RecentInterviewActivityQuery interviews;

    @Test void preservesOwnerOrderingThreeRowWindowAndLogicalDelete() {
        long owner = 813101L, other = 813102L;
        for (int index = 0; index < 5; index++) {
            var time = Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(index));
            sql.update("INSERT INTO exam_attempt(paper_id,user_id,status,create_time,deleted) VALUES(1,?,?,?,0)", owner, "STATUS_" + index, time);
            sql.update("INSERT INTO mock_interview_session(user_id,track,question_count,questions_json,create_time,deleted) VALUES(?,'JAVA_BASIC',1,'[]',?,0)", owner, time);
        }
        sql.update("INSERT INTO exam_attempt(paper_id,user_id,status,deleted) VALUES(1,?,'OTHER',0)", other);
        sql.update("INSERT INTO exam_attempt(paper_id,user_id,status,create_time,deleted) VALUES(1,?,'DELETED',CURRENT_TIMESTAMP,1)", owner);
        sql.update("INSERT INTO mock_interview_session(user_id,track,question_count,questions_json,deleted) VALUES(?,'JAVA_BASIC',1,'[]',1)", other);
        assertEquals(List.of("STATUS_4", "STATUS_3", "STATUS_2"), exams.statuses(owner));
        assertEquals(List.of("OTHER"), exams.statuses(other));
        assertEquals(3, interviews.boundedCount(owner));
        assertEquals(0, interviews.boundedCount(other));
        assertTrue(exams.statuses(813103L).isEmpty());
    }
}
