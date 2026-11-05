package com.knowflow.agent.tool.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.agent.ToolContext;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DbQueryToolSecurityTest {
    @Test
    void rejectsSensitiveTablesBeforeDatabaseExecution() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectMapper json = new ObjectMapper();
        DbQueryTool tool = new DbQueryTool(jdbc, json);
        assertFalse(tool.execute(json.readTree("{\"sql\":\"select password from sys_user\"}"),
                ToolContext.builder().userId(1L).build()).isSuccess());
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsUnsupportedSyntaxAndSensitiveColumns() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectMapper json = new ObjectMapper();
        DbQueryTool tool = new DbQueryTool(jdbc, json);
        for (String sql : java.util.List.of("select * from question_topic", "select password from question_topic",
                "select code from question_topic; select code from question_topic",
                "select t.code from question_topic t", "select code from question_topic union select password from sys_user",
                "with q as (select password from sys_user) select code from question_topic",
                "select code from question_topic join sys_user on 1=1", "select (select password from sys_user) from question_topic",
                "select sleep(10) from question_topic", "select code from \"question_topic\"")) {
            assertFalse(tool.execute(json.createObjectNode().put("sql", sql), ToolContext.builder().userId(1L).build()).isSuccess(), sql);
        }
        verifyNoInteractions(jdbc);
        assertFalse(tool.enabledByDefault());
        assertEquals(com.knowflow.agent.tool.ToolPermission.DANGEROUS, tool.permission());
    }

    @Test
    void executesOnlyBoundedPublicDictionaryProjection() throws Exception {
        var source = new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:tool_policy;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("create table question_topic(code varchar(20), status int, deleted int)");
        for (int index = 0; index < 80; index++) jdbc.update("insert into question_topic values (?,1,0)", "topic" + index);
        ObjectMapper json = new ObjectMapper();
        var result = new DbQueryTool(jdbc, json).execute(json.createObjectNode().put("sql", "select code from question_topic"), ToolContext.builder().userId(1L).build());
        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().startsWith("共 50 行"));
        jdbc.execute("drop table question_topic");
        var failed = new DbQueryTool(jdbc, json).execute(json.createObjectNode().put("sql", "select code from question_topic"), ToolContext.builder().userId(1L).build());
        assertFalse(failed.isSuccess());
        assertEquals("数据库查询失败，请稍后重试", failed.getError());
    }

    @Test
    void appliesSqlLimitAndStatementTimeoutBeforeExecution() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var connection = mock(java.sql.Connection.class);
        var statement = mock(java.sql.PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(jdbc.query(any(org.springframework.jdbc.core.PreparedStatementCreator.class), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenAnswer(invocation -> {
                    org.springframework.jdbc.core.PreparedStatementCreator creator = invocation.getArgument(0);
                    assertSame(statement, creator.createPreparedStatement(connection));
                    verify(connection).prepareStatement("SELECT code FROM question_topic WHERE deleted = 0 AND status = 1 LIMIT 50");
                    verify(statement).setMaxRows(50);
                    verify(statement).setQueryTimeout(3);
                    return java.util.List.of();
                });
        ObjectMapper json = new ObjectMapper();
        assertTrue(new DbQueryTool(jdbc, json).execute(json.createObjectNode().put("sql", "select code from question_topic"), ToolContext.builder().userId(1L).build()).isSuccess());
    }

    @Test
    void registryRejectsDisabledOrUnapprovedDangerousToolBeforeSql() {
        var configMapper = mock(com.knowflow.agent.mapper.AgentToolConfigMapper.class);
        var callMapper = mock(com.knowflow.agent.mapper.AgentToolCallMapper.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectMapper json = new ObjectMapper();
        var tool = new DbQueryTool(jdbc, json);
        var registry = new com.knowflow.agent.tool.ToolRegistry(java.util.List.of(tool), configMapper, callMapper, json);
        var context = ToolContext.builder().userId(1L).sessionId(1L).build();
        assertFalse(registry.invoke("db_query", json.createObjectNode().put("sql", "select code from question_topic"), context).isSuccess());
        var config = new com.knowflow.agent.entity.AgentToolConfig();
        config.setEnabled(1);
        config.setAllowWrite(0);
        when(configMapper.selectOne(any())).thenReturn(config);
        assertFalse(registry.invoke("db_query", json.createObjectNode().put("sql", "select code from question_topic"), context).isSuccess());
        config.setAllowWrite(1);
        var unapproved = registry.invoke("db_query",
                json.createObjectNode().put("sql", "select code from question_topic"), context);
        assertFalse(unapproved.isSuccess());
        assertTrue(unapproved.getError().contains("未获批准"));
        verifyNoInteractions(jdbc);
    }
}
