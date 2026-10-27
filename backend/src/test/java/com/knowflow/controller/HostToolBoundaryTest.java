package com.knowflow.controller;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.agent.ToolContext;
import com.knowflow.agent.tool.*;
import com.knowflow.exception.BusinessException;
import com.knowflow.support.FakeAiConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @Import(FakeAiConfiguration.class)
@TestPropertySource(properties={"knowflow.datasource.h2.url=jdbc:h2:mem:host_tools;DB_CLOSE_DELAY=-1;MODE=MySQL","knowflow.host-capabilities.enabled=false"})
class HostToolBoundaryTest {
    @Autowired List<AgentTool> tools;
    @Autowired ToolRegistry registry;
    @Autowired ObjectMapper json;
    @BeforeEach void actor(){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(1L,"unused",List.of()));}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void directToolsCannotBypassWithoutRegistry() throws Exception {
        for(String name:List.of("code_run","test_gen","terminal","git","fs_read","fs_write","fs_tree","fs_symbols","code_review")) {
            AgentTool tool=tools.stream().filter(t->t.name().equals(name)).findFirst().orElseThrow();
            BusinessException e=assertThrows(BusinessException.class,()->tool.execute(json.createObjectNode(),ToolContext.builder().userId(1L).build()),name);
            assertEquals(403,e.getCode(),name);
        }
    }
    @Test void registryReturnsCapabilityFailureNotSuccessfulToolResult() {
        ToolResult result=registry.invoke("code_run",json.createObjectNode(),ToolContext.builder().userId(1L).sessionId(1L).build());
        assertFalse(result.isSuccess());assertTrue(result.getError().contains("可信本地开发"));
    }
}
