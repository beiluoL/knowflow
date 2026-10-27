package com.knowflow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 主动回忆会话表运行时幂等迁移：为已存在的 H2 / MySQL 库补列。
 * 仅追加可空列，不修改历史数据与既有轮次结果。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WbRecallSessionSchema {

    private final JdbcTemplate jdbc;

    @PostConstruct
    void initialize() {
        try {
            jdbc.execute((ConnectionCallback<Void>) connection -> {
                Set<String> columns = new HashSet<>();
                try (var ps = connection.prepareStatement("SELECT * FROM wb_recall_session WHERE 1=0");
                     var rs = ps.executeQuery()) {
                    var meta = rs.getMetaData();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        columns.add(meta.getColumnName(i).toLowerCase(Locale.ROOT));
                    }
                }
                try (var statement = connection.createStatement()) {
                    if (!columns.contains("source_type")) {
                        statement.execute("ALTER TABLE wb_recall_session ADD COLUMN source_type VARCHAR(20) DEFAULT 'PASTE'");
                    }
                    if (!columns.contains("archived")) {
                        statement.execute("ALTER TABLE wb_recall_session ADD COLUMN archived INT DEFAULT 0");
                    }
                    if (!columns.contains("round1_draft")) {
                        statement.execute("ALTER TABLE wb_recall_session ADD COLUMN round1_draft TEXT");
                    }
                    if (!columns.contains("round2_draft")) {
                        statement.execute("ALTER TABLE wb_recall_session ADD COLUMN round2_draft TEXT");
                    }
                    if (!columns.contains("round3_draft")) {
                        statement.execute("ALTER TABLE wb_recall_session ADD COLUMN round3_draft TEXT");
                    }
                    if (!columns.contains("gap_json")) {
                        statement.execute("ALTER TABLE wb_recall_session ADD COLUMN gap_json TEXT");
                    }
                }
                return null;
            });
        } catch (Exception ex) {
            log.warn("wb_recall_session 运行时补列失败：{}", ex.getMessage());
        }
    }
}
