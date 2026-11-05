package com.knowflow.learning.evidence;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

/** 可重复的增量迁移，不回填历史答案或知识映射。 */
@Component
@RequiredArgsConstructor
public class LearningEvidenceSchema {
    private final JdbcTemplate jdbc;

    @PostConstruct
    public void initialize() {
        jdbc.execute(
                (ConnectionCallback<Void>)
                        connection -> {
                            String dialect =
                                    connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("H2")
                                            ? "h2"
                                            : "mysql";
                            ScriptUtils.executeSqlScript(
                                    connection,
                                    new ClassPathResource(
                                            "db/" + dialect + "/migration/V20261025__learning_evidence_receipt.sql"));
                            return null;
                        });
    }
}
