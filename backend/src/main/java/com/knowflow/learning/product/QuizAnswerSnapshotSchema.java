package com.knowflow.learning.product;
import jakarta.annotation.PostConstruct;import lombok.RequiredArgsConstructor;import org.springframework.jdbc.core.*;import org.springframework.stereotype.Component;
/** Nullable additive field. Historical answers are never backfilled or rescored. */
@Component @RequiredArgsConstructor
public class QuizAnswerSnapshotSchema {
 private final JdbcTemplate jdbc;
 @PostConstruct void initialize(){jdbc.execute((ConnectionCallback<Void>)c->{boolean found=false;try(var s=c.prepareStatement("SELECT * FROM quiz_answer_record WHERE 1=0");var r=s.executeQuery()){var m=r.getMetaData();for(int i=1;i<=m.getColumnCount();i++)if("question_snapshot".equalsIgnoreCase(m.getColumnName(i)))found=true;}if(!found)try(var s=c.createStatement()){s.execute("ALTER TABLE quiz_answer_record ADD COLUMN question_snapshot TEXT NULL COMMENT '作答时题目与判分条件快照，旧记录不补造'");}return null;});}
}
