package com.knowflow.service;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 复习卡类型专属字段的增量迁移：只补列，不改旧数据、不改调度字段。
 * H2（本地文件库 init-mode=never）与 MySQL（init-mode=auto）均适用。
 */
@Component
@RequiredArgsConstructor
public class WbReviewCardSchema {

    private final JdbcTemplate jdbc;

    @PostConstruct
    void initialize() {
        jdbc.execute(
                (ConnectionCallback<Void>)
                        connection -> {
                            Set<String> columns = new HashSet<>();
                            try (var ps = connection.prepareStatement("SELECT * FROM wb_review_card WHERE 1=0");
                                    var rs = ps.executeQuery()) {
                                var meta = rs.getMetaData();
                                for (int i = 1; i <= meta.getColumnCount(); i++) {
                                    columns.add(meta.getColumnName(i).toLowerCase(Locale.ROOT));
                                }
                            }
                            try (var statement = connection.createStatement()) {
                                if (!columns.contains("hint")) {
                                    statement.execute("ALTER TABLE wb_review_card ADD COLUMN hint VARCHAR(1000)");
                                }
                                if (!columns.contains("tags")) {
                                    statement.execute("ALTER TABLE wb_review_card ADD COLUMN tags VARCHAR(500)");
                                }
                                if (!columns.contains("extra_json")) {
                                    statement.execute("ALTER TABLE wb_review_card ADD COLUMN extra_json TEXT");
                                }
                            }
                            return null;
                        });
    }
}
