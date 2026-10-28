package com.knowflow.learning.adaptive;
import jakarta.annotation.PostConstruct;import lombok.RequiredArgsConstructor;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Component;import java.sql.*;
@Component @RequiredArgsConstructor @ConditionalOnProperty(name="knowflow.product.adaptive-learning.enabled",havingValue="true")
public class AdaptiveSchema {
 private final JdbcTemplate sql;
 @PostConstruct void init(){sql.execute((org.springframework.jdbc.core.ConnectionCallback<Void>)c->{migrate(c);return null;});}
 public static void migrate(Connection c)throws SQLException{try(var s=c.createStatement()){
  s.execute("CREATE TABLE IF NOT EXISTS adaptive_path_state(owner_id BIGINT NOT NULL,path_id BIGINT NOT NULL,revision BIGINT NOT NULL DEFAULT 0,locks_json LONGTEXT NOT NULL,deferred_json LONGTEXT NOT NULL,order_json LONGTEXT,order_hash VARCHAR(64),updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),PRIMARY KEY(owner_id,path_id))");
  for(String column:new String[]{"order_json","order_hash"}){boolean found=false;for(String table:new String[]{"adaptive_path_state","ADAPTIVE_PATH_STATE"})try(var columns=c.getMetaData().getColumns(c.getCatalog(),null,table,null)){while(columns.next())if(column.equalsIgnoreCase(columns.getString("COLUMN_NAME")))found=true;}if(!found)s.execute("ALTER TABLE adaptive_path_state ADD COLUMN "+column+(column.equals("order_json")?" LONGTEXT":" VARCHAR(64)"));}
  s.execute("CREATE TABLE IF NOT EXISTS adaptive_path_revision(id BIGINT AUTO_INCREMENT PRIMARY KEY,owner_id BIGINT NOT NULL,path_id BIGINT NOT NULL,base_revision BIGINT NOT NULL,request_id VARCHAR(80) NOT NULL,input_hash VARCHAR(64) NOT NULL,payload LONGTEXT NOT NULL,state VARCHAR(20) NOT NULL,created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),applied_at TIMESTAMP(6) NULL DEFAULT NULL,UNIQUE(owner_id,request_id))");
 }}
}
