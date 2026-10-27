package com.knowflow.service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 技术英语 P0 双方言增量迁移。
 *
 * 与 NoteSchema / ChatManagementSchema 相同：在数据源发布前执行，
 * 保证已存在业务表的 MySQL（init-mode=auto 会跳过 schema.sql）也能拿到新表。
 * 只做 CREATE TABLE / CREATE INDEX，不 DROP、不修改既有业务表。
 */
public final class TechVocabSchema {

    private TechVocabSchema() {
    }

    public static void migrate(Connection conn) throws SQLException {
        // MySQL 存量库的默认字符集不一定是 utf8mb4（本地 5.7 默认 latin1），
        // 增量表必须显式声明字符集，否则中文含义/释义写入会报 Incorrect string value。
        // 与 db/mysql/schema.sql 保持一致：只声明 utf8mb4，排序规则沿用库/服务端默认，
        // 避免同库内增量表与全量脚本建出的表出现 collation 混用。
        String tableSuffix = isMySql(conn)
                ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
                : "";
        try (var st = conn.createStatement()) {
            // 公共词条：normalized_key + domain + sense_key 允许同形多义，deleted 纳入唯一键
            st.execute("""
                    CREATE TABLE IF NOT EXISTS tech_vocab_term (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        headword VARCHAR(200) NOT NULL,
                        normalized_key VARCHAR(200) NOT NULL,
                        term_type VARCHAR(20) NOT NULL DEFAULT 'WORD',
                        sense_key VARCHAR(100) NOT NULL DEFAULT 'default',
                        domain VARCHAR(40) NOT NULL,
                        full_form VARCHAR(300),
                        class_name VARCHAR(200),
                        part_of_speech VARCHAR(40),
                        ipa_us VARCHAR(200),
                        ipa_uk VARCHAR(200),
                        meaning_cn VARCHAR(300) NOT NULL,
                        definition_cn TEXT,
                        definition_en TEXT,
                        word_breakdown TEXT,
                        breakdown_note VARCHAR(500),
                        interview_en TEXT,
                        example_en TEXT,
                        example_cn TEXT,
                        aliases VARCHAR(500),
                        content_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
                        reference_source VARCHAR(1000),
                        license_note VARCHAR(500),
                        reviewed_at TIMESTAMP NULL,
                        create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        deleted INT DEFAULT 0,
                        CONSTRAINT uk_tech_term_sense UNIQUE (normalized_key, domain, sense_key, deleted)
                    )
                    """ + tableSuffix);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS tech_vocab_pronunciation (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        term_id BIGINT NOT NULL,
                        variant VARCHAR(30) NOT NULL,
                        reading_text VARCHAR(500),
                        locale VARCHAR(20),
                        reading_mode VARCHAR(30),
                        audio_url VARCHAR(1000),
                        audio_provider VARCHAR(100),
                        license_note VARCHAR(500),
                        verification_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
                        create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        deleted INT DEFAULT 0,
                        CONSTRAINT uk_tech_pron_variant UNIQUE (term_id, variant, deleted)
                    )
                    """ + tableSuffix);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS user_vocab_state (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        term_id BIGINT NOT NULL,
                        favorite INT NOT NULL DEFAULT 0,
                        learning_status VARCHAR(20) NOT NULL DEFAULT 'NEW',
                        linked_flashcard_id BIGINT,
                        last_opened_at TIMESTAMP NULL,
                        create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        deleted INT DEFAULT 0,
                        CONSTRAINT uk_user_vocab_term UNIQUE (user_id, term_id, deleted)
                    )
                    """ + tableSuffix);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS tech_vocab_concept_link (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        term_id BIGINT NOT NULL,
                        target_type VARCHAR(30) NOT NULL,
                        target_id BIGINT NOT NULL,
                        relation_type VARCHAR(30) NOT NULL DEFAULT 'RELATED',
                        create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        deleted INT DEFAULT 0,
                        CONSTRAINT uk_tech_link UNIQUE (term_id, target_type, target_id, relation_type, deleted)
                    )
                    """ + tableSuffix);
            // P2 个人候选词条：与公共词库隔离，只有管理员审核通过后才生成正式词条
            st.execute("""
                    CREATE TABLE IF NOT EXISTS tech_vocab_candidate (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        headword VARCHAR(200) NOT NULL,
                        normalized_key VARCHAR(200) NOT NULL,
                        term_type VARCHAR(20) NOT NULL DEFAULT 'WORD',
                        domain VARCHAR(40) NOT NULL,
                        full_form VARCHAR(300),
                        meaning_cn VARCHAR(300),
                        definition_cn TEXT,
                        source_type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
                        source_id BIGINT,
                        source_title VARCHAR(300),
                        source_context VARCHAR(500),
                        confidence INT,
                        extractor VARCHAR(10) NOT NULL DEFAULT 'RULE',
                        review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                        duplicate_term_id BIGINT,
                        term_id BIGINT,
                        review_note VARCHAR(500),
                        create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        deleted INT DEFAULT 0,
                        CONSTRAINT uk_tech_candidate UNIQUE (user_id, normalized_key, source_type, source_id, deleted)
                    )
                    """ + tableSuffix);
            ensureIndex(conn, st, "tech_vocab_term", "idx_tech_term_domain",
                    "CREATE INDEX idx_tech_term_domain ON tech_vocab_term (domain, content_status, deleted)");
            ensureIndex(conn, st, "tech_vocab_term", "idx_tech_term_type",
                    "CREATE INDEX idx_tech_term_type ON tech_vocab_term (term_type, content_status, deleted)");
            // 存量库补列：V3 词条详情增强字段，只新增可空列，不修改既有数据
            ensureColumn(conn, st, "tech_vocab_term", "breakdown_note",
                    "ALTER TABLE tech_vocab_term ADD COLUMN breakdown_note VARCHAR(500)");
            ensureColumn(conn, st, "tech_vocab_term", "interview_en",
                    "ALTER TABLE tech_vocab_term ADD COLUMN interview_en TEXT");
            ensureIndex(conn, st, "tech_vocab_pronunciation", "idx_tech_pron_term",
                    "CREATE INDEX idx_tech_pron_term ON tech_vocab_pronunciation (term_id, deleted)");
            ensureIndex(conn, st, "user_vocab_state", "idx_user_vocab_favorite",
                    "CREATE INDEX idx_user_vocab_favorite ON user_vocab_state (user_id, favorite, deleted)");
            ensureIndex(conn, st, "user_vocab_state", "idx_user_vocab_status",
                    "CREATE INDEX idx_user_vocab_status ON user_vocab_state (user_id, learning_status, deleted)");
            ensureIndex(conn, st, "user_vocab_state", "idx_user_vocab_card",
                    "CREATE INDEX idx_user_vocab_card ON user_vocab_state (linked_flashcard_id)");
            ensureIndex(conn, st, "tech_vocab_concept_link", "idx_tech_link_target",
                    "CREATE INDEX idx_tech_link_target ON tech_vocab_concept_link (target_type, target_id, deleted)");
            ensureIndex(conn, st, "tech_vocab_candidate", "idx_tech_candidate_owner",
                    "CREATE INDEX idx_tech_candidate_owner ON tech_vocab_candidate (user_id, review_status, deleted)");
        }
    }

    /** H2（即使 MODE=MySQL）不接受 MySQL 的 ENGINE / CHARSET 子句，按真实产品名区分 */
    private static boolean isMySql(Connection conn) throws SQLException {
        String product = conn.getMetaData().getDatabaseProductName();
        return product != null && product.toLowerCase(Locale.ROOT).contains("mysql");
    }

    /** 索引不存在才创建；H2 默认大写表名，按数据库标识符规则补齐大小写 */
    private static void ensureIndex(Connection conn, java.sql.Statement st, String table, String index, String ddl)
            throws SQLException {
        if (hasIndex(conn, table, index)) {
            return;
        }
        if (conn.getMetaData().storesUpperCaseIdentifiers() && hasIndex(conn, table.toUpperCase(), index)) {
            return;
        }
        st.execute(ddl);
    }

    private static boolean hasIndex(Connection conn, String table, String index) throws SQLException {
        Set<String> names = new HashSet<>();
        try (ResultSet rs = conn.getMetaData().getIndexInfo(conn.getCatalog(), conn.getSchema(), table, false, false)) {
            while (rs.next()) {
                String name = rs.getString("INDEX_NAME");
                if (name != null) {
                    names.add(name.toLowerCase());
                }
            }
        }
        return names.contains(index.toLowerCase());
    }

    /** 列不存在才执行 ADD COLUMN；只允许可空列，保证存量数据无需回填 */
    private static void ensureColumn(Connection conn, java.sql.Statement st, String table, String column, String ddl)
            throws SQLException {
        if (hasColumn(conn, table, column)) {
            return;
        }
        if (conn.getMetaData().storesUpperCaseIdentifiers() && hasColumn(conn, table.toUpperCase(), column)) {
            return;
        }
        st.execute(ddl);
    }

    private static boolean hasColumn(Connection conn, String table, String column) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), conn.getSchema(), table, null)) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
