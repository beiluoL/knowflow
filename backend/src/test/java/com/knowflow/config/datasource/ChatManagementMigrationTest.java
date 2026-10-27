package com.knowflow.config.datasource;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.knowflow.dto.DbSwitchDTO;
import com.knowflow.service.impl.DatabaseAdminServiceImpl;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ChatManagementMigrationTest {
 private DataSourceProperties.Item target(String suffix) {
  var item=new DataSourceProperties.Item();item.setUrl("jdbc:h2:mem:chat_migration_"+suffix+UUID.randomUUID()+";DB_CLOSE_DELAY=-1;MODE=MySQL");item.setUsername("sa");item.setPassword("");item.setMinimumIdle(0);item.setInitMode("auto");return item;
 }
 private void legacy(DataSourceProperties.Item item)throws Exception {
  try(var c=DriverManager.getConnection(item.getUrl(),"sa","");var statement=c.createStatement()) {
   statement.execute("CREATE TABLE chat_conversation(id BIGINT PRIMARY KEY,user_id BIGINT)");statement.execute("INSERT INTO chat_conversation VALUES(1,17)");
  }
 }
 @Test void existingAutoAndNeverTargetsReceiveMigrationBeforeSwap()throws Exception {
  for(String mode:new String[]{"auto","never"}){
   var props=new DataSourceProperties();props.setConfigFile("/tmp/knowflow-chat-migration-"+UUID.randomUUID()+".json");
   var item=target(mode);item.setInitMode(mode);props.setH2(item);legacy(item);
   var manager=new DataSourceManager(props);var old=manager.buildDataSource(DbTypeEnum.H2,target("old"));var routing=new DynamicRoutingDataSource(old,DbTypeEnum.H2);
   try {
    var admin=new DatabaseAdminServiceImpl(routing,manager,props,new MybatisPlusInterceptor());var dto=new DbSwitchDTO();dto.setType("h2");admin.switchDataSource(dto);
    assertNotSame(old,routing.getDelegate());
    try(var c=routing.getConnection();var stmt=c.createStatement();var rs=stmt.executeQuery("SELECT pinned,archived FROM chat_conversation WHERE id=1")){assertTrue(rs.next());assertFalse(rs.getBoolean(1));assertFalse(rs.getBoolean(2));}
    manager.initSchemaIfNeeded(routing::getConnection,DbTypeEnum.H2,item);
    try(var c=routing.getConnection();var stmt=c.createStatement();var rs=stmt.executeQuery("SELECT COUNT(*) FROM chat_public_share")){assertTrue(rs.next());assertEquals(0,rs.getInt(1));}
   }finally{routing.getDelegate().close();old.close();}
  }
 }
 @Test void failedTargetMigrationClosesNewPoolAndRetainsOld()throws Exception {
  var props=new DataSourceProperties();props.setConfigFile("/tmp/knowflow-chat-migration-"+UUID.randomUUID()+".json");props.setH2(target("invalid"));props.getH2().setInitMode("never");
  class TrackingManager extends DataSourceManager {
   HikariDataSource built;TrackingManager(){super(props);}
   @Override public HikariDataSource buildDataSource(DbTypeEnum type,DataSourceProperties.Item item){built=super.buildDataSource(type,item);return built;}
  }
  var manager=new TrackingManager();var old=manager.buildDataSource(DbTypeEnum.H2,target("old"));var routing=new DynamicRoutingDataSource(old,DbTypeEnum.H2);
  try {
   var admin=new DatabaseAdminServiceImpl(routing,manager,props,new MybatisPlusInterceptor());var dto=new DbSwitchDTO();dto.setType("h2");assertThrows(IllegalStateException.class,()->admin.switchDataSource(dto));assertSame(old,routing.getDelegate());assertFalse(old.isClosed());assertTrue(manager.built.isClosed());
  }finally{old.close();}
 }
}
