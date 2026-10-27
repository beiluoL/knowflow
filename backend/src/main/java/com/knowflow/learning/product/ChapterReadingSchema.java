package com.knowflow.learning.product;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;

/** Additive fields in the existing chapter record. No historical completion is rewritten. */
@Component @RequiredArgsConstructor
public class ChapterReadingSchema {
    private final JdbcTemplate jdbc;
    @PostConstruct void initialize() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            Set<String> columns = new HashSet<>();
            boolean nullableCompletion = false;
            String completionDefault = null;
            try (var ps = connection.prepareStatement("SELECT * FROM learning_user_chapter WHERE 1=0"); var rs = ps.executeQuery()) {
                var meta = rs.getMetaData();
                for (int i=1; i<=meta.getColumnCount(); i++) {
                    columns.add(meta.getColumnName(i).toLowerCase(Locale.ROOT));
                    if (meta.getColumnName(i).equalsIgnoreCase("complete_time")) {
                        nullableCompletion = meta.isNullable(i) == java.sql.ResultSetMetaData.columnNullable;
                        try (var detail = connection.getMetaData().getColumns(connection.getCatalog(),meta.getSchemaName(i),meta.getTableName(i),meta.getColumnName(i))) {
                            if (detail.next()) completionDefault = detail.getString("COLUMN_DEF");
                        }
                    }
                }
            }
            // 旧MySQL5.7可能把未声明NULL的TIMESTAMP建成NOT NULL；仅改列定义，不改历史值。
            if (!nullableCompletion || (completionDefault != null && !completionDefault.equalsIgnoreCase("NULL"))) try (var st = connection.createStatement()) {
                if (connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL"))
                    st.execute("ALTER TABLE learning_user_chapter MODIFY COLUMN complete_time TIMESTAMP NULL DEFAULT NULL COMMENT '用户标记读完时间，进度记录为NULL'");
                else
                    st.execute("ALTER TABLE learning_user_chapter ALTER COLUMN complete_time TIMESTAMP NULL DEFAULT NULL");
            }
            Map<String,String> fields = new LinkedHashMap<>();
            fields.put("reading_anchor", "VARCHAR(255) DEFAULT NULL COMMENT '章节阅读标题位置，不表示掌握'");
            fields.put("reading_revision", "BIGINT NOT NULL DEFAULT 0 COMMENT '阅读位置乐观锁版本'");
            fields.put("reading_saved_at", "TIMESTAMP NULL DEFAULT NULL COMMENT '阅读位置保存时间'");
            fields.put("reading_presentation_hash", "VARCHAR(64) DEFAULT NULL COMMENT '用户阅读展示版本指纹，仅用于本人书签坐标'");
            fields.put("reading_content_hash", "VARCHAR(64) DEFAULT NULL COMMENT '保存时原章节内容摘要'");
            try (var st = connection.createStatement()) {
                for (var field : fields.entrySet()) if (!columns.contains(field.getKey()))
                    st.execute("ALTER TABLE learning_user_chapter ADD COLUMN " + field.getKey() + " " + field.getValue());
            }
            return null;
        });
    }
}
