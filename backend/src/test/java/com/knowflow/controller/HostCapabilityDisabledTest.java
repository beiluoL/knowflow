package com.knowflow.controller;

import com.knowflow.exception.GlobalExceptionHandler;
import com.knowflow.support.FakeAiConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Import(FakeAiConfiguration.class)
@TestPropertySource(properties={"knowflow.datasource.h2.url=jdbc:h2:mem:host_disabled;DB_CLOSE_DELAY=-1;MODE=MySQL", "knowflow.host-capabilities.enabled=false"})
class HostCapabilityDisabledTest {
    @Autowired CodeRunController code;
    @Autowired LocalReaderController reader;
    @Autowired CodeAgentController agent;
    @Autowired KnowledgeImportController imports;
    @Autowired ObsidianImportController obsidian;
    @TempDir Path temp;
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private void actor(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(1L,"unused",List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }
    private MockMvc mvc() { return MockMvcBuilders.standaloneSetup(code,reader,agent,imports,obsidian).setControllerAdvice(new GlobalExceptionHandler()).build(); }
    @Test void anonymousCannotExecute() throws Exception {
        mvc().perform(post("/api/code/run").contentType("application/json").content("{\"language\":\"invalid\",\"code\":\"0\"}")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
    }
    @Test void normalUserCannotExecuteEvenInvalidLanguage() throws Exception { actor("USER"); rejectedExecution(); }
    @Test void adminDoesNotGrantHostTrust() throws Exception { actor("ADMIN"); rejectedExecution(); }
    private void rejectedExecution() throws Exception {
        mvc().perform(post("/api/code/run").contentType("application/json").content("{\"language\":\"invalid\",\"code\":\"0\"}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }
    @Test void agentExecutionCannotBypass() throws Exception {
        actor("USER"); mvc().perform(post("/api/agent/execute").contentType("application/json").content("{\"language\":\"invalid\",\"code\":\"0\"}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }
    @Test void debugCannotBypass() throws Exception {
        actor("USER"); mvc().perform(post("/api/code/debug").contentType("application/json").content("{\"language\":\"invalid\",\"code\":\"0\"}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }
    @Test void readerResolveRefusesBeforePathExistsProbe() throws Exception {
        actor("USER"); mvc().perform(post("/api/local-reader/resolve").contentType("application/json").content("{\"path\":\""+temp+"\"}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }
    @Test void readerScanRefuses() throws Exception {
        Files.writeString(temp.resolve("fixture.md"),"safe fixture"); actor("USER"); mvc().perform(get("/api/local-reader/scan").param("path",temp.toString())).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }
    @Test void readerContentRefuses() throws Exception {
        Files.writeString(temp.resolve("fixture.md"),"safe fixture"); actor("USER"); mvc().perform(get("/api/local-reader/content").param("rootAbsolutePath",temp.toString()).param("path","fixture.md")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }
    @Test void readerImageRefusesBeforeFallback() throws Exception {
        Files.write(temp.resolve("fixture.png"),new byte[]{1}); actor("USER"); mvc().perform(get("/api/local-reader/image").param("rootAbsolutePath",temp.toString()).param("path","fixture.png")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }
    @Test void pathImportScanCannotBypass() throws Exception {
        actor("USER"); mvc().perform(post("/api/knowledge/import/path/scan").param("path",temp.toString())).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }
    @Test void obsidianFileListCannotBypass() throws Exception {
        Files.writeString(temp.resolve("fixture.md"),"safe fixture"); actor("USER"); mvc().perform(get("/api/obsidian/import/scan").param("filePaths",temp.resolve("fixture.md").toString())).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }
    @Test void assessRefusesEvenWithNoTestsToRun() throws Exception {
        actor("USER");mvc().perform(post("/api/code/assess").contentType("application/json").content("{\"language\":\"python\",\"code\":\"print('safe')\",\"testCasesJson\":\"[]\"}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }
    @Test void pythonDebugRefusesBeforeDirectProcess() throws Exception {
        actor("ADMIN");mvc().perform(post("/api/code/debug").contentType("application/json").content("{\"language\":\"python\",\"code\":\"print('safe')\"}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }
    @Test void obsidianSseRefusesBeforeResponseStarts() throws Exception {
        actor("USER");mvc().perform(post("/api/obsidian/import/stream").contentType("application/json").content("{\"path\":\""+temp+"\"}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }
    @Test void pathSseRefusesBeforeResponseStarts() throws Exception {
        actor("USER");mvc().perform(post("/api/knowledge/import/path/stream").param("path",temp.toString()).param("targetCategoryId","1")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }
}
