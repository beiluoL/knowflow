package com.knowflow.flashcards;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/** Additive migration only: no existing card identity, due time or historical grade is updated. */
@Component
@RequiredArgsConstructor
public class FlashcardSchedulingSchema {
    private final JdbcTemplate jdbc;

    @PostConstruct
    void initialize() {
        jdbc.execute(
                (ConnectionCallback<Void>)
                        connection -> {
                            Set<String> columns = new HashSet<>();
                            try (var ps =
                                            connection.prepareStatement(
                                                    "SELECT * FROM learning_flashcard WHERE 1=0");
                                    var rs = ps.executeQuery()) {
                                var meta = rs.getMetaData();
                                for (int i = 1; i <= meta.getColumnCount(); i++)
                                    columns.add(meta.getColumnName(i).toLowerCase(Locale.ROOT));
                            }
                            try (var statement = connection.createStatement()) {
                                if (!columns.contains("fsrs_state"))
                                    statement.execute(
                                            "ALTER TABLE learning_flashcard ADD COLUMN fsrs_state"
                                                    + " TEXT");
                                if (!columns.contains("fsrs_revision"))
                                    statement.execute(
                                            "ALTER TABLE learning_flashcard ADD COLUMN"
                                                    + " fsrs_revision BIGINT DEFAULT 0 NOT NULL");
                                statement.execute(
                                        "CREATE TABLE IF NOT EXISTS learning_flashcard_settings"
                                            + " (user_id BIGINT PRIMARY KEY,settings_json TEXT NOT"
                                            + " NULL,revision BIGINT NOT NULL DEFAULT 1)");
                            }
                            return null;
                        });
    }
}
