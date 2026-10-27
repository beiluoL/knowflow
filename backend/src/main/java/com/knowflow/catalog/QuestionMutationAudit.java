package com.knowflow.catalog;
import com.knowflow.common.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
/** 与题目事务共用数据源；只记录身份/动作/状态，无隐藏答案或代码用例。 */
@Service @RequiredArgsConstructor
public class QuestionMutationAudit {
 private final JdbcTemplate jdbc;
 public void record(String source,Long id,String action,String before,String after){jdbc.update("INSERT INTO question_catalog_audit(actor_id,source_type,source_question_id,action,before_state,after_state,created_at) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)",SecurityUtils.getCurrentUserId(),source,id,action,before,after);}
}
