package com.knowflow.learning.studypack;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.knowledge.entity.DocDocument;
import jakarta.annotation.PostConstruct;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Component;

/** 个人上传资料通过原有文档 API 保持私有，管理员也不例外。 */
@Component
@RequiredArgsConstructor
public class StudyPackAccessPolicy {
    private final JdbcTemplate sql;

    @Value("${knowflow.product.study-pack.enabled:false}")
    private boolean enabled;

    private boolean present;

    @PostConstruct
    void initialize() {
        present =
                Boolean.TRUE.equals(
                        sql.execute(
                                (ConnectionCallback<Boolean>)
                                        c -> {
                                            if (enabled) StudyPackSchema.migrate(c);
                                            return StudyPackSchema.exists(c);
                                        }));
    }

    public boolean canRead(Long doc, Long user) {
        if (!present || doc == null) return true;
        var owners =
                sql.queryForList("SELECT owner_id FROM study_pack_job WHERE doc_id=?", Long.class, doc);
        return owners.isEmpty() || owners.stream().allMatch(owner -> Objects.equals(owner, user));
    }

    public void scope(LambdaQueryWrapper<DocDocument> query, Long user) {
        if (present)
            query.notInSql(
                    DocDocument::getId,
                    "SELECT doc_id FROM study_pack_job WHERE doc_id IS NOT NULL"
                            + (user == null ? "" : " AND owner_id<>" + user));
    }
}
