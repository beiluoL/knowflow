package com.knowflow.interview.bridge;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class InterviewRemediationMigrationTest {
    @Test void h2MigrationCanRepeatPreservesHistoryAndEnforcesRealReferences() throws Exception {
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+ UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(source);
        for(String table:new String[]{"mock_interview_session","kg_entity","task","sys_user"}) {
            jdbc.execute("CREATE TABLE "+table+"(id BIGINT PRIMARY KEY)");
            jdbc.update("INSERT INTO "+table+"(id) VALUES (1)");
        }
        try(var connection=source.getConnection()) {
            var migration=new ClassPathResource("db/h2/migration/V20261017__interview_remediation.sql");
            ScriptUtils.executeSqlScript(connection,migration);
            jdbc.update("INSERT INTO interview_remediation(session_id,knowledge_id,user_id,task_id,request_id) VALUES(1,1,1,1,'first')");
            ScriptUtils.executeSqlScript(connection,migration);
        }
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM interview_remediation",Integer.class));
        assertNotNull(jdbc.queryForObject("SELECT REMARKS FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='INTERVIEW_REMEDIATION'",String.class));
        assertEquals(6,jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='INTERVIEW_REMEDIATION' AND REMARKS IS NOT NULL",Integer.class));
        for(String column:new String[]{"session_id","knowledge_id","user_id","task_id"}) {
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE interview_remediation SET "+column+"=999"));
        }
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO interview_remediation(session_id,knowledge_id,user_id,task_id,request_id) VALUES(1,1,1,1,'different')"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("DELETE FROM task WHERE id=1"));
    }
}
