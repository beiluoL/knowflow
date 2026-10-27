package com.knowflow.interview.bridge;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 独立内存库，只验证已复现的设计令牌长度与可重复扩容。 */
class TaskTagColorMigrationTest {
    @Test void repeatedColorCapacityUpgradePreservesExistingTagsAndAcceptsTokenDefault() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:tag_color_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE task_tag(id BIGINT PRIMARY KEY, color VARCHAR(20) DEFAULT '#aaa')");
        jdbc.update("INSERT INTO task_tag(id,color) VALUES(1,'#123456')");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO task_tag(id,color) VALUES(2,'var(--kb-muted-foreground)')"));
        var upgrade = new ResourceDatabasePopulator(new ClassPathResource("db/h2/migration/V20261018__task_tag_color_capacity.sql"));
        upgrade.execute(source);
        upgrade.execute(source);
        jdbc.update("INSERT INTO task_tag(id) VALUES(2)");
        assertEquals("#123456", jdbc.queryForObject("SELECT color FROM task_tag WHERE id=1", String.class));
        assertEquals("var(--kb-muted-foreground)", jdbc.queryForObject("SELECT color FROM task_tag WHERE id=2", String.class));
        assertEquals(64, jdbc.queryForObject("SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='TASK_TAG' AND COLUMN_NAME='COLOR'", Integer.class));
    }
}
