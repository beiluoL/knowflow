package com.knowflow.interview.generation;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 只在独立内存库验证增量脚本可重复执行、中文 COMMENT 完整；不触碰真实持久库。 */
class InterviewTrainerMigrationTest {

    @Test
    void trainerTablesAreAdditiveRepeatableAndDocumented() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:phase1_trainer_migration;DB_CLOSE_DELAY=-1;MODE=MySQL", "sa", "");
        var jdbc = new JdbcTemplate(ds);
        new ResourceDatabasePopulator(new ClassPathResource("db/h2/schema.sql")).execute(ds);
        for (int repeat = 0; repeat < 2; repeat++) {
            new ResourceDatabasePopulator(new ClassPathResource("db/h2/migration/V20261101__interview_trainer.sql")).execute(ds);
        }
        for (String table : List.of("INTERVIEW_GENERATION_TASK", "INTERVIEW_GENERATED_QUESTION")) {
            assertNotEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME=?", Integer.class, table));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME=? AND (REMARKS IS NULL OR REMARKS='')", Integer.class, table));
            assertNotNull(jdbc.queryForObject("SELECT REMARKS FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME=?", String.class, table));
        }
        // 不产生历史数据：重复执行前后均不应有任何自动回填行。
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM interview_generation_task", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM interview_generated_question", Integer.class));
    }
}
