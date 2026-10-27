package com.knowflow.flashcards;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class FlashcardAdditiveMigrationTest {
    @Test
    void repeatedUpgradePreservesLegacyIdsDueGradesAndHistory() {
        var source =
                new DriverManagerDataSource(
                        "jdbc:h2:mem:flashcard_migration_fixture;DB_CLOSE_DELAY=-1;MODE=MySQL",
                        "sa",
                        "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute(
                "CREATE TABLE learning_flashcard(id BIGINT PRIMARY KEY,front TEXT,back"
                    + " TEXT,next_review_time TIMESTAMP,review_count INT)");
        jdbc.execute("CREATE TABLE learning_event(id BIGINT PRIMARY KEY,metadata TEXT)");
        jdbc.update(
                "INSERT INTO learning_flashcard VALUES(5000000000,'legacy question','legacy"
                    + " answer','2030-01-02 03:04:00',7)");
        jdbc.update("INSERT INTO learning_event VALUES(1,'{\"quality\":3,\"legacy\":true}')");
        var before =
                jdbc.queryForList(
                        "SELECT id,front,back,next_review_time,review_count FROM"
                            + " learning_flashcard");
        var events = jdbc.queryForList("SELECT * FROM learning_event");
        var schema = new FlashcardSchedulingSchema(jdbc);
        schema.initialize();
        schema.initialize();
        var exchange = new FlashcardExchangeService(jdbc, null, new com.fasterxml.jackson.databind.ObjectMapper());
        exchange.initialize();
        exchange.initialize();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM learning_flashcard_import_source", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM learning_flashcard_import_receipt", Integer.class));
        assertEquals(
                before,
                jdbc.queryForList(
                        "SELECT id,front,back,next_review_time,review_count FROM"
                            + " learning_flashcard"));
        assertEquals(events, jdbc.queryForList("SELECT * FROM learning_event"));
        assertNull(
                jdbc.queryForObject(
                        "SELECT fsrs_state FROM learning_flashcard WHERE id=5000000000",
                        String.class));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT fsrs_revision FROM learning_flashcard WHERE id=5000000000",
                        Long.class));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM learning_flashcard_settings", Integer.class));
    }
}
