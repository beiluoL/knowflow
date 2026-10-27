package com.knowflow.service;
import com.knowflow.dto.*;
import com.knowflow.exception.BusinessException;
import com.knowflow.security.*;
import com.knowflow.service.impl.CodeExecutionServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HostExecutionTest {
    @TempDir Path temp;
    CodeExecutionServiceImpl engine; CodeWorkspaceService workspace;
    @BeforeEach void setup() {
        HostCapabilityProperties props=new HostCapabilityProperties(); props.setEnabled(true);props.setAuthorizedUserIds(List.of(1L));
        workspace=mock(CodeWorkspaceService.class); engine=new CodeExecutionServiceImpl(workspace,new HostCapabilityGuard(props));
        ReflectionTestUtils.setField(engine,"defaultTimeLimitMs",10000L);ReflectionTestUtils.setField(engine,"outputLimitBytes",1024);ReflectionTestUtils.setField(engine,"maxConcurrency",8);
        ReflectionTestUtils.setField(engine,"pythonBin","python3");ReflectionTestUtils.invokeMethod(engine,"init");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(1L,"unused",List.of()));
    }
    @AfterEach void clear(){ SecurityContextHolder.clearContext(); }
    CodeRunRequest req(String code) { CodeRunRequest r=new CodeRunRequest();r.setLanguage("python");r.setCode(code);return r; }
    @Test void safePythonExecutes() { CodeRunResult r=engine.execute(req("print('safe fixture')"),1L);assertEquals(CodeRunResult.Status.SUCCESS,r.getStatus());assertEquals("safe fixture\n",r.getOutput()); }
    @Test void safeNodeExecutes() { runLanguage("javascript", "console.log('safe fixture')", "nodeBin", "node"); }
    @Test void safeJavaExecutes() { ReflectionTestUtils.setField(engine,"javacBin","javac"); runLanguage("java", "public class Main { public static void main(String[] args) { System.out.println(\"safe fixture\"); } }", "javaBin", "java"); }
    @Test void safeCppExecutes() { runLanguage("cpp", "#include <iostream>\nint main(){std::cout << \"safe fixture\\n\";}", "gppBin", "g++"); }
    private void runLanguage(String language,String code,String field,String binary) {
        ReflectionTestUtils.setField(engine,field,binary);CodeRunRequest r=req(code);r.setLanguage(language);
        CodeRunResult result=engine.execute(r,1L);assertEquals(CodeRunResult.Status.SUCCESS,result.getStatus(),result.toString());assertEquals("safe fixture\n",result.getOutput());
    }
    @Test void timeoutCannotExceedCap() { CodeRunRequest r=req("print('safe')");r.setTimeLimitMs(10001L);assertEquals(400,assertThrows(BusinessException.class,()->engine.execute(r,1L)).getCode()); }
    @Test void illegalExecutableRejected() { CodeRunRequest r=req("0");r.setLanguage("sh");assertEquals(400,assertThrows(BusinessException.class,()->engine.execute(r,1L)).getCode()); }
    @Test void workspaceRunsFromTemporarySnapshot() throws Exception {
        Path ws=Files.createDirectory(temp.resolve("workspace"));Files.writeString(ws.resolve("entry.py"),"import os\nprint(os.path.basename(os.getcwd()).startswith('knowflow-code-'))");
        when(workspace.getWorkspaceDir(1L)).thenReturn(ws);CodeRunRequest r=req("unused");r.setWorkspace(true);r.setEntryFile("entry.py");
        CodeRunResult result=engine.execute(r,1L);assertEquals(CodeRunResult.Status.SUCCESS,result.getStatus());assertEquals("True\n",result.getOutput());
    }
    @Test void workspaceSymlinkCannotEscape() throws Exception {
        Path ws=Files.createDirectory(temp.resolve("workspace"));Path outside=Files.writeString(temp.resolve("outside.py"),"print('safe fixture')");Files.createSymbolicLink(ws.resolve("entry.py"),outside);
        when(workspace.getWorkspaceDir(1L)).thenReturn(ws);CodeRunRequest r=req("unused");r.setWorkspace(true);r.setEntryFile("entry.py");
        assertEquals(403,assertThrows(BusinessException.class,()->engine.execute(r,1L)).getCode());
    }
    @Test void finiteOutputIsTruncatedWithoutPipeDeadlock() {
        CodeRunResult r=engine.execute(req("print('x'*50000)"),1L);assertEquals(CodeRunResult.Status.SUCCESS,r.getStatus());assertTrue(r.getOutput().length()<1200);assertTrue(r.getOutput().contains("截断"));
    }
    @Test void emptyCodeIsInputErrorNotFakeSuccess() { assertEquals(400,assertThrows(BusinessException.class,()->engine.execute(req(""),1L)).getCode()); }
    @Test void missingWorkspaceEntryIsInputError() { CodeRunRequest r=req("unused");r.setWorkspace(true);assertEquals(400,assertThrows(BusinessException.class,()->engine.execute(r,1L)).getCode()); }
}
